package com.perfumerlab.scentstudio.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 묶음 4 서버 테스트 공통: 로그인 토큰 + 테스트 카탈로그 key 목록(catalog-test.keys.json: 노트 307개, 계열 10개)
 * + 동의 문서 지금 버전(application.yml 기본값 draft-1). 요청마다 다른 IP를 줘서 IP 제한과 섞이지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.Config.class)
abstract class MeApiTestBase extends ApiTestBase {

    static final String VERSION = "draft-1";
    private static final AtomicInteger IP = new AtomicInteger(1);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
        r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        r.add("scent.catalog.keys-file", () -> "classpath:catalog-test.keys.json");
    }

    static String ip() {
        int n = IP.getAndIncrement();
        return "100.64." + (n / 250) + "." + (1 + n % 250);
    }

    HttpResponse<String> getAs(TestAuth.User u, String path) throws Exception {
        return get(path, "Authorization", u.bearer(), "X-Forwarded-For", ip());
    }

    HttpResponse<String> postAs(TestAuth.User u, String path, String json) throws Exception {
        return post(path, json, "Authorization", u.bearer(), "X-Forwarded-For", ip());
    }

    HttpResponse<String> putAs(TestAuth.User u, String path, String json) throws Exception {
        return send("PUT", path, json, "Authorization", u.bearer(), "X-Forwarded-For", ip());
    }

    /** 지금 버전 세 문서에 동의한 사용자 (DB에 바로 넣음) */
    static TestAuth.User consentedUser() {
        TestAuth.User u = TestAuth.newUser();
        consent(u.id(), "terms", "privacy", "age_14");
        return u;
    }

    static void consent(UUID user, String... types) {
        for (String t : types) {
            TestAuth.admin("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, ?, ?, '1.0.0')", user, t, VERSION);
        }
    }

    static String prefs(String disliked, String preferred) {
        return "{\"dislikedNoteKeys\":" + disliked + ",\"preferredFamilies\":" + preferred + "}";
    }

    /** ["note-001", ..., "note-n"] */
    static String notes(int from, int to) {
        StringBuilder s = new StringBuilder("[");
        for (int i = from; i <= to; i++) s.append(i > from ? "," : "").append(String.format("\"note-%03d\"", i));
        return s.append("]").toString();
    }

    static void assertError(HttpResponse<String> r, int status, String code) {
        org.junit.jupiter.api.Assertions.assertEquals(status, r.statusCode(), r.body());
        org.junit.jupiter.api.Assertions.assertTrue(r.body().startsWith("{\"error\":{\"code\":\"" + code + "\""), r.body());
    }
}
