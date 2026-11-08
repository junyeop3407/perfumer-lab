package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 6: 사용 기록 POST /events (server_design.md 4-2절, db_design.md ⑤, 승인 I1·I2).
 * - 설치 id는 헤더 X-Install-Id(uuid). 없거나 모양이 틀리면 400
 * - 한 요청에 1~100개. 101개면 400(하나도 저장 안 함)
 * - 묶음 안의 잘못된 기록은 그것만 빼고 저장, {accepted, rejected:[번호(0부터)]}
 * - (install_id, client_event_id)가 같으면 한 번만 저장 → 같은 묶음을 다시 보내도 한 번
 * - 로그인 + 지금 버전 동의가 있을 때만 user_id를 붙인다. 로그인 없이도 보낼 수 있다
 * - 요청(묶음) 기준 install_id + IP 분당 10회 → 11번째 429 + Retry-After
 */
class EventsApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/events";

    static String event(UUID id, String name, String props, String occurredAt) {
        return "{\"clientEventId\":\"" + id + "\",\"name\":\"" + name + "\",\"props\":" + props + ",\"occurredAt\":\"" + occurredAt + "\"}";
    }

    static String ok(UUID id) {
        return event(id, "app_open", "{\"screen\":\"home\"}", Instant.now().minus(1, ChronoUnit.HOURS).toString());
    }

    static String batch(String... events) {
        return "{\"appVersion\":\"1.0.0\",\"events\":[" + String.join(",", events) + "]}";
    }

    static String many(int n) {
        String[] es = new String[n];
        for (int i = 0; i < n; i++) es[i] = ok(UUID.randomUUID());
        return batch(es);
    }

    HttpResponse<String> send(UUID install, String body, String... more) throws Exception {
        String[] h = new String[4 + more.length];
        h[0] = "X-Install-Id";
        h[1] = install.toString();
        h[2] = "X-Forwarded-For";
        h[3] = ip();
        System.arraycopy(more, 0, h, 4, more.length);
        return post(PATH, body, h);
    }

    static long stored(UUID install) throws Exception {
        return dbCount("select count(*) from app.events where install_id = ?", install);
    }

    @Test
    void anonymousBatchIsStored() throws Exception {
        UUID install = UUID.randomUUID();
        HttpResponse<String> r = send(install, batch(ok(UUID.randomUUID()), ok(UUID.randomUUID()), ok(UUID.randomUUID())));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"accepted\":3,\"rejected\":[]}", r.body());
        assertEquals(3, stored(install));
        assertEquals(0, dbCount("select count(*) from app.events where install_id = ? and user_id is not null", install));
        assertEquals(3, dbCount("select count(*) from app.events where install_id = ? and app_version = '1.0.0' and props = '{\"screen\": \"home\"}'::jsonb"
                + " and name = 'app_open' and client_event_id is not null", install), "칸이 그대로 저장됨");
    }

    @Test
    void loggedInWithConsentGetsUserId() throws Exception {
        TestAuth.User u = consentedUser();
        UUID install = UUID.randomUUID();
        assertEquals(200, send(install, batch(ok(UUID.randomUUID())), "Authorization", u.bearer()).statusCode());
        assertEquals(1, dbCount("select count(*) from app.events where install_id = ? and user_id = ?", install, u.id()));
    }

    @Test
    void loggedInWithoutConsentIsStoredAnonymously() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        UUID install = UUID.randomUUID();
        assertEquals(200, send(install, batch(ok(UUID.randomUUID())), "Authorization", u.bearer()).statusCode());
        assertEquals(1, stored(install));
        assertEquals(0, dbCount("select count(*) from app.events where user_id = ?", u.id()), "동의 전에는 사용자와 잇지 않음");
    }

    @Test
    void badTokenIs401NotAnonymous() throws Exception {
        UUID install = UUID.randomUUID();
        HttpResponse<String> r = send(install, batch(ok(UUID.randomUUID())), "Authorization", "Bearer not.a.token");
        assertEquals(401, r.statusCode());
        assertEquals(0, stored(install));
    }

    @Test
    void resendingSameBatchStoresOnce() throws Exception {
        UUID install = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        String body = batch(ok(a), ok(b));
        assertEquals("{\"accepted\":2,\"rejected\":[]}", send(install, body).body());
        assertEquals("{\"accepted\":2,\"rejected\":[]}", send(install, body).body(), "다시 보내도 성공으로 답함");
        assertEquals(2, stored(install));
    }

    @Test
    void duplicateInsideOneBatchStoresOnce() throws Exception {
        UUID install = UUID.randomUUID(), a = UUID.randomUUID();
        assertEquals(200, send(install, batch(ok(a), ok(a))).statusCode());
        assertEquals(1, stored(install));
    }

    @Test
    void sameEventIdOnOtherInstallIsSeparate() throws Exception {
        UUID a = UUID.randomUUID(), i1 = UUID.randomUUID(), i2 = UUID.randomUUID();
        send(i1, batch(ok(a)));
        send(i2, batch(ok(a)));
        assertEquals(1, stored(i1));
        assertEquals(1, stored(i2));
    }

    @Test
    void badEventsAreSkippedOthersStored() throws Exception {
        UUID install = UUID.randomUUID();
        String now = Instant.now().toString();
        String bigProps = "{\"x\":\"" + "a".repeat(2100) + "\"}";
        // 칸 모양은 짧지만 DB가 세는 글자(jsonb 글자, 쉼표·콜론 뒤 띄어쓰기 포함)로는 2KB를 넘는 props
        StringBuilder spread = new StringBuilder("{");
        for (int i = 0; i < 220; i++) spread.append(i > 0 ? "," : "").append("\"k").append(i).append("\":1");
        spread.append("}");
        assertTrue(spread.length() < 2048, "서버로 보내는 글자는 2KB 안");
        String body = batch(
                ok(UUID.randomUUID()),                                                           // 0 ok
                event(UUID.randomUUID(), "hack", "{}", now),                                     // 1 모르는 이름
                "{\"name\":\"app_open\",\"props\":{},\"occurredAt\":\"" + now + "\"}",           // 2 clientEventId 없음
                event(UUID.randomUUID(), "app_open", "[]", now),                                 // 3 props가 객체 아님
                event(UUID.randomUUID(), "app_open", bigProps, now),                             // 4 props 2KB 넘음
                event(UUID.randomUUID(), "app_open", "{}", Instant.now().plus(1, ChronoUnit.HOURS).toString()), // 5 미래
                event(UUID.randomUUID(), "app_open", "{}", "어제"),                               // 6 시각 모양
                "null",                                                                         // 7
                "{\"clientEventId\":\"not-uuid\",\"name\":\"app_open\",\"props\":{},\"occurredAt\":\"" + now + "\"}", // 8
                event(UUID.randomUUID(), "app_open", spread.toString(), now),                    // 9 DB 기준 2KB 넘음
                "{\"clientEventId\":\"" + UUID.randomUUID() + "\",\"name\":\"recs_viewed\",\"occurredAt\":\"" + now + "\"}", // 10 props 없음 → {}
                event(UUID.randomUUID(), "app_open", "\"text\"", now),                           // 11 props가 글자
                "{\"clientEventId\":\"" + UUID.randomUUID() + "\",\"name\":\"app_open\",\"props\":{}}", // 12 시각 없음
                event(UUID.randomUUID(), "favorite_added", "{\"k\":\"v\"}", now));               // 13 ok
        HttpResponse<String> r = send(install, body);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"accepted\":3,\"rejected\":[1,2,3,4,5,6,7,8,9,11,12]}", r.body());
        assertEquals(3, stored(install));
    }

    @Test
    void propsThatDbCannotStoreAreSkippedNotWholeBatch() throws Exception {
        UUID install = UUID.randomUUID();
        String now = Instant.now().toString();
        HttpResponse<String> r = send(install, batch(
                event(UUID.randomUUID(), "app_open", "{\"x\":\"a\\u0000b\"}", now),   // 0 NUL (jsonb가 못 담음)
                event(UUID.randomUUID(), "app_open", "{\"x\":\"a\\ud800b\"}", now),   // 1 짝 없는 서로게이트
                event(UUID.randomUUID(), "app_open", "{\"x\":1e400}", now),           // 2 double로 Infinity
                event(UUID.randomUUID(), "app_open", "{\"a\\u0000\":1}", now),        // 3 key에 NUL
                event(UUID.randomUUID(), "app_open", "{\"n\":{\"m\":[1,2.5,\"한글\",true,null]}}", now))); // 4 ok
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"accepted\":1,\"rejected\":[0,1,2,3]}", r.body());
        assertEquals(1, stored(install));
    }

    @Test
    void allEightNamesAreAccepted() throws Exception {
        UUID install = UUID.randomUUID();
        String now = Instant.now().toString();
        String[] names = {"app_open", "blend_started", "blend_summary_viewed", "recs_viewed", "perfume_opened", "blend_saved", "favorite_added", "feedback_submitted"};
        String[] es = new String[names.length];
        for (int i = 0; i < names.length; i++) es[i] = event(UUID.randomUUID(), names[i], "{}", now);
        assertEquals("{\"accepted\":8,\"rejected\":[]}", send(install, batch(es)).body());
    }

    @Test
    void hundredOkHundredOneIs400() throws Exception {
        UUID install = UUID.randomUUID();
        assertEquals("{\"accepted\":100,\"rejected\":[]}", send(install, many(100)).body());
        UUID other = UUID.randomUUID();
        assertError(send(other, many(101)), 400, "BAD_REQUEST");
        assertEquals(0, stored(other), "101개 묶음은 하나도 저장 안 함");
    }

    @ParameterizedTest(name = "잘못된 묶음: {0}")
    @ValueSource(strings = {
            "{\"appVersion\":\"1.0.0\",\"events\":[]}",
            "{\"appVersion\":\"1.0.0\"}",
            "{\"appVersion\":\"1.0.0\",\"events\":null}",
            "{\"appVersion\":\"1.0.0\",\"events\":{}}",
            "{\"events\":[EV]}",
            "{\"appVersion\":\"\",\"events\":[EV]}",
            "{\"appVersion\":\"1.0.0 beta\",\"events\":[EV]}",
            "{\"appVersion\":\"" + "1" + "234567890123456789012345678901234\",\"events\":[EV]}",
            "[]", "not json"
    })
    void badBatchIs400(String template) throws Exception {
        UUID install = UUID.randomUUID();
        assertError(send(install, template.replace("EV", ok(UUID.randomUUID()))), 400, "BAD_REQUEST");
        assertEquals(0, stored(install));
    }

    @Test
    void installIdIsRequired() throws Exception {
        assertError(post(PATH, batch(ok(UUID.randomUUID())), "X-Forwarded-For", ip()), 400, "BAD_REQUEST");
        assertError(post(PATH, batch(ok(UUID.randomUUID())), "X-Install-Id", "not-a-uuid", "X-Forwarded-For", ip()), 400, "BAD_REQUEST");
        assertError(post(PATH, batch(ok(UUID.randomUUID())), "X-Install-Id", "", "X-Forwarded-For", ip()), 400, "BAD_REQUEST");
    }

    @Test
    void tenBatchesPerMinutePerInstallAndIp() throws Exception {
        UUID install = UUID.randomUUID();
        String fixedIp = ip();
        for (int i = 0; i < 10; i++) {
            assertEquals(200, post(PATH, batch(ok(UUID.randomUUID())), "X-Install-Id", install.toString(), "X-Forwarded-For", fixedIp).statusCode(), "요청 " + (i + 1));
        }
        HttpResponse<String> r = post(PATH, batch(ok(UUID.randomUUID())), "X-Install-Id", install.toString(), "X-Forwarded-For", fixedIp);
        assertError(r, 429, "RATE_LIMITED");
        assertTrue(Long.parseLong(r.headers().firstValue("Retry-After").orElseThrow()) >= 1);
        assertEquals(10, stored(install));
    }
}
