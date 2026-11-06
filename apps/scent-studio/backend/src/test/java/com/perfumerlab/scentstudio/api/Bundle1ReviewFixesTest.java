package com.perfumerlab.scentstudio.api;

import com.perfumerlab.scentstudio.web.HealthController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 묶음 1 리뷰(QA팀 + Gemini, 2026-11-06) 회귀 테스트. 판단은 docs/review.md "묶음 1". */
class Bundle1ReviewFixesTest {

    static final String TOKEN = "Bundle1ReviewTokenAbc123Def456Ghi789Jkl0"; // 40자, 영문·숫자 섞임
    static final String FUTURE = Instant.now().plus(Duration.ofHours(2)).toString();

    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
    }

    // QA-1: 로그인 없이 부르는 health가 매번 DB를 쓰면 몰아 부르기로 연결 4개를 묶을 수 있다 → 결과를 잠깐 보관
    @Test
    void healthReusesRecentResultInsteadOfHittingDbEveryTime() {
        JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
        AtomicInteger calls = new AtomicInteger();
        when(jdbc.sql(anyString()).query(Integer.class).single()).thenAnswer(i -> { calls.incrementAndGet(); return 1; });
        HealthController h = new HealthController(jdbc);
        for (int i = 0; i < 200; i++) assertEquals(200, h.health().getStatusCode().value());
        assertEquals(1, calls.get(), "보관 시간 안에서는 DB를 한 번만 부른다");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=false", "scent.debug.ip=false", "scent.debug.token="})
    class ErrorsKeepTheirRealStatus extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        // QA-2: 공개 주소에서 난 오류(406 등)가 오류 처리 단계에서 401로 바뀌면 안 된다
        @Test
        void notAcceptableStaysNotAcceptable() throws Exception {
            HttpResponse<String> r = get("/api/v1/health/live", "Accept", "application/xml");
            assertEquals(406, r.statusCode(), r.body());
            HealthApiTest.assertNoSecrets(r.body());
        }
    }

    // QA-7: 측정 토큰은 영문·숫자 32자 이상 + 여러 글자가 섞여야 하고, DEBUG_UNTIL(종료 시각)이 지나면 저절로 꺼진다
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "scent.debug.until=2099-01-01T00:00:00Z"})
    class WeakTokenNeverOpens extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void repeatedCharacterTokenIsRejected() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", "a".repeat(40)).statusCode());
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.token=" + TOKEN, "scent.debug.until=2020-01-01T00:00:00Z"})
    class ExpiredSwitchCloses extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void pastUntilMeansOff() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).statusCode());
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.token=" + TOKEN})
    class MissingUntilMeansOff extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void noUntilNoAccess() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).statusCode());
        }
    }

    // QA-12: 켜져 있어도 토큰이 비어 있으면 빈 헤더로 열리지 않는다
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.ip=true", "scent.debug.token=", "scent.debug.until=2099-01-01T00:00:00Z"})
    class EmptyTokenNeverOpens extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void emptyHeaderDoesNotMatchEmptyToken() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", "").statusCode());
            assertEquals(404, get("/api/v1/_debug/ip").statusCode());
        }
    }

    // QA-13 + Gemini-3: 경로 변형·같은 헤더 두 개·다른 메서드·공백 토큰·아주 긴 토큰으로 열리지 않는다(지금 막히는 것을 고정)
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.ip=true", "scent.debug.token=" + TOKEN, "scent.debug.until=2099-01-01T00:00:00Z"})
    class VariantsDoNotOpen extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void pathVariants() throws Exception {
            assertEquals(200, get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).statusCode(), "정상 경로는 열림(대조군)");
            for (String p : new String[]{"/api/v1/_debug/mem/", "/api/v1/_debug/MEM", "/api/v1/_debug/mem;x=1",
                    "/api/v1/_debug%2Fmem", "/api/v1//_debug/mem", "/api/v1/_debug/./mem", "/API/v1/_debug/mem"}) {
                int code = get(p, "X-Debug-Token", TOKEN).statusCode();
                assertTrue(code == 400 || code == 401 || code == 404, p + " → " + code);
            }
        }

        @Test
        void headerTricks() throws Exception {
            // 헤더 값 앞뒤 공백은 HTTP 규칙상 서버가 잘라 낸다(토큰 자체는 여전히 필요) → 검사하지 않음
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", "wrong", "X-Debug-Token", TOKEN).statusCode(), "첫 값이 틀리면 닫힘");
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", "A".repeat(4000)).statusCode(), "아주 긴 값");
        }

        @Test
        void otherMethods() throws Exception {
            HttpClient http = HttpClient.newHttpClient();
            for (String m : new String[]{"POST", "PUT", "DELETE", "HEAD"}) {
                HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/_debug/mem"))
                        .header("X-Debug-Token", TOKEN).method(m, HttpRequest.BodyPublishers.noBody()).build();
                int code = http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
                assertNotEquals(200, code, m);
            }
        }

        @Test
        void memShowsCdsAndPools() throws Exception {
            String body = get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).body();
            assertTrue(body.contains("\"vmInfo\""), body);
            assertTrue(body.contains("\"metaspaceUsedMB\""), body);
            assertTrue(body.contains("\"codeCacheUsedMB\""), body);
            assertTrue(body.contains("\"debugUntil\""), body);
        }
    }

    // QA-14: 접속 주소에 비밀번호가 섞여 들어온 경우에도 응답·로그(시작 로그 포함)에 나오지 않는다
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/nowhere?user=scent_server&password=" + PasswordInUrl.URL_PW,
            "spring.datasource.hikari.connection-timeout=500"})
    @ExtendWith(OutputCaptureExtension.class)
    class PasswordInUrl extends ApiTestBase {
        static final String URL_PW = "urlEmbeddedPasswordMustNotLeak987654";

        @Test
        void neverPrinted(CapturedOutput output) throws Exception {
            HttpResponse<String> r = get("/api/v1/health");
            assertEquals(503, r.statusCode());
            assertFalse(r.body().contains(URL_PW));
            assertTrue(output.getAll().contains("Started"), "시작 로그까지 잡고 있어야 검사가 의미 있다");
            assertFalse(output.getAll().contains(URL_PW), "비밀번호가 로그에 나오면 안 된다");
        }
    }
}
