package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 5: 시향 기록 (db_design.md ④·4-3·8절, 승인 D4). 1차는 만들기·목록·본인 삭제만(수정은 나중 차수).
 * - 남의 기록 id(없는 id, uuid가 아닌 id)로 지우기 → 404, 남의 조합을 blendId로 연결 → 404(조합이 있는지 알려 주지 않음)
 * - 카탈로그에 없는 향수 key → 400 UNKNOWN_KEY, 칸 검사 → 400 BAD_REQUEST
 * - 1인당 1,000개(사용자 단위 잠금), 넘으면 409 LIMIT_REACHED
 */
class FeedbackApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/feedback";

    static String fb(String perfumeKey, String extra) {
        return "{\"perfumeKey\":\"" + perfumeKey + "\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"" + extra + "}";
    }

    static final String MIN = fb("Brand:perfume", "");

    static void prefill(UUID user, int n) {
        TestAuth.admin("insert into app.tasting_feedback (user_id, perfume_key, satisfaction, similarity, engine_version, catalog_version)"
                + " select ?, 'Brand:perfume', 3, 3, 8, '2026.11.05' from generate_series(1, ?) g", user, n);
    }

    static long count(UUID user) throws Exception {
        return dbCount("select count(*) from app.tasting_feedback where user_id = ?", user);
    }

    @Test
    void createThenList() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals("{\"feedback\":[]}", getAs(u, PATH).body());
        String testedAt = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        HttpResponse<String> r = postAs(u, PATH, fb("Le_Re_Noir:455-tabac-ecarlate",
                ",\"memo\":\"첫 줄\\n둘째 줄\",\"testedAt\":\"" + testedAt + "\",\"rank\":2,\"score\":0.81"));
        assertEquals(201, r.statusCode(), r.body());
        String id = BlendsApiTest.idOf(r);
        assertTrue(r.body().contains("\"perfumeKey\":\"Le_Re_Noir:455-tabac-ecarlate\",\"blendId\":null,\"satisfaction\":4,\"similarity\":3"
                + ",\"memo\":\"첫 줄\\n둘째 줄\",\"testedAt\":\"" + testedAt + "\",\"rank\":2,\"score\":0.81,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\""), r.body());
        assertEquals("{\"feedback\":[" + r.body() + "]}", getAs(u, PATH).body());
        assertTrue(r.body().contains("\"id\":\"" + id + "\""));
    }

    @Test
    void optionalFieldsDefault() throws Exception {
        TestAuth.User u = consentedUser();
        Instant before = Instant.now().minusSeconds(5);
        HttpResponse<String> r = postAs(u, PATH, MIN);
        assertEquals(201, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"memo\":null") && r.body().contains("\"rank\":null") && r.body().contains("\"score\":null"), r.body());
        String testedAt = r.body().replaceAll(".*\"testedAt\":\"([^\"]+)\".*", "$1");
        assertFalse(Instant.parse(testedAt).isBefore(before), "시향 시각을 안 보내면 지금");
    }

    @Test
    void listIsNewestTestedFirst() throws Exception {
        TestAuth.User u = consentedUser();
        String old = BlendsApiTest.idOf(postAs(u, PATH, fb("Brand:perfume", ",\"testedAt\":\"" + Instant.now().minus(3, ChronoUnit.DAYS) + "\"")));
        String recent = BlendsApiTest.idOf(postAs(u, PATH, fb("Brand:perfume", ",\"testedAt\":\"" + Instant.now().minus(1, ChronoUnit.DAYS) + "\"")));
        String body = getAs(u, PATH).body();
        assertTrue(body.indexOf(recent) < body.indexOf(old), body);
    }

    // ---------- 조합 연결 ----------

    @Test
    void linkOwnBlend() throws Exception {
        TestAuth.User u = consentedUser();
        String blend = BlendsApiTest.idOf(postAs(u, BlendsApiTest.PATH, BlendsApiTest.blend("\"내 조합\"", "[]", BlendsApiTest.ONE_NOTE)));
        HttpResponse<String> r = postAs(u, PATH, fb("Brand:perfume", ",\"blendId\":\"" + blend + "\""));
        assertEquals(201, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"blendId\":\"" + blend + "\""), r.body());
        // 조합을 지우면 기록은 남고 연결만 비워진다
        assertEquals(204, deleteAs(u, BlendsApiTest.PATH + "/" + blend).statusCode());
        String list = getAs(u, PATH).body();
        assertTrue(list.contains("\"blendId\":null"), list);
        assertEquals(1, count(u.id()));
    }

    @Test
    void othersOrMissingBlendIs404() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        String aBlend = BlendsApiTest.idOf(postAs(a, BlendsApiTest.PATH, BlendsApiTest.blend("\"A 조합\"", "[]", BlendsApiTest.ONE_NOTE)));
        HttpResponse<String> others = postAs(b, PATH, fb("Brand:perfume", ",\"blendId\":\"" + aBlend + "\""));
        assertError(others, 404, "NOT_FOUND");
        HttpResponse<String> missing = postAs(b, PATH, fb("Brand:perfume", ",\"blendId\":\"" + UUID.randomUUID() + "\""));
        assertEquals(others.body(), missing.body(), "남의 것과 없는 것이 같은 답");
        assertEquals(0, count(b.id()));
    }

    // ---------- 지우기 ----------

    @Test
    void deleteOwn() throws Exception {
        TestAuth.User u = consentedUser();
        String id = BlendsApiTest.idOf(postAs(u, PATH, MIN));
        assertEquals(204, deleteAs(u, PATH + "/" + id).statusCode());
        assertEquals("{\"feedback\":[]}", getAs(u, PATH).body());
        assertError(deleteAs(u, PATH + "/" + id), 404, "NOT_FOUND");
    }

    @Test
    void deleteOthersIs404AndKeepsIt() throws Exception {
        TestAuth.User a = consentedUser(), b = consentedUser();
        String id = BlendsApiTest.idOf(postAs(a, PATH, MIN));
        HttpResponse<String> r = deleteAs(b, PATH + "/" + id);
        assertError(r, 404, "NOT_FOUND");
        assertEquals(r.body(), deleteAs(b, PATH + "/" + UUID.randomUUID()).body(), "없는 id와 같은 답");
        assertEquals(1, count(a.id()), "a의 기록은 그대로");
        assertEquals("{\"feedback\":[]}", getAs(b, PATH).body(), "b 목록에는 안 보임");
    }

    @ParameterizedTest(name = "uuid가 아닌 id: {0}")
    @ValueSource(strings = {"abc", "1", "00000000-0000-0000-0000-00000000000g"})
    void malformedIdIs404(String id) throws Exception {
        assertError(deleteAs(consentedUser(), PATH + "/" + id), 404, "NOT_FOUND");
    }

    // ---------- 검사 ----------

    @Test
    void unknownPerfumeIs400() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, fb("Brand:no-such-perfume", "")), 400, "UNKNOWN_KEY");
        assertError(postAs(u, PATH, fb("rose", "")), 400, "UNKNOWN_KEY");
        assertEquals(0, count(u.id()));
    }

    @ParameterizedTest(name = "잘못된 시향 기록: {0}")
    @ValueSource(strings = {
            "{\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",   // 향수 없음
            "{\"perfumeKey\":null,\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\" Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}", // 만족도 없음
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":0,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":6,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":3.5,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":\"4\",\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}", // 비슷함 없음
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":0,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":6,\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"catalogVersion\":\"2026.11.05\"}",       // 엔진 버전 없음
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":0,\"catalogVersion\":\"2026.11.05\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8}",                       // 카탈로그 버전 없음
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"CV33\"}",
            "{\"perfumeKey\":\"Brand:perfume\",\"satisfaction\":4,\"similarity\":3,\"engineVersion\":8,\"catalogVersion\":\"2026 11\"}",
            "BASE,\"memo\":\"M501\"}",
            "BASE,\"memo\":\"a\\u0000b\"}",
            "BASE,\"memo\":\"a\\ud800b\"}",
            "BASE,\"rank\":0}", "BASE,\"rank\":11}", "BASE,\"rank\":1.5}",
            "BASE,\"score\":-0.01}", "BASE,\"score\":1.01}", "BASE,\"score\":\"0.5\"}",
            "BASE,\"testedAt\":\"어제\"}", "BASE,\"testedAt\":\"FUTURE\"}", "BASE,\"testedAt\":\"2019-12-31T23:59:59Z\"}",
            "BASE,\"blendId\":\"not-a-uuid\"}", "BASE,\"blendId\":1}",
            "[]", "not json"
    })
    void badShapesAre400(String template) throws Exception {
        String base = MIN.substring(0, MIN.length() - 1);
        String body = template.replace("BASE", base)
                .replace("CV33", "1".repeat(33))
                .replace("M501", "가".repeat(501))
                .replace("FUTURE", Instant.now().plus(1, ChronoUnit.HOURS).toString());
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, body), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    @Test
    void edgesThatAreAllowed() throws Exception {
        TestAuth.User u = consentedUser();
        String base = MIN.substring(0, MIN.length() - 1);
        assertEquals(201, postAs(u, PATH, base + ",\"memo\":\"" + "가".repeat(500) + "\"}").statusCode(), "메모 500자");
        assertEquals(201, postAs(u, PATH, base + ",\"rank\":1,\"score\":0}").statusCode());
        assertEquals(201, postAs(u, PATH, base + ",\"rank\":10,\"score\":1}").statusCode());
        assertEquals(201, postAs(u, PATH, base + ",\"testedAt\":\"" + Instant.now().plusSeconds(60) + "\"}").statusCode(), "기기 시계 조금 빠름(5분 안)");
    }

    @Test
    void writesNeedConsentReadsDoNot() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        prefill(u.id(), 1);
        String id = Bundle4ReviewFixesTest.TestDbQuery.text("select id::text from app.tasting_feedback where user_id = ?", u.id());
        assertError(postAs(u, PATH, MIN), 403, "CONSENT_REQUIRED");
        assertError(deleteAs(u, PATH + "/" + id), 403, "CONSENT_REQUIRED");
        assertEquals(200, getAs(u, PATH).statusCode());
        assertEquals(1, count(u.id()));
    }

    // ---------- 1인당 1,000개 ----------

    @Test
    void limit1000() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 999);
        assertEquals(201, postAs(u, PATH, MIN).statusCode());
        assertError(postAs(u, PATH, MIN), 409, "LIMIT_REACHED");
        assertEquals(1000, count(u.id()));
    }

    @Test
    void twentyAtOnceNeverExceed1000() throws Exception {
        TestAuth.User u = consentedUser();
        prefill(u.id(), 990);
        List<HttpResponse<String>> rs = together(20, i -> () -> postAs(u, PATH, MIN));
        assertEquals(10, countStatus(rs, 201));
        assertEquals(10, countStatus(rs, 409));
        rs.stream().filter(r -> r.statusCode() == 409).forEach(r -> assertError(r, 409, "LIMIT_REACHED"));
        assertEquals(1000, count(u.id()));
    }
}
