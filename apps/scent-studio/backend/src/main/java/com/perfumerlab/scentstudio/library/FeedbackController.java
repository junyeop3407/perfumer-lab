package com.perfumerlab.scentstudio.library;

import com.perfumerlab.scentstudio.catalog.CatalogKeys;
import com.perfumerlab.scentstudio.me.Consents;
import com.perfumerlab.scentstudio.web.ApiError;
import com.perfumerlab.scentstudio.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 시향 기록 (db_design.md ④·4-3, 승인 D4). 1차는 만들기·목록·본인 삭제만(수정은 나중 차수, 사장 결정 2026-11-06).
 * - 지우기: 내 기록일 때만. 남의 id·없는 id·uuid가 아닌 id → 404
 * - blendId: 내 조합만. 남의 조합·없는 조합 → 404(같은 답). DB 외래 키 (blend_id, user_id)가 한 번에 막는다
 * - 카탈로그에 없는 향수 → 400 UNKNOWN_KEY. 칸 검사(만족도·비슷함 1~5, 메모 500자, 순위 1~10, 점수 0~1, 시각) → 400
 * - 1인당 1,000개(사용자 단위 잠금 안에서 세고 넣음) → 409 LIMIT_REACHED
 */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    static final int LIMIT = 1000, MAX_MEMO = 500;
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5); // 기기 시계가 조금 빠른 것은 받아 줌(실제 시계 기준)
    static final Instant EARLIEST = Instant.parse("2020-01-01T00:00:00Z");
    static final Pattern CATALOG_VERSION = Pattern.compile("[0-9A-Za-z.-]{1,32}");
    private static final String COLS = "id, perfume_key, blend_id, satisfaction, similarity, memo, tested_at, rank, score,"
            + " engine_version, catalog_version, created_at";

    public record FeedbackRequest(String perfumeKey, String blendId, Integer satisfaction, Integer similarity, String memo,
                                  String testedAt, Integer rank, Double score, Integer engineVersion, String catalogVersion) {}

    public record Feedback(String id, String perfumeKey, String blendId, int satisfaction, int similarity, String memo, String testedAt,
                           Integer rank, Double score, int engineVersion, String catalogVersion, String createdAt) {}

    public record FeedbackList(List<Feedback> feedback) {}

    private static final RowMapper<Feedback> ROWS = (rs, n) -> new Feedback(rs.getString("id"), rs.getString("perfume_key"),
            rs.getString("blend_id"), rs.getInt("satisfaction"), rs.getInt("similarity"), rs.getString("memo"), Library.time(rs, "tested_at"),
            rs.getObject("rank", Integer.class), rs.getObject("score", Double.class), rs.getInt("engine_version"),
            rs.getString("catalog_version"), Library.time(rs, "created_at"));

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Consents consents;
    private final CatalogKeys catalog;

    public FeedbackController(JdbcClient jdbc, TransactionTemplate tx, Consents consents, CatalogKeys catalog) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.consents = consents;
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public FeedbackList list(JwtAuthenticationToken auth) {
        return new FeedbackList(jdbc.sql("select " + COLS + " from app.tasting_feedback where user_id = ? order by tested_at desc, created_at desc, id")
                .param(UUID.fromString(auth.getName())).query(ROWS).list());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Feedback> create(JwtAuthenticationToken auth, @RequestBody FeedbackRequest req) {
        UUID user = UUID.fromString(auth.getName());
        consents.requireComplete(user);
        UUID blend = blendId(req);
        OffsetDateTime testedAt = check(req);
        if (!catalog.contains(CatalogKeys.Kind.PERFUME, req.perfumeKey())) throw new ApiException(ApiError.UNKNOWN_KEY);
        try {
            Feedback made = tx.execute(s -> {
                Library.lock(jdbc, Library.Kind.FEEDBACK, user);
                Library.requireRoom(jdbc, "tasting_feedback", user, LIMIT);
                return jdbc.sql("insert into app.tasting_feedback (user_id, perfume_key, blend_id, satisfaction, similarity, memo, tested_at,"
                                + " rank, score, engine_version, catalog_version) values (?, ?, ?, ?, ?, ?, coalesce(?, now()), ?, ?, ?, ?) returning " + COLS)
                        .params(user, req.perfumeKey(), blend, req.satisfaction(), req.similarity(), req.memo(), testedAt, req.rank(), req.score(),
                                req.engineVersion(), req.catalogVersion())
                        .query(ROWS).single();
            });
            return ResponseEntity.status(HttpStatus.CREATED).body(made);
        } catch (DataIntegrityViolationException e) {
            // 외래 키 위반 = 내 조합이 아님(남의 것·없음·방금 지워짐) → 있는지 알려 주지 않고 404
            if (blend != null && Library.sqlState(e, "23503")) throw new ApiException(ApiError.NOT_FOUND);
            throw e;
        }
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> delete(JwtAuthenticationToken auth, @PathVariable("id") String id) {
        UUID user = UUID.fromString(auth.getName());
        consents.requireComplete(user);
        int n = jdbc.sql("delete from app.tasting_feedback where id = ? and user_id = ?").params(Library.id(id), user).update();
        if (n == 0) throw new ApiException(ApiError.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }

    private static UUID blendId(FeedbackRequest req) {
        if (req == null || req.blendId() == null) return null;
        try {
            return Library.id(req.blendId());
        } catch (ApiException e) {
            throw new ApiException(ApiError.BAD_REQUEST); // 본문의 모양이 틀린 것은 400
        }
    }

    /** 칸 검사. 통과하면 시향 시각(안 보냈으면 null → DB가 지금으로) */
    private OffsetDateTime check(FeedbackRequest r) {
        if (r == null || !Library.key(r.perfumeKey(), FavoritesController.MAX_KEY)
                || !between(r.satisfaction(), 1, 5) || !between(r.similarity(), 1, 5)
                || (r.memo() != null && (Library.length(r.memo()) < 0 || Library.length(r.memo()) > MAX_MEMO))
                || (r.rank() != null && !between(r.rank(), 1, 10))
                || (r.score() != null && !(r.score() >= 0 && r.score() <= 1))
                || r.engineVersion() == null || r.engineVersion() < 1
                || r.catalogVersion() == null || !CATALOG_VERSION.matcher(r.catalogVersion()).matches()) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
        if (r.testedAt() == null) return null;
        try {
            OffsetDateTime t = OffsetDateTime.parse(r.testedAt());
            Instant i = t.toInstant();
            if (i.isBefore(EARLIEST) || i.isAfter(Instant.now().plus(CLOCK_SKEW))) throw new ApiException(ApiError.BAD_REQUEST);
            return t;
        } catch (DateTimeParseException e) {
            throw new ApiException(ApiError.BAD_REQUEST);
        }
    }

    private static boolean between(Integer v, int lo, int hi) {
        return v != null && v >= lo && v <= hi;
    }

}
