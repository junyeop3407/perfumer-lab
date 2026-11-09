package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 7: 오류 신고 POST /api/v1/reports (server_design.md 2·4절, 사장 결정 2026-11-06 "로그인한 사람만", 승인 K3·K4).
 * - 로그인 없음 401, 동의 전 403 CONSENT_REQUIRED, 칸 검사 400, 카탈로그에 없는 향수·노트 400 UNKNOWN_KEY
 * - 성공은 204(서버는 신고를 읽을 권한이 없음 → 돌려줄 내용 없음). 시간당 20회는 RateLimitApiTest.reportsAre20PerHour
 */
class ReportsApiTest extends MeApiTestBase {

    static final String PATH = "/api/v1/reports";

    static String rep(String fields) {
        return "{" + fields + ",\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\",\"appVersion\":\"1.0.0\"}";
    }

    static final String MIN = rep("\"kind\":\"not_similar\",\"perfumeKey\":\"Brand:perfume\"");

    static long count(UUID user) throws Exception {
        return dbCount("select count(*) from app.error_reports where user_id = ?", user);
    }

    @Test
    void savesReportWithAllFields() throws Exception {
        TestAuth.User u = consentedUser();
        HttpResponse<String> r = postAs(u, PATH, rep("\"kind\":\"not_similar\",\"perfumeKey\":\"Fake_Maison:123-sample-scent\","
                + "\"memo\":\"우디가 너무 강해요\\n둘째 줄\",\"rank\":3,\"score\":0.71"));
        assertEquals(204, r.statusCode(), r.body());
        assertEquals("", r.body());
        assertEquals(1, dbCount("select count(*) from app.error_reports where user_id = ? and kind = 'not_similar'"
                + " and perfume_key = 'Fake_Maison:123-sample-scent' and note_key is null and memo = ? and rank = 3 and score = 0.71"
                + " and engine_version = 8 and catalog_version = '2026.11.05' and app_version = '1.0.0' and status = 'open'",
                u.id(), "우디가 너무 강해요\n둘째 줄"));
    }

    @Test
    void minimalReport() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, postAs(u, PATH, MIN).statusCode());
        assertEquals(1, dbCount("select count(*) from app.error_reports where user_id = ? and memo is null and rank is null and score is null", u.id()));
    }

    @Test
    void noteOnlyReport() throws Exception {
        TestAuth.User u = consentedUser();
        assertEquals(204, postAs(u, PATH, rep("\"kind\":\"note_name\",\"noteKey\":\"bergamot\"")).statusCode());
        assertEquals(1, count(u.id()));
    }

    @Test
    void sameReportTwiceIsTwoReports() throws Exception {
        TestAuth.User u = consentedUser();
        postAs(u, PATH, MIN);
        postAs(u, PATH, MIN);
        assertEquals(2, count(u.id()));
    }

    @Test
    void reportIsStoredForTheTokenUserOnly() throws Exception {
        TestAuth.User u = consentedUser(), other = consentedUser();
        // 본문에 다른 사람 id를 넣어도 모르는 칸이라 무시(앱 전체 규칙), 저장은 토큰의 사용자
        assertEquals(204, postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"userId\":\"" + other.id() + "\"")).statusCode());
        assertEquals(1, count(u.id()));
        assertEquals(0, count(other.id()));
    }

    @Test
    void statusCannotBeSetByClient() throws Exception {
        TestAuth.User u = consentedUser();
        // 처리 상태·만든 때·id를 보내도 무시 → 늘 open, DB가 정한 시각
        assertEquals(204, postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"status\":\"resolved\","
                + "\"createdAt\":\"2020-01-01T00:00:00Z\",\"id\":\"" + UUID.randomUUID() + "\"")).statusCode());
        assertEquals(1, dbCount("select count(*) from app.error_reports where user_id = ? and status = 'open'"
                + " and created_at > now() - interval '1 minute'", u.id()));
    }

    @Test
    void needsLogin() throws Exception {
        HttpResponse<String> r = post(PATH, MIN, "X-Forwarded-For", ip());
        assertError(r, 401, "UNAUTHORIZED");
    }

    @Test
    void needsConsent() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        consent(u.id(), "terms", "privacy"); // 만 14세 확인 빠짐
        assertError(postAs(u, PATH, MIN), 403, "CONSENT_REQUIRED");
        assertEquals(0, count(u.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"kind\":\"bug\",\"perfumeKey\":\"Brand:perfume\"",          // 모르는 종류
            "\"perfumeKey\":\"Brand:perfume\"",                           // 종류 없음
            "\"kind\":\"other\"",                                          // 향수도 노트도 없음
            "\"kind\":\"other\",\"perfumeKey\":\"\"",                     // 빈 key
            "\"kind\":\"other\",\"perfumeKey\":\" Brand:perfume\"",       // 앞 공백
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"rank\":0",
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"rank\":11",
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"score\":1.5",
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"score\":-0.1",
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"kind\":\"other\"", // 같은 칸 두 번
            "\"kind\":\"other\",\"perfumeKey\":1",                  // key 자리에 숫자
            "\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"rank\":2.5",
    })
    void rejectsBadFields(String fields) throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, rep(fields)), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    @Test
    void memoUpTo500Characters() throws Exception {
        TestAuth.User u = consentedUser();
        String ok = "가".repeat(499) + "😀"; // 이모지도 한 글자
        assertEquals(204, postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"memo\":\"" + ok + "\"")).statusCode());
        assertError(postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"memo\":\"" + "가".repeat(501) + "\"")), 400, "BAD_REQUEST");
        assertEquals(1, count(u.id()));
    }

    @Test
    void memoWithNulOrBrokenCharacterIs400Not500() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"memo\":\"a\\u0000b\"")), 400, "BAD_REQUEST");
        assertError(postAs(u, PATH, rep("\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\",\"memo\":\"a\\ud800b\"")), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"engineVersion\":0,\"catalogVersion\":\"2026.11.05\",\"appVersion\":\"1.0.0\"",
            "\"catalogVersion\":\"2026.11.05\",\"appVersion\":\"1.0.0\"",
            "\"engineVersion\":8,\"catalogVersion\":\"\",\"appVersion\":\"1.0.0\"",
            "\"engineVersion\":8,\"catalogVersion\":\"2026 11 05\",\"appVersion\":\"1.0.0\"",
            "\"engineVersion\":8,\"appVersion\":\"1.0.0\"",
            "\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\"",
            "\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\",\"appVersion\":\"\"",
            "\"engineVersion\":8,\"catalogVersion\":\"2026.11.05\",\"appVersion\":\"1.0.0 beta\"",
    })
    void rejectsBadVersions(String versions) throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, "{\"kind\":\"other\",\"perfumeKey\":\"Brand:perfume\"," + versions + "}"), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    @Test
    void unknownPerfumeOrNoteKey() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, rep("\"kind\":\"wrong_info\",\"perfumeKey\":\"Nobody:nothing\"")), 400, "UNKNOWN_KEY");
        assertError(postAs(u, PATH, rep("\"kind\":\"note_name\",\"noteKey\":\"zz-not-a-note\"")), 400, "UNKNOWN_KEY");
        assertError(postAs(u, PATH, rep("\"kind\":\"note_name\",\"perfumeKey\":\"Brand:perfume\",\"noteKey\":\"zz-not-a-note\"")), 400, "UNKNOWN_KEY");
        assertEquals(0, count(u.id()));
    }

    @Test
    void notJsonOrEmptyBody() throws Exception {
        TestAuth.User u = consentedUser();
        assertError(postAs(u, PATH, "not json"), 400, "BAD_REQUEST");
        assertError(postAs(u, PATH, "{}"), 400, "BAD_REQUEST");
        assertError(postAs(u, PATH, "null"), 400, "BAD_REQUEST");
        assertEquals(0, count(u.id()));
    }

    @Test
    void otherMethodsAreNotAllowed() throws Exception {
        TestAuth.User u = consentedUser();
        postAs(u, PATH, MIN);
        int list = getAs(u, PATH).statusCode();
        assertTrue(list == 405 || list == 404, "신고 목록은 내주지 않음: " + list);
        int del = deleteAs(u, PATH).statusCode();
        assertTrue(del == 405 || del == 404, "신고 지우기 없음: " + del);
        assertEquals(1, count(u.id()));
    }

    @Test
    void reportsAreDeletedWithTheAccount() throws Exception {
        TestAuth.User u = consentedUser();
        postAs(u, PATH, MIN);
        TestAuth.admin("delete from auth.users where id = ?", u.id());
        assertEquals(0, count(u.id()));
    }
}
