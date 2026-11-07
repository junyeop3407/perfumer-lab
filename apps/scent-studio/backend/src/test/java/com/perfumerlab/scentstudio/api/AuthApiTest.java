package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 3: 로그인 토큰 확인 (server_design.md 3·3-1·8절). 401 경우 9가지 + 그 밖의 모양, 세션 확인 30초 보관.
 * 아직 로그인 뒤 쓰는 API가 없어서, 토큰이 통과하면 없는 주소의 404(NOT_FOUND)가 된다 → "401이 아님"으로 통과를 본다.
 * 요청마다 X-Forwarded-For 맨 끝 값(= 요청한 사람 IP)을 다르게 줘서 요청 제한에 걸리지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.Config.class)
class AuthApiTest extends ApiTestBase {

    static final String PATH = "/api/v1/me";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
        r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
    }

    @Autowired
    TestClock clock;

    HttpResponse<String> call(String authorization) throws Exception {
        String ip = "10.9." + (int) (Math.random() * 250) + "." + (int) (1 + Math.random() * 250);
        return authorization == null ? get(PATH, "X-Forwarded-For", ip) : get(PATH, "Authorization", authorization, "X-Forwarded-For", ip);
    }

    void assertUnauthorized(HttpResponse<String> r, String why) {
        assertEquals(401, r.statusCode(), why + " " + r.body());
        assertEquals("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요해요\"}}", r.body(), why + ": 이유를 드러내지 않는 같은 응답");
    }

    void assertPassed(HttpResponse<String> r, String why) {
        assertEquals(404, r.statusCode(), why + " " + r.body());
        assertTrue(r.body().contains("\"code\":\"NOT_FOUND\""), why + " " + r.body());
    }

    @Test
    void validTokenPasses() throws Exception {
        assertPassed(call(TestAuth.newUser().bearer()), "정상 토큰");
    }

    // ---------- 401 경우 9가지 (server_design.md 8절) ----------

    @Test
    void noToken() throws Exception {
        assertUnauthorized(call(null), "토큰 없음");
        assertUnauthorized(call("Bearer "), "빈 토큰");
        assertUnauthorized(call("Bearer not.a.jwt"), "토큰 모양 아님");
        assertUnauthorized(call("Basic dXNlcjpwYXNz"), "Bearer 아님");
    }

    @Test
    void signedByAnotherKey() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.sign(TestAuth.OTHER_KEY, u, c -> { })), "같은 kid, 다른 키");
    }

    @Test
    void algNone() throws Exception {
        assertUnauthorized(call("Bearer " + TestAuth.none(TestAuth.newUser())), "alg none");
    }

    @Test
    void hs256() throws Exception {
        assertUnauthorized(call("Bearer " + TestAuth.hs256(TestAuth.newUser())), "HS256");
    }

    @Test
    void expired() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        long now = clock.millis(); // 서버가 쓰는 시계 기준 (다른 테스트가 시계를 앞으로 돌려도 같게)
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.issueTime(new Date(now - 3_600_000)).expirationTime(new Date(now - 60_000)))), "60초 전 만료");
        assertPassed(call("Bearer " + TestAuth.token(u, c -> c.issueTime(new Date(now - 3_600_000)).expirationTime(new Date(now - 10_000)))), "10초 전 만료는 시계 오차 30초 안");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.expirationTime(null))), "만료 시각 없음");
    }

    @Test
    void wrongAudience() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.audience("other"))), "aud 다름");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.audience((String) null))), "aud 없음");
    }

    @Test
    void anonRoleToken() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("role", "anon"))), "anon 토큰(공개 키 모양)");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("role", "service_role"))), "service_role");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("role", null))), "role 없음");
    }

    @Test
    void anonymousUser() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("is_anonymous", true))), "익명 사용자");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("is_anonymous", "false"))), "is_anonymous가 참거짓 값이 아님");
    }

    @Test
    void sessionMissing() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("session_id", UUID.randomUUID().toString()))), "없는 세션");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("session_id", null))), "session_id 없음");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("session_id", "not-a-uuid"))), "session_id 모양 아님");
        TestAuth.User other = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.claim("session_id", other.session().toString()))), "남의 세션");
    }

    // ---------- 그 밖의 모양 ----------

    @Test
    void wrongIssuerOrSubject() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.issuer("https://other-ref.supabase.co/auth/v1"))), "다른 프로젝트");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.issuer(null))), "iss 없음");
        assertUnauthorized(call("Bearer " + TestAuth.token(u, c -> c.subject("not-a-uuid"))), "sub 모양 아님");
    }

    @Test
    void tokenInQueryIsIgnored() throws Exception {
        String ip = "10.8.0.1";
        TestAuth.User u = TestAuth.newUser();
        // 테스트 실행 중에 만든 가짜 토큰. (커밋 훅의 비밀값 규칙이 '이름=" + 변수' 모양을 비밀값으로 오탐해서 주소를 먼저 만든다, review.md 묶음 3)
        String queryParam = "access_token";
        String url = PATH + "?" + queryParam + "=" + u.token();
        assertUnauthorized(get(url, "X-Forwarded-For", ip), "주소에 넣은 토큰은 안 읽음");
        assertPassed(get(PATH, "Authorization", u.bearer(), "X-Forwarded-For", ip), "같은 토큰을 헤더로 보내면 통과(대조)");
    }

    // ---------- 세션 확인 30초 보관 (3-1) ----------

    @Test
    void loggedOutSessionIsRejectedWithin30Seconds() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        String bearer = u.bearer();
        assertPassed(call(bearer), "처음");
        TestAuth.deleteSession(u.session()); // 로그아웃
        assertPassed(call(bearer), "30초 보관 중에는 아직 통과");
        clock.advance(Duration.ofSeconds(31));
        assertUnauthorized(call(bearer), "31초 뒤 세션 다시 확인 → 막힘");
    }

    @Test
    void deletedAccountIsRejected() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        TestAuth.admin("delete from auth.users where id = ?", u.id());
        assertUnauthorized(call(u.bearer()), "계정 삭제 뒤(세션도 같이 지워짐)");
    }

    @Test
    void healthStillOpenWithoutToken() throws Exception {
        assertEquals(200, get("/api/v1/health", "X-Forwarded-For", "10.7.0.1").statusCode());
        assertEquals(200, get("/api/v1/health", "Authorization", "Bearer garbage", "X-Forwarded-For", "10.7.0.2").statusCode(),
                "로그인 없이 부르는 주소는 이상한 토큰이 붙어도 열림");
    }
}
