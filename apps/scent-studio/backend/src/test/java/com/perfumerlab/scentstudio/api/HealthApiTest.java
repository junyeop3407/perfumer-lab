package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 1: health(DB 됨), 그 밖 주소 401, 측정 주소는 꺼져 있으면 404, 응답에 비밀값 없음. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"scent.debug.mem=false", "scent.debug.ip=false", "scent.debug.token=", "scent.debug.until="})
class HealthApiTest extends ApiTestBase {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
    }

    @Test
    void healthIsOkWhenDbWorks() throws Exception {
        HttpResponse<String> r = get("/api/v1/health");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"db\":\"ok\""), r.body());
        assertNoSecrets(r.body());
    }

    @Test
    void liveIsAlwaysOk() throws Exception {
        assertEquals(200, get("/api/v1/health/live").statusCode());
    }

    @Test
    void everythingElseNeedsLogin() throws Exception {
        for (String path : new String[]{"/api/v1/blends", "/api/v1/me", "/api/v1/nope", "/", "/api/v1/openapi", "/error"}) {
            HttpResponse<String> r = get(path);
            assertEquals(401, r.statusCode(), path);
            assertTrue(r.body().contains("UNAUTHORIZED"), path + " " + r.body());
            assertNoSecrets(r.body());
        }
        assertEquals(401, post("/api/v1/health", "{}").statusCode(), "health는 GET만");
    }

    @Test
    void debugEndpointsAreHiddenByDefault() throws Exception {
        for (String path : new String[]{"/api/v1/_debug/mem", "/api/v1/_debug/ip"}) {
            assertEquals(404, get(path).statusCode(), path);
            assertEquals(404, get(path, "X-Debug-Token", "x".repeat(40)).statusCode(), path + " (토큰이 비어 있으면 어떤 값도 통과 못 함)");
        }
    }

    @Test
    void noSessionCookie() throws Exception {
        HttpResponse<String> r = get("/api/v1/health");
        assertTrue(r.headers().allValues("Set-Cookie").isEmpty(), "쿠키를 만들지 않는다");
    }

    static void assertNoSecrets(String body) {
        assertFalse(body.contains(TestDb.SERVER_PASSWORD), "DB 비밀번호가 응답에 나오면 안 된다");
        assertFalse(body.contains("jdbc:"), "접속 주소가 응답에 나오면 안 된다");
        assertFalse(body.toLowerCase().contains("exception"), "예외 내용이 응답에 나오면 안 된다");
    }
}
