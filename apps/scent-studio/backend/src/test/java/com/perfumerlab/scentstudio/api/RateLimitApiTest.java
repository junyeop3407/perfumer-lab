package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 3: 요청 횟수 제한 (server_design.md 4절 표, 4-1 헤더 조작 6가지). 넘으면 429 RATE_LIMITED + Retry-After(초).
 * 사용 기록(/events)·카탈로그 파일 제한은 그 API를 만드는 묶음 6에서.
 * 아직 없는 API(조합·찜 등)는 404지만 제한은 주소 처리 전에 걸리므로 "N번까지는 429가 아니고 N+1번째가 429"로 본다. GET /me는 묶음 4부터 200.
 * 테스트끼리 바구니가 섞이지 않게 IP·사용자를 테스트마다 새로 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.Config.class)
class RateLimitApiTest extends ApiTestBase {

    static final String PROXY_ADDED = ", "; // 프록시가 맨 끝에 진짜 IP를 덧붙인 모양: "<사용자가 보낸 값>, <진짜>"
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
        return "198.51." + (n / 250) + "." + (1 + n % 250);
    }

    static void assertLimited(HttpResponse<String> r, String why, long maxRetry) {
        assertEquals(429, r.statusCode(), why + " " + r.body());
        assertEquals("{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많아요. 잠시 후 다시 시도해 주세요\"}}", r.body(), why);
        long retry = Long.parseLong(r.headers().firstValue("Retry-After").orElseThrow(() -> new AssertionError(why + ": Retry-After 없음")));
        assertTrue(retry >= 1 && retry <= maxRetry, why + ": Retry-After " + retry);
    }

    /** n번은 429가 아니고 n+1번째가 429 */
    void limitIs(int n, long maxRetry, String why, Call call) throws Exception {
        for (int i = 1; i <= n; i++) {
            HttpResponse<String> r = call.run(i);
            assertNotEquals(429, r.statusCode(), why + " " + i + "번째");
        }
        assertLimited(call.run(n + 1), why + " " + (n + 1) + "번째", maxRetry);
    }

    @FunctionalInterface
    interface Call {
        HttpResponse<String> run(int i) throws Exception;
    }

    // ---------- 로그인 없이: IP 분당 60회 ----------

    @Test
    void anonymousIsLimitedPerIp() throws Exception {
        String ip = newIp();
        limitIs(60, 60, "로그인 없이 health", i -> get("/api/v1/health", "X-Forwarded-For", ip));
        assertNotEquals(429, get("/api/v1/health", "X-Forwarded-For", newIp()).statusCode(), "다른 IP는 따로");
        clock.advance(Duration.ofSeconds(61));
        assertNotEquals(429, get("/api/v1/health", "X-Forwarded-For", ip).statusCode(), "1분 지나면 다시 됨");
    }

    @Test
    void liveIsNeverLimited() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 80; i++) assertEquals(200, get("/api/v1/health/live", "X-Forwarded-For", ip).statusCode(), "Render 상태 확인 " + i);
    }

    // ---------- 4-1 헤더 조작 6가지 ----------

    @Test
    void header1_fakeValueInFrontIsIgnored() throws Exception {
        String real = newIp();
        limitIs(60, 60, "가짜 1.2.3.4를 앞에 넣어도 진짜 IP 바구니", i -> get("/api/v1/health", "X-Forwarded-For", "1.2.3.4" + PROXY_ADDED + real));
        assertLimited(get("/api/v1/health", "X-Forwarded-For", real), "가짜 값 없이도 같은 바구니", 60);
    }

    @Test
    void header2_rotatingFakeValuesShareOneBucket() throws Exception {
        String real = newIp();
        limitIs(60, 60, "요청마다 가짜 값을 바꿔도", i -> get("/api/v1/health", "X-Forwarded-For", "7.7.7." + i + PROXY_ADDED + real));
    }

    @Test
    void header3_oddShapesKeepRightmostRuleWithoutServerError() throws Exception {
        String real = newIp();
        limitIs(60, 60, "여러 줄·쉼표 수십 개·빈 값", i -> {
            HttpResponse<String> r = switch (i % 3) {
                case 0 -> get("/api/v1/health", "X-Forwarded-For", "1.1.1.1", "X-Forwarded-For", ", ,," + real);
                case 1 -> get("/api/v1/health", "X-Forwarded-For", ",".repeat(40) + real);
                default -> get("/api/v1/health", "X-Forwarded-For", " , " + real);
            };
            assertTrue(r.statusCode() < 500, "서버 오류 없음: " + r.statusCode());
            return r;
        });
    }

    @Test
    void header4_invalidRightmostFallsBackToConnectionAddress() throws Exception {
        // 맨 끝이 IP 모양이 아니면 연결 주소(테스트에서는 127.0.0.1)를 쓴다 → 모양이 다른 값들도 모두 한 바구니
        String[] bad = {"abc", "999.1.1.1", "203.0.113.9:443"};
        limitIs(60, 60, "모양이 다른 잘못된 값들", i -> get("/api/v1/health", "X-Forwarded-For", bad[i % 3]));
        assertLimited(get("/api/v1/health"), "헤더 없이도 같은 연결 주소 바구니", 60);
        clock.advance(Duration.ofSeconds(61)); // 다른 테스트가 연결 주소 바구니를 쓰지 않게 비움
    }

    @Test
    void header5_otherIpHeadersAreNotRead() throws Exception {
        String real = newIp();
        limitIs(60, 60, "True-Client-IP·X-Real-IP·Forwarded를 바꿔도", i -> get("/api/v1/health",
                "X-Forwarded-For", real, "True-Client-IP", "8.8.8." + i, "X-Real-IP", "8.8.4." + i,
                "Forwarded", "for=9.9.9." + i, "CF-Connecting-IP", "6.6.6." + i));
    }

    @Test
    void header6_loggedInUserBucketIgnoresIpChanges() throws Exception {
        TestAuth.User u = TestAuth.newUser();
        String bearer = u.bearer();
        limitIs(120, 60, "로그인한 사용자 읽기 분당 120회, IP를 매번 바꿔도",
                i -> get("/api/v1/me", "Authorization", bearer, "X-Forwarded-For", newIp()));
    }

    // ---------- 로그인한 사용자: 표 ----------

    @Test
    void writesAre30PerMinute() throws Exception {
        String bearer = TestAuth.newUser().bearer();
        String ip = newIp();
        limitIs(30, 60, "저장·찜·시향·취향 바꾸기(POST·PUT·DELETE)", i -> switch (i % 3) {
            case 0 -> post("/api/v1/blends", "{}", "Authorization", bearer, "X-Forwarded-For", ip);
            case 1 -> send("PUT", "/api/v1/favorites/Brand:perfume", null, "Authorization", bearer, "X-Forwarded-For", ip);
            default -> send("DELETE", "/api/v1/feedback/" + java.util.UUID.randomUUID(), null, "Authorization", bearer, "X-Forwarded-For", ip);
        });
        assertNotEquals(429, get("/api/v1/blends", "Authorization", bearer, "X-Forwarded-For", ip).statusCode(), "읽기는 따로");
    }

    @Test
    void mergeIs5PerMinute() throws Exception {
        String bearer = TestAuth.newUser().bearer();
        limitIs(5, 60, "취향 합치기", i -> post("/api/v1/me/preferences/merge", "{}", "Authorization", bearer, "X-Forwarded-For", newIp()));
        assertNotEquals(429, send("PUT", "/api/v1/me/preferences", "{}", "Authorization", bearer, "X-Forwarded-For", newIp()).statusCode(),
                "합치기 한도는 다른 쓰기와 따로");
    }

    @Test
    void reportsAre20PerHour() throws Exception {
        String bearer = TestAuth.newUser().bearer();
        limitIs(20, 3600, "오류 신고", i -> post("/api/v1/reports", "{}", "Authorization", bearer, "X-Forwarded-For", newIp()));
        clock.advance(Duration.ofMinutes(2));
        assertLimited(post("/api/v1/reports", "{}", "Authorization", bearer, "X-Forwarded-For", newIp()), "2분 뒤에도 시간당 한도", 3600);
    }

    @Test
    void accountDeletionIs3PerHour() throws Exception {
        String bearer = TestAuth.newUser().bearer();
        limitIs(3, 3600, "계정 삭제", i -> send("DELETE", "/api/v1/me", null, "Authorization", bearer, "X-Forwarded-For", newIp()));
    }

    @Test
    void usersHaveSeparateBuckets() throws Exception {
        String a = TestAuth.newUser().bearer(), b = TestAuth.newUser().bearer();
        limitIs(5, 60, "사용자 A 합치기", i -> post("/api/v1/me/preferences/merge", "{}", "Authorization", a, "X-Forwarded-For", newIp()));
        assertNotEquals(429, post("/api/v1/me/preferences/merge", "{}", "Authorization", b, "X-Forwarded-For", newIp()).statusCode(), "사용자 B는 따로");
    }

    // ---------- 401 잦은 IP: 분당 20회 실패 뒤 5분 막음 ----------

    @Test
    void ipWithManyAuthFailuresIsBlockedFor5Minutes() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 20; i++) {
            assertEquals(401, get("/api/v1/me", "Authorization", "Bearer bad" + i, "X-Forwarded-For", ip).statusCode(), "실패 " + i);
        }
        String good = TestAuth.newUser().bearer();
        assertLimited(get("/api/v1/me", "Authorization", good, "X-Forwarded-For", ip), "21번째는 맞는 토큰이어도 막힘", 300);
        assertLimited(get("/api/v1/health", "X-Forwarded-For", ip), "로그인 없이 부르는 주소도 막힘", 300);
        assertNotEquals(429, get("/api/v1/me", "Authorization", good, "X-Forwarded-For", newIp()).statusCode(), "다른 IP는 됨");
        clock.advance(Duration.ofMinutes(4));
        assertLimited(get("/api/v1/health", "X-Forwarded-For", ip), "4분 뒤 아직 막힘", 60);
        clock.advance(Duration.ofSeconds(61));
        assertEquals(200, get("/api/v1/me", "Authorization", good, "X-Forwarded-For", ip).statusCode(), "5분 지나면 풀림");
    }

    @Test
    void fewAuthFailuresDoNotBlock() throws Exception {
        String ip = newIp();
        for (int i = 0; i < 19; i++) get("/api/v1/me", "Authorization", "Bearer bad" + i, "X-Forwarded-For", ip);
        assertEquals(200, get("/api/v1/me", "Authorization", TestAuth.newUser().bearer(), "X-Forwarded-For", ip).statusCode());
    }
}
