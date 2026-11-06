package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** 측정 주소: 켜는 값 + 32자 이상 토큰 + 헤더가 같을 때만 응답 (plan_stage4.md 승인 때 추가 1). */
class DebugGateTest {

    static final String TOKEN = "DebugTokenForTestsOnly0123456789abcdefgh"; // 40자

    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.ip=true", "scent.debug.token=" + TOKEN})
    class Enabled extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void needsTheExactToken() throws Exception {
            for (String path : new String[]{"/api/v1/_debug/mem", "/api/v1/_debug/ip"}) {
                assertEquals(404, get(path).statusCode(), path + " 헤더 없음");
                assertEquals(404, get(path, "X-Debug-Token", "wrong").statusCode(), path + " 틀린 토큰");
                assertEquals(404, get(path, "X-Debug-Token", TOKEN.substring(0, 39)).statusCode(), path + " 한 글자 모자람");
                assertEquals(404, get(path, "X-Debug-Token", TOKEN + "x").statusCode(), path + " 한 글자 더");
                assertEquals(404, get(path, "x-debug-token", TOKEN.toUpperCase()).statusCode(), path + " 대소문자 다름");
                HttpResponse<String> ok = get(path, "X-Debug-Token", TOKEN);
                assertEquals(200, ok.statusCode(), path);
                assertFalse(ok.body().contains(TOKEN), "토큰을 돌려주면 안 된다");
                HealthApiTest.assertNoSecrets(ok.body());
            }
        }

        @Test
        void memReportsMeasurements() throws Exception {
            String body = get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).body();
            for (String k : new String[]{"jvmStartToReadyMs", "heapUsedMB", "heapMaxMB", "rssPeakMB", "cgroupPeakMB", "threads"}) {
                assertTrue(body.contains("\"" + k + "\""), k + " in " + body);
            }
        }

        @Test
        void ipShowsForwardedForShapeOnly() throws Exception {
            String body = get("/api/v1/_debug/ip", "X-Debug-Token", TOKEN, "X-Forwarded-For", "1.2.3.4, 5.6.7.8").body();
            assertTrue(body.contains("\"xffEntries\":2"), body);
            assertTrue(body.contains("\"xffFromRight\":[\"5.6.7.8\",\"1.2.3.4\"]"), body);
            assertFalse(body.contains(TOKEN));
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=true", "scent.debug.ip=true", "scent.debug.token=short-token"})
    class ShortTokenNeverOpens extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void shortTokenIsRejectedEvenIfHeaderMatches() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", "short-token").statusCode());
            assertEquals(404, get("/api/v1/_debug/ip", "X-Debug-Token", "short-token").statusCode());
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"scent.debug.mem=false", "scent.debug.ip=false", "scent.debug.token=" + TOKEN})
    class SwitchedOff extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) { db(r); }

        @Test
        void offMeansOffEvenWithToken() throws Exception {
            assertEquals(404, get("/api/v1/_debug/mem", "X-Debug-Token", TOKEN).statusCode());
            assertEquals(404, get("/api/v1/_debug/ip", "X-Debug-Token", TOKEN).statusCode());
        }
    }
}
