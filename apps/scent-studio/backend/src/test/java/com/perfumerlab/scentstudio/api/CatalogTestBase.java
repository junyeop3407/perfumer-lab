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

/**
 * 묶음 6 카탈로그 테스트 공통(서버 설정·가짜 Storage·배포 줄 넣기). 테스트는 CatalogApiTest, 회귀는 Bundle6ReviewFixesTest.
 * 묶음 6: 카탈로그 (server_design.md 5절). 파일은 비공개 Storage 버킷 catalog에만 있고, 앱은 서버를 거쳐서만 받는다.
 * - GET /catalog/latest: 지금 버전 정보만(주소 없음)
 * - GET /catalog/{version}: X-Install-Id 필요(400). install_id 하루 3회 + IP 시간당 10회(429 + Retry-After).
 *   Storage에서 서버 전용 키로 받아 catalog_releases의 sha256·크기와 맞을 때만 준다(다르면 503, 보관 안 함). 맞으면 메모리에 보관.
 * - 서버 전용 Secret key는 응답·로그·오류 어디에도 나오지 않는다.
 * - key 목록(검사용)도 key 목록 파일 설정이 없으면 Storage에서 받아 keys_sha256을 확인한 뒤 쓴다.
 * 실제 Storage에는 아무것도 만들거나 올리지 않는다(FakeStorage).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.Config.class)
@ExtendWith(OutputCaptureExtension.class)
abstract class CatalogTestBase extends ApiTestBase {

    static final String V = "2026.11.05", OLD = "2026.11.01";
    static final byte[] FILE = FakeStorage.gzip("{\"version\":\"" + V + "\",\"perfumes\":[]}");
    static final String KEYS_JSON = "{\"version\":\"" + V + "\",\"notes\":[\"rose\",\"musk\"],\"families\":[\"floral\"],\"perfumes\":[\"Fake_Maison:123-sample-scent\"]}";
    static final byte[] KEYS = FakeStorage.gzip(KEYS_JSON);
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    TestClock clock;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
        r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        r.add("scent.catalog.keys-file", () -> "");                 // key 목록도 Storage에서
        r.add("scent.storage.url", FakeStorage::url);
        r.add("scent.storage.secret-key", () -> FakeStorage.SECRET);
        r.add("scent.storage.timeout", () -> "4s");                  // Storage 전체 마감 시간(실제 30초). 테스트는 짧게
    }

    private static final AtomicInteger DAY = new AtomicInteger(1);

    /** 테스트마다 다른 버전(서버가 메모리에 보관한 파일과 섞이지 않게) */
    static String uniq() {
        return String.format("2026.10.%02d", DAY.getAndIncrement());
    }

    static String ip() {
        int n = IP.getAndIncrement();
        return "100.70." + (n / 250) + "." + (1 + n % 250);
    }

    @BeforeEach
    void fresh() {
        TestAuth.admin("delete from app.catalog_releases");
        FakeStorage.reset();
    }

    static void release(String version, byte[] file, byte[] keys, boolean current) {
        TestAuth.admin("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes, storage_path, keys_storage_path,"
                        + " min_app_version, is_current) values (?, 8, ?, ?, ?, ?, ?, '1.0.0', ?)",
                version, FakeStorage.sha256(file), FakeStorage.sha256(keys), file.length,
                "catalog-" + version + ".json.gz", "catalog-" + version + ".keys.json.gz", current);
        FakeStorage.put("catalog/catalog-" + version + ".json.gz", 200, file);
        FakeStorage.put("catalog/catalog-" + version + ".keys.json.gz", 200, keys);
    }

    HttpResponse<byte[]> download(String version, UUID install, String ip) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/catalog/" + version))
                .timeout(Duration.ofSeconds(15)).GET().header("X-Forwarded-For", ip);
        if (install != null) b.header("X-Install-Id", install.toString());
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static String text(HttpResponse<byte[]> r) {
        return new String(r.body(), StandardCharsets.UTF_8);
    }

    static void assertError(HttpResponse<byte[]> r, int status, String code) {
        assertEquals(status, r.statusCode(), text(r));
        assertTrue(text(r).startsWith("{\"error\":{\"code\":\"" + code + "\""), text(r));
    }

}
