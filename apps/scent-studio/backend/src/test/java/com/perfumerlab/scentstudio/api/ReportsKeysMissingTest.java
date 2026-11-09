package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 7: 카탈로그 key 목록을 못 받으면 오류 신고도 503(아무 key나 받아 두지 않음). CatalogKeysMissingTest와 같은 설정.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportsKeysMissingTest extends ApiTestBase {

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
    void reportIs503WhenKeysAreMissing() throws Exception {
        TestAuth.User u = MeApiTestBase.consentedUser();
        MeApiTestBase.assertError(post("/api/v1/reports", ReportsApiTest.MIN, "Authorization", u.bearer(), "X-Forwarded-For", "100.65.1.1"),
                503, "UNAVAILABLE");
        assertEquals(0, MeApiTestBase.dbCount("select count(*) from app.error_reports where user_id = ?", u.id()));
    }
}
