package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 4: GET /me, POST /me/consents, 동의 없으면 쓰기 403 CONSENT_REQUIRED (server_design.md 2절, db_design.md ⑥).
 * 동의 문서 지금 버전은 서버 설정(사장 결정 2026-11-07, 처음 값 draft-1). 가입일은 주지 않는다(사장 결정).
 */
class MeApiTest extends MeApiTestBase {

    static final String ALL = "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"},"
            + "{\"type\":\"privacy\",\"version\":\"draft-1\"},{\"type\":\"age_14\",\"version\":\"draft-1\"}]}";

    @Test
    void meShowsEmailAndRequiredConsents() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = getAs(u, "/api/v1/me");
        assertEquals(200, r.statusCode(), r.body());
        String b = r.body();
        assertTrue(b.contains("\"userId\":\"" + u.id() + "\""), b);
        assertTrue(b.contains("\"email\":\"" + u.id() + "@test.invalid\""), b);
        assertTrue(b.contains("\"required\":{\"terms\":\"draft-1\",\"privacy\":\"draft-1\",\"age_14\":\"draft-1\"}"), b);
        assertTrue(b.contains("\"agreed\":{\"terms\":false,\"privacy\":false,\"age_14\":false}"), b);
        assertTrue(b.contains("\"complete\":false"), b);
        assertFalse(b.contains("createdAt"), "가입일은 주지 않는다");
    }

    @Test
    void consentAllThenComplete() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = postAs(u, "/api/v1/me/consents", ALL);
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"complete\":true"), r.body());
        assertEquals(200, postAs(u, "/api/v1/me/consents", ALL).statusCode(), "같은 동의를 다시 보내도 성공(한 번만 기록)");
        assertTrue(getAs(u, "/api/v1/me").body().contains("\"agreed\":{\"terms\":true,\"privacy\":true,\"age_14\":true}"));
    }

    @Test
    void partialConsentIsNotComplete() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = postAs(u, "/api/v1/me/consents",
                "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"},{\"type\":\"privacy\",\"version\":\"draft-1\"}]}");
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"agreed\":{\"terms\":true,\"privacy\":true,\"age_14\":false}"), r.body());
        assertTrue(r.body().contains("\"complete\":false"), r.body());
    }

    @Test
    void oldVersionConsentIsRejected() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertError(postAs(u, "/api/v1/me/consents",
                "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-0\"}]}"), 409, "STALE_CONSENT_VERSION");
        assertTrue(getAs(u, "/api/v1/me").body().contains("\"terms\":false"), "기록되지 않음");
    }

    @Test
    void oldVersionOnRecordDoesNotCount() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        TestAuth.admin("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, 'terms', 'draft-0', '1.0.0')", u.id());
        consent(u.id(), "privacy", "age_14");
        assertTrue(getAs(u, "/api/v1/me").body().contains("\"complete\":false"), "옛 버전 동의는 지금 동의로 치지 않음");
        assertError(putAs(u, "/api/v1/me/preferences", prefs("[]", "[]")), 403, "CONSENT_REQUIRED");
    }

    @ParameterizedTest(name = "잘못된 동의 요청: {0}")
    @ValueSource(strings = {
            "{\"appVersion\":\"1.0.0\",\"consents\":[]}",
            "{\"appVersion\":\"1.0.0\"}",
            "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"marketing\",\"version\":\"draft-1\"}]}",
            "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"},{\"type\":\"terms\",\"version\":\"draft-1\"}]}",
            "{\"appVersion\":\"1.0.0\",\"consents\":[{\"type\":\"terms\"}]}",
            "{\"appVersion\":\"1.0.0\",\"consents\":[null]}",
            "{\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"}]}",
            "{\"appVersion\":\"\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"}]}",
            "{\"appVersion\":\"123456789012345678901234567890123\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"}]}",
            "{\"appVersion\":\"1.0.0 \\n\",\"consents\":[{\"type\":\"terms\",\"version\":\"draft-1\"}]}",
            "not json", "[]", "{\"appVersion\":1,\"consents\":\"terms\"}"
    })
    void badConsentRequests(String body) throws Exception {
        assertError(postAs(TestAuth.newUser(), "/api/v1/me/consents", body), 400, "BAD_REQUEST");
    }

    @Test
    void consentNeedsJson() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        HttpResponse<String> r = send("POST", "/api/v1/me/consents", ALL, "Authorization", u.bearer(), "X-Forwarded-For", ip(),
                "Content-Type", "text/plain");
        assertError(r, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void writesNeedAllThreeConsents() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertError(putAs(u, "/api/v1/me/preferences", prefs("[]", "[]")), 403, "CONSENT_REQUIRED");
        assertError(postAs(u, "/api/v1/me/preferences/merge", prefs("[]", "[]")), 403, "CONSENT_REQUIRED");
        consent(u.id(), "terms", "privacy");
        assertError(putAs(u, "/api/v1/me/preferences", prefs("[]", "[]")), 403, "CONSENT_REQUIRED");
        consent(u.id(), "age_14");
        assertEquals(200, putAs(u, "/api/v1/me/preferences", prefs("[]", "[]")).statusCode());
    }

    @Test
    void readsDoNotNeedConsent() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertEquals(200, getAs(u, "/api/v1/me").statusCode());
        assertEquals(200, getAs(u, "/api/v1/me/preferences").statusCode());
    }

    @Test
    void consentsAreKeptPerUser() throws Exception {
        TestAuth.User a = consentedUser(), b = TestAuth.newUser();
        assertTrue(getAs(a, "/api/v1/me").body().contains("\"complete\":true"));
        assertTrue(getAs(b, "/api/v1/me").body().contains("\"complete\":false"), "남의 동의가 내 것이 되지 않음");
    }

    @Test
    void tooLargeBodyIsRejected() throws Exception {
        String big = "{\"appVersion\":\"1.0.0\",\"pad\":\"" + "a".repeat(300_000) + "\",\"consents\":[]}";
        assertError(postAs(TestAuth.newUser(), "/api/v1/me/consents", big), 413, "PAYLOAD_TOO_LARGE");
    }

    @Test
    void meNeedsLogin() throws Exception {
        assertEquals(401, get("/api/v1/me", "X-Forwarded-For", ip()).statusCode());
        assertEquals(401, post("/api/v1/me/consents", ALL, "X-Forwarded-For", ip()).statusCode());
    }
}
