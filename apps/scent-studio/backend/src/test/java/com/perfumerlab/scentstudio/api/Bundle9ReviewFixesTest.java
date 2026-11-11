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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 9 레드팀 1번(2026-11-11, 사장 결정 "틀린 토큰만 막기"):
 * 틀린 토큰이 잦은 IP를 막아도 같은 IP의 정상 사용자(맞는 토큰·토큰 없는 요청)는 막지 않는다(통신사 공유 IP·공용 와이파이).
 * 막힌 IP에서는 위조·깨진 토큰만 429. 서명은 맞는데 만료·로그아웃(세션 끝남)인 토큰은 "틀린 토큰"으로 세지 않는다(401 그대로 → 앱이 새로 로그인).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.Config.class)
class Bundle9ReviewFixesTest extends ApiTestBase {

    static final AtomicInteger NEXT_IP = new AtomicInteger(1);

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

    static String newIp() {
        int n = NEXT_IP.getAndIncrement();
        return "203.0." + (113 + n / 250) + "." + (1 + n % 250);
    }

    void blockIp(String ip) throws Exception {
        for (int i = 0; i < 20; i++) {
            assertEquals(401, get("/api/v1/me", "Authorization", "Bearer forged" + i, "X-Forwarded-For", ip).statusCode(), "실패 " + i);
        }
    }

    @Test
    void blockedIpStillServesValidTokensAndAnonymousRequests() throws Exception {
        String ip = newIp();
        blockIp(ip);
        String good = TestAuth.newUser().bearer();
        assertEquals(200, get("/api/v1/me", "Authorization", good, "X-Forwarded-For", ip).statusCode(), "같은 IP의 정상 사용자");
        assertEquals(200, get("/api/v1/health", "X-Forwarded-For", ip).statusCode(), "토큰 없는 요청");
        HttpResponse<String> forged = get("/api/v1/me", "Authorization", "Bearer forged-more", "X-Forwarded-For", ip);
        RateLimitApiTest.assertLimited(forged, "막힌 IP의 틀린 토큰은 429", 300);
        HttpResponse<String> forgedOnPublic = get("/api/v1/catalog/latest", "Authorization", "Bearer forged-more", "X-Forwarded-For", ip);
        RateLimitApiTest.assertLimited(forgedOnPublic, "로그인 없이 부르는 주소에 틀린 토큰을 붙여도 429", 300);
    }

    @Test
    void expiredButGenuineTokensAreNotCountedAsForged() throws Exception {
        String ip = newIp();
        TestAuth.User u = TestAuth.newUser();
        long now = clock.millis();
        String expired = "Bearer " + TestAuth.token(u, c -> c.issueTime(new Date(now - 3_600_000)).expirationTime(new Date(now - 120_000)));
        for (int i = 0; i < 30; i++) {
            assertEquals(401, get("/api/v1/me", "Authorization", expired, "X-Forwarded-For", ip).statusCode(), "만료 토큰은 늘 401(429 아님) " + i);
        }
        assertEquals(401, get("/api/v1/me", "Authorization", "Bearer forged", "X-Forwarded-For", ip).statusCode(), "만료 토큰 30번으로 IP가 막히지 않음");
    }

    @Test
    void loggedOutTokensAreNotCountedAsForged() throws Exception {
        String ip = newIp();
        TestAuth.User u = TestAuth.newUser();
        TestAuth.deleteSession(u.session()); // 로그아웃
        for (int i = 0; i < 30; i++) {
            assertEquals(401, get("/api/v1/me", "Authorization", u.bearer(), "X-Forwarded-For", ip).statusCode(), "로그아웃 토큰은 늘 401 " + i);
        }
        assertEquals(401, get("/api/v1/me", "Authorization", "Bearer forged", "X-Forwarded-For", ip).statusCode(), "로그아웃 토큰 30번으로 IP가 막히지 않음");
    }

    // QA 중간: 세지 않는 실패(만료·로그아웃)도 IP 제한(로그인 없이 분당 60)은 받는다. 로그아웃 토큰을 쏟아 DB 세션 조회를 늘리지 못하게
    @Test
    void notCountedFailuresStillUseTheIpLimit() throws Exception {
        String ip = newIp();
        TestAuth.User u = TestAuth.newUser();
        TestAuth.deleteSession(u.session());
        for (int i = 0; i < 60; i++) {
            assertEquals(401, get("/api/v1/me", "Authorization", u.bearer(), "X-Forwarded-For", ip).statusCode(), "60번까지 401 " + i);
        }
        RateLimitApiTest.assertLimited(get("/api/v1/me", "Authorization", u.bearer(), "X-Forwarded-For", ip), "61번째는 IP 분당 한도", 60);
        assertEquals(200, get("/api/v1/me", "Authorization", TestAuth.newUser().bearer(), "X-Forwarded-For", ip).statusCode(), "같은 IP의 맞는 토큰은 사용자 바구니라 그대로 됨");
    }

    // QA 낮음: 모양은 맞고 다른 키로 서명한 토큰(진짜 위조)도 센다
    @Test
    void wellFormedTokensSignedWithAnotherKeyAreCounted() throws Exception {
        String ip = newIp();
        TestAuth.User u = TestAuth.newUser();
        for (int i = 0; i < 20; i++) {
            String forged = "Bearer " + TestAuth.sign(TestAuth.OTHER_KEY, u, c -> { });
            assertEquals(401, get("/api/v1/me", "Authorization", forged, "X-Forwarded-For", ip).statusCode(), "실패 " + i);
        }
        RateLimitApiTest.assertLimited(get("/api/v1/me", "Authorization", "Bearer " + TestAuth.sign(TestAuth.OTHER_KEY, u, c -> { }), "X-Forwarded-For", ip),
                "다른 키 서명 20번 뒤 막힘", 300);
        assertEquals(200, get("/api/v1/me", "Authorization", u.bearer(), "X-Forwarded-For", ip).statusCode(), "진짜 토큰은 통과");
    }

    @Test
    void blockStillEndsAfter5Minutes() throws Exception {
        String ip = newIp();
        blockIp(ip);
        clock.advance(Duration.ofMinutes(4));
        RateLimitApiTest.assertLimited(get("/api/v1/me", "Authorization", "Bearer forged-x", "X-Forwarded-For", ip), "4분 뒤 틀린 토큰은 아직 막힘", 60);
        clock.advance(Duration.ofSeconds(61));
        assertEquals(401, get("/api/v1/me", "Authorization", "Bearer forged-y", "X-Forwarded-For", ip).statusCode(), "5분 지나면 다시 401");
    }
}
