package com.perfumerlab.scentstudio.events;

import com.perfumerlab.scentstudio.limit.LimitFilters;
import com.perfumerlab.scentstudio.limit.RateLimiter;
import com.perfumerlab.scentstudio.me.Consents;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import com.perfumerlab.scentstudio.web.InstallId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 사용 기록 묶음 받기 (server_design.md 4-2절, db_design.md ⑤).
 * - 설치 id는 X-Install-Id 헤더(없으면 400). 한 요청 1~100개(벗어나면 400, 하나도 저장 안 함)
 * - 잘못된 기록은 그것만 빼고 저장하고 번호(0부터)를 rejected로 알린다(잘못된 하나 때문에 앱 대기열이 막히지 않게)
 * - (install_id, client_event_id) 중복은 DB가 무시 → 같은 묶음을 다시 보내도 한 번만 저장. 이미 있던 것도 accepted로 센다
 * - 로그인했고 지금 버전 동의가 다 있을 때만 user_id를 붙인다(동의 전 기록은 사용자와 잇지 않음)
 * - 요청(묶음) 기준 install_id + IP 짝 분당 10회, IP만으로 분당 30회(설치 id를 바꿔 가며 보내는 것 막기). (로그인 사용자 쓰기 30회·로그인 없는 IP 60회 제한도 앞에서 따로 걸림)
 * props 크기(2KB)는 DB가 세는 방식(jsonb 글자)이 정답이라 DB에서 거른다. 한 번의 쿼리로 묶음 전체를 넣는다.
 */
@RestController
public class EventsController {

    static final int MAX_BATCH = 100, MAX_PROPS_BYTES = 2048;
    static final Set<String> NAMES = Set.of("app_open", "blend_started", "blend_summary_viewed", "recs_viewed",
            "perfume_opened", "blend_saved", "favorite_added", "feedback_submitted");
    static final Pattern APP_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+-]{0,31}");
    static final Instant EARLIEST = Instant.parse("2020-01-01T00:00:00Z");
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    public record EventsRequest(String appVersion, JsonNode events) {}

    public record EventsResult(int accepted, List<Integer> rejected) {}

    private final JdbcClient jdbc;
    private final RateLimiter limiter;
    private final Consents consents;
    private final JsonMapper json = JsonMapper.builder().build();

    public EventsController(JdbcClient jdbc, RateLimiter limiter, Consents consents) {
        this.jdbc = jdbc;
        this.limiter = limiter;
        this.consents = consents;
    }

    @PostMapping(path = "/api/v1/events", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public EventsResult receive(HttpServletRequest req, Authentication auth, @RequestBody EventsRequest body) {
        UUID install = InstallId.from(req);
        limiter.require(RateLimiter.Limit.EVENTS_IP, LimitFilters.ipKey(req));
        limiter.require(RateLimiter.Limit.EVENTS, install + "|" + LimitFilters.ipKey(req));
        if (body == null || body.appVersion() == null || !APP_VERSION.matcher(body.appVersion()).matches()
                || body.events() == null || !body.events().isArray() || body.events().isEmpty() || body.events().size() > MAX_BATCH) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
        UUID user = null;
        if (auth instanceof JwtAuthenticationToken t) {
            UUID id = UUID.fromString(t.getName());
            if (consents.agreed(id).size() == Consents.TYPES.size()) user = id;
        }

        ArrayNode valid = json.createArrayNode();
        List<Integer> rejected = new ArrayList<>();
        Instant latest = Instant.now().plus(CLOCK_SKEW);
        for (int i = 0; i < body.events().size(); i++) {
            ObjectNode row = row(body.events().get(i), latest);
            if (row == null) rejected.add(i);
            else valid.add(row.put("i", i));
        }
        if (!valid.isEmpty()) {
            // ok = DB 기준 props 2KB 이하인 것. ins는 바깥에서 안 읽어도 한 번 실행된다(Postgres 데이터 바꾸는 WITH)
            List<Integer> kept = jdbc.sql("""
                            with input as (
                              select (x ->> 'i')::int as i, (x ->> 'cid')::uuid as cid, x ->> 'name' as name, x -> 'props' as props,
                                     (x ->> 'at')::timestamptz as at
                              from jsonb_array_elements(?::jsonb) x),
                            ok as (select * from input where octet_length(props::text) <= %d),
                            ins as (
                              insert into app.events (install_id, user_id, name, props, app_version, occurred_at, client_event_id)
                              select ?::uuid, ?::uuid, name, props, ?, at, cid from ok
                              on conflict do nothing
                              returning 1)
                            select i from ok order by i""".formatted(MAX_PROPS_BYTES))
                    .params(json.writeValueAsString(valid), install, user, body.appVersion())
                    .query(Integer.class).list();
            for (JsonNode v : valid) if (!kept.contains(v.get("i").asInt())) rejected.add(v.get("i").asInt());
            rejected.sort(null);
            return new EventsResult(kept.size(), rejected);
        }
        return new EventsResult(0, rejected);
    }

    /** 기록 하나 검사. 맞으면 DB에 넘길 모양, 틀리면 null */
    private ObjectNode row(JsonNode e, Instant latest) {
        if (e == null || !e.isObject()) return null;
        UUID cid = uuid(e.get("clientEventId"));
        JsonNode name = e.get("name"), props = e.get("props"), at = e.get("occurredAt");
        if (cid == null || name == null || !name.isString() || !NAMES.contains(name.asString())) return null;
        if (props == null || props.isNull()) props = json.createObjectNode();
        if (!props.isObject() || !storable(props)) return null;
        if (at == null || !at.isString()) return null;
        Instant when;
        try {
            when = OffsetDateTime.parse(at.asString()).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
        if (when.isBefore(EARLIEST) || when.isAfter(latest)) return null;
        ObjectNode row = json.createObjectNode();
        row.put("cid", cid.toString()).put("name", name.asString()).put("at", when.toString()).set("props", props);
        return row;
    }

    private static UUID uuid(JsonNode n) {
        if (n == null || !n.isString() || n.asString().length() != 36) return null;
        try {
            UUID u = UUID.fromString(n.asString());
            return u.toString().equals(n.asString().toLowerCase(Locale.ROOT)) ? u : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** jsonb가 담을 수 있나: 글자에 NUL·짝 없는 서로게이트 없음, 숫자는 유한 */
    static boolean storable(JsonNode n) {
        if (n.isString()) return text(n.asString());
        if (n.isNumber()) return !(n.isDouble() || n.isFloat()) || Double.isFinite(n.asDouble());
        if (n.isObject()) {
            for (Map.Entry<String, JsonNode> f : n.properties()) if (!text(f.getKey()) || !storable(f.getValue())) return false;
            return true;
        }
        if (n.isArray()) {
            for (JsonNode c : n) if (!storable(c)) return false;
            return true;
        }
        return n.isBoolean() || n.isNull();
    }

    private static boolean text(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) return false;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
