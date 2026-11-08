package com.perfumerlab.scentstudio.ops;

import com.perfumerlab.scentstudio.api.FakeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 묶음 6: 카탈로그 배포 도구(./gradlew dbCatalogPublish)의 안전장치. 실제 Storage가 아니라 가짜 Storage로.
 * 버킷이 공개면 멈춤, 공개 주소로 파일이 받아지면 멈춤, key 목록 파일은 매번 같은 바이트(같은 sha256).
 */
class CatalogPublishTest {

    static final String CATALOG = "{\"version\":\"2026.11.05\",\"engineVersion\":8,"
            + "\"notes\":[{\"key\":\"rose\"},{\"key\":\"musk\"}],\"families\":[{\"code\":\"floral\"}],\"perfumes\":[{\"key\":\"Fake_Maison:123-sample-scent\"}]}";

    CatalogPublish publish;

    @BeforeEach
    void fresh() {
        FakeStorage.reset();
        publish = new CatalogPublish(FakeStorage.url(), FakeStorage.SECRET);
    }

    @Test
    void publicBucketStops() {
        FakeStorage.putPath("/storage/v1/bucket/catalog", 200, "{\"id\":\"catalog\",\"public\":true}");
        assertThrows(IllegalStateException.class, publish::privateBucket);
    }

    @Test
    void privateBucketPasses() {
        FakeStorage.putPath("/storage/v1/bucket/catalog", 200, "{\"id\":\"catalog\",\"public\":false}");
        assertDoesNotThrow(publish::privateBucket);
    }

    @Test
    void unclearBucketAnswerStops() {
        FakeStorage.putPath("/storage/v1/bucket/catalog", 200, "{\"id\":\"catalog\"}"); // public 칸 없음
        assertThrows(IllegalStateException.class, publish::privateBucket);
    }

    @Test
    void servedOnPublicAddressStops() {
        FakeStorage.putPath("/storage/v1/object/public/catalog/catalog-2026.11.05.json.gz", 200, "x");
        assertThrows(IllegalStateException.class, () -> publish.publicBlocked("catalog-2026.11.05.json.gz"));
    }

    @Test
    void publicAddressBlockedPasses() {
        FakeStorage.putPath("/storage/v1/object/public/catalog/catalog-2026.11.05.json.gz", 400, "{\"error\":\"not public\"}");
        assertDoesNotThrow(() -> publish.publicBlocked("catalog-2026.11.05.json.gz"));
    }

    @Test
    void keysFileIsSortedAndStable() throws Exception {
        JsonNode root = JsonMapper.builder().build().readTree(CATALOG);
        byte[] a = CatalogPublish.keysFile(root, "2026.11.05"), b = CatalogPublish.keysFile(root, "2026.11.05");
        assertArrayEquals(a, b, "같은 카탈로그면 같은 바이트(같은 sha256)");
        String json = new String(new GZIPInputStream(new ByteArrayInputStream(a)).readAllBytes());
        assertEquals("{\"version\":\"2026.11.05\",\"notes\":[\"musk\",\"rose\"],\"families\":[\"floral\"],\"perfumes\":[\"Fake_Maison:123-sample-scent\"]}", json);
    }
}
