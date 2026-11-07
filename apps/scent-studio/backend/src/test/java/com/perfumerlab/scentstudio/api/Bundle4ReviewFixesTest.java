package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

/** 단계 4 묶음 4 리뷰에서 나온 것 (docs/review.md "묶음 4"). */
class Bundle4ReviewFixesTest {

    @Nested
    class Running extends MeApiTestBase {

        // QA-2: 길이를 적지 않고(chunked) 보낸 큰 본문도 읽다가 멈추고 413
        @Test
        void chunkedTooLargeBodyIs413() throws Exception {
            TestAuth.User u = consentedUser();
            byte[] big = ("{\"dislikedNoteKeys\":[],\"preferredFamilies\":[],\"pad\":\"" + "a".repeat(300_000) + "\"}").getBytes(StandardCharsets.UTF_8);
            Flow.Publisher<ByteBuffer> noLength = HttpRequest.BodyPublishers.ofByteArray(big)::subscribe; // 길이를 모르는 본문 → chunked
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/me/preferences")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("Authorization", u.bearer()).header("X-Forwarded-For", ip())
                    .PUT(HttpRequest.BodyPublishers.fromPublisher(noLength)).build();
            HttpResponse<String> r = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            assertError(r, 413, "PAYLOAD_TOO_LARGE");
        }

        // QA-3: key 글자 모양 (DB valid_key와 같아야 500이 나지 않음)
        @ParameterizedTest(name = "잘못된 key 모양: [{0}]")
        @ValueSource(strings = {"", " rose", "rose ", "a\\tb", "a\\nb", "\\u0000", "장미", "rosé"})
        void badKeyShapesAre400(String jsonEscaped) throws Exception {
            TestAuth.User u = consentedUser();
            String body = prefs("[\"" + jsonEscaped + "\"]", "[]");
            assertError(putAs(u, "/api/v1/me/preferences", body), 400, "BAD_REQUEST");
            assertError(postAs(u, "/api/v1/me/preferences/merge", body), 400, "BAD_REQUEST");
        }

        @Test
        void innerSpaceIsAValidShape() throws Exception {
            TestAuth.User u = consentedUser();
            assertError(putAs(u, "/api/v1/me/preferences", prefs("[\"a b\"]", "[]")), 400, "UNKNOWN_KEY"); // 모양은 통과, 목록에 없을 뿐
        }

        // QA-5: Spring 표준 오류(406 등)는 원래 상태 번호 그대로
        @Test
        void notAcceptableIs406() throws Exception {
            TestAuth.User u = consentedUser();
            HttpResponse<String> r = get("/api/v1/me/preferences", "Authorization", u.bearer(), "X-Forwarded-For", ip(), "Accept", "text/plain");
            assertEquals(406, r.statusCode(), r.body());
        }

        // QA-6: 합쳐도 바뀐 게 없으면 저장하지 않는다(updated_at 그대로)
        @Test
        void mergeWithoutChangeDoesNotWrite() throws Exception {
            TestAuth.User u = consentedUser();
            putAs(u, "/api/v1/me/preferences", prefs("[\"rose\"]", "[\"woody\"]"));
            // updated_at은 바꿀 때마다 DB 트리거가 지금으로 덮으므로, PUT 직후 값을 기억해 두고 비교한다
            String before = TestDbQuery.text("select updated_at::text from app.user_preferences where user_id = ?", u.id());
            assertEquals(200, postAs(u, "/api/v1/me/preferences/merge", prefs("[\"rose\"]", "[\"floral\"]")).statusCode());
            assertEquals(before, TestDbQuery.text("select updated_at::text from app.user_preferences where user_id = ?", u.id()),
                    "바뀐 게 없으면 저장하지 않음(updated_at 그대로)");
            assertEquals(200, postAs(u, "/api/v1/me/preferences/merge", prefs("[\"musk\"]", "[]")).statusCode());
            assertNotEquals(before, TestDbQuery.text("select updated_at::text from app.user_preferences where user_id = ?", u.id()), "바뀌면 저장");
        }

        // QA-7: 같은 이름 칸 두 번, 아주 깊은 중첩은 400
        @Test
        void duplicateJsonFieldsAre400() throws Exception {
            String body = "{\"dislikedNoteKeys\":[\"rose\"],\"dislikedNoteKeys\":[\"musk\"],\"preferredFamilies\":[]}";
            assertError(putAs(consentedUser(), "/api/v1/me/preferences", body), 400, "BAD_REQUEST");
        }

        @Test
        void deeplyNestedJsonIs400() throws Exception {
            String body = "{\"dislikedNoteKeys\":[],\"preferredFamilies\":[],\"x\":" + "[".repeat(2000) + "]".repeat(2000) + "}";
            assertError(putAs(consentedUser(), "/api/v1/me/preferences", body), 400, "BAD_REQUEST");
        }
    }

    // QA-1: 카탈로그 key 파일이 읽혀도 내용이 비었거나 모양이 틀리면 "목록 없음"(503). 0개짜리 목록으로 기기 값을 버리지 않는다
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class EmptyCatalogFile extends ApiTestBase {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) {
            r.add("spring.datasource.url", TestDb::jdbcUrl);
            r.add("spring.datasource.username", () -> "scent_server");
            r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
            r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
            r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
            r.add("scent.catalog.keys-file", () -> "classpath:catalog-empty.keys.json");
        }

        @Test
        void mergeIs503NotSilentDrop() throws Exception {
            TestAuth.User u = MeApiTestBase.consentedUser();
            HttpResponse<String> r = post("/api/v1/me/preferences/merge", MeApiTestBase.prefs("[\"rose\"]", "[]"),
                    "Authorization", u.bearer(), "X-Forwarded-For", "100.66.0.1");
            MeApiTestBase.assertError(r, 503, "UNAVAILABLE");
        }
    }

    /** 테스트 DB에 줄이 있나 (주인 계정으로) */
    static final class TestDbQuery {
        static String text(String sql, Object... args) throws Exception {
            try (var c = java.sql.DriverManager.getConnection(TestDb.jdbcUrl()); var p = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
                try (var rs = p.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }

        static boolean exists(String sql, Object... args) throws Exception {
            try (var c = java.sql.DriverManager.getConnection(TestDb.jdbcUrl()); var p = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
                try (var rs = p.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }
}
