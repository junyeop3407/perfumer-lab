package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 묶음 6: 카탈로그 API (설명은 CatalogTestBase) */
class CatalogApiTest extends CatalogTestBase {

    // ---------- 지금 버전 ----------

    @Test
    void latestShowsCurrentReleaseWithoutAnyAddress() throws Exception {
        release(OLD, FILE, KEYS, false);
        release(V, FILE, KEYS, true);
        HttpResponse<String> r = get("/api/v1/catalog/latest", "X-Forwarded-For", ip());
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("{\"version\":\"" + V + "\",\"engineVersion\":8,\"sha256\":\"" + FakeStorage.sha256(FILE) + "\",\"sizeBytes\":" + FILE.length
                + ",\"minAppVersion\":\"1.0.0\"}", r.body());
        assertFalse(r.body().contains("http") || r.body().contains("storage") || r.body().contains(".gz"), "주소·파일 이름을 알려 주지 않음");
    }

    @Test
    void latestWithoutReleaseIs404() throws Exception {
        HttpResponse<String> r = get("/api/v1/catalog/latest", "X-Forwarded-For", ip());
        assertEquals(404, r.statusCode(), r.body());
    }

    // ---------- 내려받기 ----------

    @Test
    void downloadGoesThroughServerWithServerKey() throws Exception {
        release(V, FILE, KEYS, true);
        HttpResponse<byte[]> r = download(V, UUID.randomUUID(), ip());
        assertEquals(200, r.statusCode(), text(r));
        assertArrayEquals(FILE, r.body());
        assertEquals("application/gzip", r.headers().firstValue("Content-Type").orElse(""));
        assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("private"));
        FakeStorage.Seen s = FakeStorage.seen.stream().filter(x -> x.path().endsWith(".json.gz") && !x.path().endsWith(".keys.json.gz"))
                .findFirst().orElseThrow();
        assertEquals("/storage/v1/object/catalog/catalog-" + V + ".json.gz", s.path(), "공개 주소(/object/public/)가 아니라 인증 주소");
        assertEquals(FakeStorage.SECRET, s.apikey());
        assertEquals("Bearer " + FakeStorage.SECRET, s.authorization());
    }

    @Test
    void oldPublishedVersionCanBeDownloaded() throws Exception {
        release(OLD, FILE, KEYS, false);
        release(V, FILE, KEYS, true);
        assertEquals(200, download(OLD, UUID.randomUUID(), ip()).statusCode());
    }

    @Test
    void secondDownloadComesFromMemory() throws Exception {
        byte[] other = FakeStorage.gzip("{\"version\":\"2026.11.06\"}");
        release("2026.11.06", other, KEYS, true);
        assertEquals(200, download("2026.11.06", UUID.randomUUID(), ip()).statusCode());
        assertEquals(200, download("2026.11.06", UUID.randomUUID(), ip()).statusCode());
        assertEquals(1, FakeStorage.seen.stream().filter(x -> x.path().endsWith("catalog-2026.11.06.json.gz")).count(), "Storage는 한 번만");
    }

    @Test
    void installIdIsRequired() throws Exception {
        release(V, FILE, KEYS, true);
        assertError(download(V, null, ip()), 400, "BAD_REQUEST");
        HttpResponse<String> r = get("/api/v1/catalog/" + V, "X-Install-Id", "not-a-uuid", "X-Forwarded-For", ip());
        assertEquals(400, r.statusCode());
        assertTrue(FakeStorage.seen.isEmpty(), "Storage를 부르지 않음");
    }

    @Test
    void unknownVersionIs404() throws Exception {
        release(V, FILE, KEYS, true);
        assertError(download("2026.12.01", UUID.randomUUID(), ip()), 404, "NOT_FOUND");
        assertError(download("latest.json", UUID.randomUUID(), ip()), 404, "NOT_FOUND");
        assertEquals(400, download("..", UUID.randomUUID(), ip()).statusCode(), "경로 오르기는 보안 필터가 먼저 막음");
        assertTrue(FakeStorage.seen.isEmpty(), "DB에 없는 버전은 Storage를 부르지 않음");
    }

    // ---------- 해시가 다르면 내주지 않음 ----------

    @Test
    void wrongHashIsNotServedAndNotKept() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        byte[] tampered = FakeStorage.gzip("{\"version\":\"" + v + "\",\"perfumes\":[\"x\"]}");
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, tampered);
        HttpResponse<byte[]> r = download(v, UUID.randomUUID(), ip());
        assertError(r, 503, "UNAVAILABLE");
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, FILE); // 고쳐 올리면
        clock.advance(Duration.ofSeconds(31)); // 실패는 30초 기억(리뷰 묶음 6 QA-2)
        HttpResponse<byte[]> again = download(v, UUID.randomUUID(), ip());
        assertEquals(200, again.statusCode(), "틀린 파일을 보관하지 않았으니 다시 받아 옴");
        assertArrayEquals(FILE, again.body());
    }

    @Test
    void sameSizeDifferentBytesIsNotServed() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        byte[] flipped = FILE.clone();
        flipped[flipped.length / 2] ^= 1;
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, flipped);
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
    }

    @Test
    void storageFailureIs503() throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 500, "boom".getBytes(StandardCharsets.UTF_8));
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 0, new byte[0]); // 연결 끊김
        assertError(download(v, UUID.randomUUID(), ip()), 503, "UNAVAILABLE");
    }

    // ---------- 제한: install_id 하루 3회 + IP 시간당 10회 ----------

    @Test
    void installIdThreePerDay() throws Exception {
        release(V, FILE, KEYS, true);
        UUID install = UUID.randomUUID();
        for (int i = 0; i < 3; i++) assertEquals(200, download(V, install, ip()).statusCode(), "IP를 바꿔도 같은 설치 id " + (i + 1));
        HttpResponse<byte[]> r = download(V, install, ip());
        assertError(r, 429, "RATE_LIMITED");
        assertTrue(Long.parseLong(r.headers().firstValue("Retry-After").orElseThrow()) > 3600, "하루 단위로 기다림");
        clock.advance(Duration.ofDays(1).plusSeconds(1));
        assertEquals(200, download(V, install, ip()).statusCode(), "다음 날 다시 됨");
    }

    @Test
    void ipTenPerHour() throws Exception {
        release(V, FILE, KEYS, true);
        String sameIp = ip();
        for (int i = 0; i < 10; i++) assertEquals(200, download(V, UUID.randomUUID(), sameIp).statusCode(), "설치 id를 바꿔도 같은 IP " + (i + 1));
        HttpResponse<byte[]> r = download(V, UUID.randomUUID(), sameIp);
        assertError(r, 429, "RATE_LIMITED");
        assertTrue(Long.parseLong(r.headers().firstValue("Retry-After").orElseThrow()) >= 1);
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertEquals(200, download(V, UUID.randomUUID(), sameIp).statusCode(), "한 시간 뒤 다시 됨");
    }

    @Test
    void limitsCountEvenForUnknownVersions() throws Exception {
        String sameIp = ip();
        for (int i = 0; i < 10; i++) download("2099.01.0" + (i % 9 + 1), UUID.randomUUID(), sameIp);
        assertError(download(V, UUID.randomUUID(), sameIp), 429, "RATE_LIMITED"); // 버전 찍어 보기도 한도에 셈
    }

    // ---------- 서버 전용 키가 새지 않음 ----------

    @Test
    void secretKeyNeverInResponsesOrLogs(CapturedOutput out) throws Exception {
        String v = uniq();
        release(v, FILE, KEYS, true);
        String echo = "{\"statusCode\":\"403\",\"error\":\"Unauthorized\",\"message\":\"invalid key " + FakeStorage.SECRET + "\"}";
        int[] statuses = {401, 403, 500, 0};
        for (int s : statuses) {
            FakeStorage.put("catalog/catalog-" + v + ".json.gz", s, echo.getBytes(StandardCharsets.UTF_8));
            HttpResponse<byte[]> r = download(v, UUID.randomUUID(), ip());
            assertEquals(503, r.statusCode());
            assertNoSecret(text(r) + r.headers().map());
        }
        FakeStorage.put("catalog/catalog-" + v + ".json.gz", 200, echo.getBytes(StandardCharsets.UTF_8)); // 해시 다름 + 본문에 키
        assertNoSecret(text(download(v, UUID.randomUUID(), ip())));
        assertNoSecret(get("/api/v1/catalog/latest", "X-Forwarded-For", ip()).body());
        assertNoSecret(get("/api/v1/health", "X-Forwarded-For", ip()).body());
        assertNoSecret(out.getAll());
    }

    static void assertNoSecret(String s) {
        assertFalse(s.contains(FakeStorage.SECRET), "서버 키가 나옴");
        assertFalse(s.contains("TESTONLY"), "서버 키 일부가 나옴");
    }

    // ---------- key 목록도 Storage에서 (해시 확인) ----------

    @Test
    void keyListComesFromStorageAndIsVerified(CapturedOutput out) throws Exception {
        clock.advance(Duration.ofMinutes(11)); // 앞 테스트들이 받아 둔 목록을 다시 확인할 때가 되게
        TestAuth.User u = TestAuth.newUser();
        for (String t : new String[]{"terms", "privacy", "age_14"}) {
            TestAuth.admin("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, ?, 'draft-1', '1.0.0')", u.id(), t);
        }
        String bearer = "Bearer " + TestAuth.token(u, c -> c.issueTime(java.util.Date.from(clock.instant()))
                .expirationTime(java.util.Date.from(clock.instant().plus(Duration.ofHours(1)))));
        String v = uniq();
        String keysJson = KEYS_JSON.replace(V, v).replace("\"perfumes\":[", "\"perfumes\":[\"Fake_Maison:789-only-new\",");
        release(v, FILE, FakeStorage.gzip(keysJson), true);
        FakeStorage.put("catalog/catalog-" + v + ".keys.json.gz", 200, FakeStorage.gzip(keysJson.replace("rose", "rosa"))); // 해시 다름
        String fav = "/api/v1/favorites/Fake_Maison:789-only-new";
        HttpResponse<String> r = send("PUT", fav, null, "Authorization", bearer, "X-Forwarded-For", ip());
        // 해시가 다른 목록은 쓰지 않는다: 받아 둔 목록이 없으면 503, 있으면 그 옛 목록으로 400(새 목록에만 있는 향수)
        assertTrue(r.statusCode() == 503 || r.statusCode() == 400, "해시가 다른 key 목록은 쓰지 않음: " + r.statusCode() + " " + r.body());

        FakeStorage.put("catalog/catalog-" + v + ".keys.json.gz", 200, FakeStorage.gzip(keysJson));
        clock.advance(Duration.ofSeconds(31)); // 실패 뒤 30초 지나면 다시 받아 봄
        assertEquals(204, send("PUT", fav, null, "Authorization", bearer, "X-Forwarded-For", ip()).statusCode());
        HttpResponse<String> unknown = send("PUT", "/api/v1/favorites/Fake_Maison:999-none", null, "Authorization", bearer, "X-Forwarded-For", ip());
        assertEquals(400, unknown.statusCode(), unknown.body());
        assertNoSecret(out.getAll());
    }
}
