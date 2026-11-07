package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 4: 카탈로그 key 목록이 없으면(묶음 6 전 실제 서버, 파일을 못 읽음) key를 확인해야 하는 저장은 503,
 * 읽기·동의는 그대로 된다. 아무 key나 받아 버리지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogKeysMissingTest extends ApiTestBase {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
        r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        r.add("scent.catalog.keys-file", () -> "");
    }

    @Test
    void writesNeedingKeysAre503() throws Exception {
        TestAuth.User u = MeApiTestBase.consentedUser();
        HttpResponse<String> r = send("PUT", "/api/v1/me/preferences", MeApiTestBase.prefs("[\"rose\"]", "[]"),
                "Authorization", u.bearer(), "X-Forwarded-For", "100.65.0.1");
        MeApiTestBase.assertError(r, 503, "UNAVAILABLE");
        assertEquals(200, get("/api/v1/me/preferences", "Authorization", u.bearer(), "X-Forwarded-For", "100.65.0.2").statusCode());
        assertEquals(200, send("PUT", "/api/v1/me/preferences", MeApiTestBase.prefs("[]", "[]"),
                "Authorization", u.bearer(), "X-Forwarded-For", "100.65.0.3").statusCode(), "확인할 key가 없는 저장(비우기)은 됨");
    }
}
