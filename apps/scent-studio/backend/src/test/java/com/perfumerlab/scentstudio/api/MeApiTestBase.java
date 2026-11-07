package com.perfumerlab.scentstudio.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

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

    HttpResponse<String> deleteAs(TestAuth.User u, String path) throws Exception {
        return send("DELETE", path, null, "Authorization", u.bearer(), "X-Forwarded-For", ip());
    }

    /** 요청 n개를 한꺼번에 출발시킨다(모두 준비된 뒤 동시에). 결과는 보낸 순서대로 */
    static List<HttpResponse<String>> together(int n, IntFunction<Callable<HttpResponse<String>>> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch ready = new CountDownLatch(n), go = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<HttpResponse<String>> c = request.apply(i);
                fs.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return c.call();
                }));
            }
            ready.await();
            go.countDown();
            List<HttpResponse<String>> out = new ArrayList<>();
            for (Future<HttpResponse<String>> f : fs) out.add(f.get(60, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    static long countStatus(List<HttpResponse<String>> rs, int status) {
        return rs.stream().filter(r -> r.statusCode() == status).count();
    }

    /** 테스트 DB에서 숫자 하나 (주인 계정으로) */
    static long dbCount(String sql, Object... args) throws Exception {
        try (Connection c = DriverManager.getConnection(TestDb.jdbcUrl()); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet rs = p.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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
