package com.perfumerlab.scentstudio.api;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.perfumerlab.scentstudio.limit.ClientIp;
import com.perfumerlab.scentstudio.limit.RateLimiter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 단계 4 묶음 3 리뷰에서 나온 것 (docs/review.md "묶음 3"). */
class Bundle3ReviewFixesTest {

    // Gemini-1: X-Forwarded-For 값으로 DNS 풀이가 일어나면 안 된다(16개뿐인 스레드를 DNS 대기로 묶는 공격).
    // JDK는 첫 글자가 16진수나 ':'일 때만 IP 글자로만 읽고, 아니면 이름 풀이로 넘긴다.
    @ParameterizedTest(name = "DNS 안 부름: {0}")
    @ValueSource(strings = {".cafe:80", ".dead.beef:1", "..:1", ".a:b", ".:"})
    void forwardedValueNeverTriggersDnsLookup(String v) {
        RecordingDnsResolver.LOOKUPS.clear();
        assertEquals("10.0.0.9", ClientIp.resolve(List.of(v), "10.0.0.9", 1));
        assertEquals(v, ClientIp.limitKey(v));
        assertFalse(RecordingDnsResolver.LOOKUPS.contains(v), "DNS 풀이가 일어남: " + RecordingDnsResolver.LOOKUPS);
    }

    // QA-2: 로그인 없는 요청이 IP를 바꿔 가며 몰려와도 다른 사용자의 바구니·막은 IP가 밀려나지 않는다
    @Test
    void anonymousFloodDoesNotEvictUserBucketsOrBlocks() {
        RateLimiter r = new RateLimiter(Clock.systemUTC());
        for (int i = 0; i < 5; i++) assertTrue(r.tryConsume(RateLimiter.Limit.MERGE, "user:a").allowed());
        assertFalse(r.tryConsume(RateLimiter.Limit.MERGE, "user:a").allowed());
        for (int i = 0; i < 20; i++) r.recordAuthFailure("203.0.113.1");
        assertTrue(r.blockedSeconds("203.0.113.1") > 0);
        for (int i = 0; i < 60_000; i++) r.tryConsume(RateLimiter.Limit.ANON_IP, "2001:db8:" + Integer.toHexString(i) + "::/64");
        assertFalse(r.tryConsume(RateLimiter.Limit.MERGE, "user:a").allowed(), "사용자 바구니가 밀려나 한도가 풀리면 안 된다");
        assertTrue(r.blockedSeconds("203.0.113.1") > 0, "막은 IP가 밀려나 풀리면 안 된다");
    }

    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(TestClock.Config.class)
    class Running extends ApiTestBase {

        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) {
            db(r);
            r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        }

        String ip() {
            return "192.0.2." + (int) (1 + Math.random() * 250);
        }

        // QA-1(높음): 토큰을 아예 안 보낸 401은 세지 않는다 (다른 사이트가 방문자 브라우저로 보호 주소를 불러 그 IP를 막게 만들 수 있음)
        @Test
        void requestsWithoutTokenDoNotBlockTheIp() throws Exception {
            String ip = ip();
            for (int i = 0; i < 25; i++) assertEquals(401, get("/api/v1/me", "X-Forwarded-For", ip).statusCode());
            for (int i = 0; i < 25; i++) assertNotEquals(429, send("OPTIONS", "/api/v1/me", null, "X-Forwarded-For", ip).statusCode());
            assertEquals(404, get("/api/v1/me", "Authorization", TestAuth.newUser().bearer(), "X-Forwarded-For", ip).statusCode(),
                    "같은 IP의 로그인한 사용자는 막히지 않는다");
        }

        // QA-4: 모르는 kid 토큰을 몰아 보내도 키 목록을 매번 받으러 가지 않는다
        @Test
        void unknownKidDoesNotHammerJwks() throws Exception {
            assertEquals(404, get("/api/v1/me", "Authorization", TestAuth.newUser().bearer(), "X-Forwarded-For", ip()).statusCode());
            int before = TestAuth.JWKS_HITS.get();
            TestAuth.User u = TestAuth.newUser();
            for (int i = 0; i < 10; i++) {
                ECKey k = TestAuth.newKey("unknown-" + UUID.randomUUID());
                assertEquals(401, get("/api/v1/me", "Authorization", "Bearer " + TestAuth.sign(k, u, c -> { }), "X-Forwarded-For", ip()).statusCode());
            }
            assertTrue(TestAuth.JWKS_HITS.get() - before <= 1, "키 목록 요청 " + (TestAuth.JWKS_HITS.get() - before) + "번");
        }

        // QA-5: 빠졌던 경우들
        @Test
        void missingIsAnonymousClaimIsRejected() throws Exception {
            TestAuth.User u = TestAuth.newUser();
            assertEquals(401, get("/api/v1/me", "Authorization", "Bearer " + TestAuth.token(u, c -> c.claim("is_anonymous", null)), "X-Forwarded-For", ip()).statusCode());
        }

        @Test
        void rs256TokenIsRejected() throws Exception {
            TestAuth.User u = TestAuth.newUser();
            RSAKey rsa = new RSAKeyGenerator(2048).keyID(TestAuth.KID).generate();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TestAuth.KID).type(JOSEObjectType.JWT).build(), TestAuth.claims(u).build());
            jwt.sign(new RSASSASigner(rsa));
            assertEquals(401, get("/api/v1/me", "Authorization", "Bearer " + jwt.serialize(), "X-Forwarded-For", ip()).statusCode());
        }

        @Test
        void oddAuthorizationHeaders() throws Exception {
            TestAuth.User u = TestAuth.newUser();
            assertEquals(404, get("/api/v1/me", "Authorization", "bearer " + u.token(), "X-Forwarded-For", ip()).statusCode(), "소문자 bearer도 표준");
            HttpResponse<String> two = get("/api/v1/me", "Authorization", u.bearer(), "Authorization", "Bearer x.y.z", "X-Forwarded-For", ip());
            assertTrue(two.statusCode() < 500, "헤더 두 개: " + two.statusCode());
            HttpResponse<String> big = get("/api/v1/me", "Authorization", "Bearer " + "a".repeat(20_000), "X-Forwarded-For", ip());
            assertTrue(big.statusCode() >= 400 && big.statusCode() < 500, "아주 긴 토큰: " + big.statusCode());
        }

        @Test
        void blockedIpStillPassesRenderHealthCheck() throws Exception {
            String ip = "192.0.2.255"; // ip()가 쓰지 않는 값
            for (int i = 0; i < 20; i++) get("/api/v1/me", "Authorization", "Bearer bad" + i, "X-Forwarded-For", ip);
            assertEquals(429, get("/api/v1/health", "X-Forwarded-For", ip).statusCode());
            assertEquals(200, get("/api/v1/health/live", "X-Forwarded-For", ip).statusCode());
        }
    }

    // QA-5(1): 키 목록을 못 읽으면 503, 실패로 세지 않음
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class JwksDown extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) {
            db(r);
            r.add("scent.auth.jwks-uri", () -> "http://127.0.0.1:1/auth/v1/.well-known/jwks.json");
        }

        @Test
        void serviceUnavailableAndNotCounted() throws Exception {
            String bearer = TestAuth.newUser().bearer();
            for (int i = 0; i < 25; i++) {
                HttpResponse<String> r = get("/api/v1/me", "Authorization", bearer, "X-Forwarded-For", "192.0.2.10");
                assertEquals(503, r.statusCode(), i + " " + r.body());
                assertTrue(r.body().contains("\"code\":\"UNAVAILABLE\""), r.body());
            }
        }
    }

    // QA-5(1): 세션 확인 DB가 안 되면 503, 실패로 세지 않음
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "spring.datasource.hikari.connection-timeout=300"})
    class SessionDbDown extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) {
            db(r);
            r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:1/nowhere");
            r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        }

        @Test
        void serviceUnavailableAndNotCounted() throws Exception {
            String bearer = TestAuth.newUser().bearer();
            for (int i = 0; i < 22; i++) {
                HttpResponse<String> r = get("/api/v1/me", "Authorization", bearer, "X-Forwarded-For", "192.0.2.11");
                assertEquals(503, r.statusCode(), i + " " + r.body());
            }
        }
    }

    // QA(낮음): 키 목록 주소를 바꾸는 설정은 이 컴퓨터 안(테스트)만 허용. 운영에서 다른 서버 키를 믿게 되는 실수를 막는다
    @Test
    void jwksOverrideMustBeLocal() {
        assertThrows(IllegalStateException.class, () -> new com.perfumerlab.scentstudio.auth.SupabaseJwt()
                .jwtDecoder("https://testref.supabase.co", "https://evil.example/jwks.json", Clock.systemUTC()));
        assertDoesNotThrow(() -> new com.perfumerlab.scentstudio.auth.SupabaseJwt()
                .jwtDecoder("https://testref.supabase.co", "http://127.0.0.1:9/jwks.json", Clock.systemUTC()));
    }
}
