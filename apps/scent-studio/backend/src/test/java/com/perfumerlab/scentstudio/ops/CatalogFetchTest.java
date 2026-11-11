package com.perfumerlab.scentstudio.ops;

import com.perfumerlab.scentstudio.api.FakeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 5-1: 실제 카탈로그 받기 도구(./gradlew dbCatalogFetch). 실제 Storage가 아니라 가짜 Storage로.
 * plan_stage5.md 1-2: 해시 같음 → 씀 / 다름 → 안 씀 / 버전 없음 → 멈춤. 키는 출력하지 않는다.
 */
class CatalogFetchTest {

    static final String VERSION = "2099.01.02";
    static final String PATH = "catalog-" + VERSION + ".json.gz";
    static final String OBJECT = "/storage/v1/object/catalog/" + PATH;

    @TempDir
    Path dir;
    Path out;
    CatalogFetch fetch;

    @BeforeEach
    void fresh() {
        FakeStorage.reset();
        fetch = new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET);
        out = dir.resolve("assets/catalog/catalog.json.gz");
    }

    static byte[] catalog(String version, String source) {
        return FakeStorage.gzip("{\"version\":\"" + version + "\",\"engineVersion\":8,\"source\":{\"name\":\"" + source + "\",\"note\":\"x\"},"
                + "\"notes\":[{\"key\":\"rose\"}],\"families\":[{\"code\":\"floral\"}],\"perfumes\":[{\"key\":\"Fake_Maison:fake_scent\"}]}");
    }

    static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    CatalogFetch.Release release(byte[] body) throws Exception {
        return new CatalogFetch.Release(VERSION, sha(body), body.length, PATH);
    }

    @Test
    void sameHashWritesFileAndReleaseNote() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        fetch.fetch(release(body), out);
        assertArrayEquals(body, Files.readAllBytes(out));
        String note = Files.readString(CatalogFetch.releaseNote(out));
        assertTrue(note.contains("\"version\":\"" + VERSION + "\""), note);
        assertTrue(note.contains("\"sha256\":\"" + sha(body) + "\""), note);
        assertTrue(note.contains("\"size\":" + body.length), note);
    }

    @Test
    void differentHashWritesNothing() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.Release wrong = new CatalogFetch.Release(VERSION, "0".repeat(64), body.length, PATH);
        assertThrows(IllegalStateException.class, () -> fetch.fetch(wrong, out));
        assertFalse(Files.exists(out));
        assertFalse(Files.exists(CatalogFetch.releaseNote(out)));
    }

    @Test
    void differentHashKeepsOldFile() throws Exception {
        Files.createDirectories(out.getParent());
        Files.write(out, new byte[]{1, 2, 3});
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.Release wrong = new CatalogFetch.Release(VERSION, "0".repeat(64), body.length, PATH);
        assertThrows(IllegalStateException.class, () -> fetch.fetch(wrong, out));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(out), "받기에 실패하면 원래 파일은 그대로");
    }

    @Test
    void differentSizeWritesNothing() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.Release wrong = new CatalogFetch.Release(VERSION, sha(body), body.length + 1, PATH);
        assertThrows(IllegalStateException.class, () -> fetch.fetch(wrong, out));
        assertFalse(Files.exists(out));
    }

    @Test
    void noCurrentVersionStops() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CatalogFetch.require(Optional.empty()));
        assertTrue(e.getMessage().contains("지금 버전"), e.getMessage());
    }

    @Test
    void missingObjectStops() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        assertThrows(IllegalStateException.class, () -> fetch.fetch(release(body), out)); // 가짜 Storage 기본 404
        assertFalse(Files.exists(out));
    }

    @Test
    void versionInsideFileMustMatch() throws Exception {
        byte[] body = catalog("2099.01.01", "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        assertThrows(IllegalStateException.class, () -> fetch.fetch(release(body), out));
        assertFalse(Files.exists(out));
    }

    @Test
    void sampleCatalogIsNotAReal() throws Exception {
        byte[] body = catalog(VERSION, "sample");
        FakeStorage.putPath(OBJECT, 200, body);
        assertThrows(IllegalStateException.class, () -> fetch.fetch(release(body), out));
        assertFalse(Files.exists(out));
    }

    @Test
    void storagePathMustComeFromVersion() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        CatalogFetch.Release odd = new CatalogFetch.Release(VERSION, sha(body), body.length, "../other.json.gz");
        assertThrows(IllegalStateException.class, () -> fetch.fetch(odd, out));
        CatalogFetch.Release badVersion = new CatalogFetch.Release("latest", sha(body), body.length, "catalog-latest.json.gz");
        assertThrows(IllegalStateException.class, () -> fetch.fetch(badVersion, out));
        assertTrue(FakeStorage.seenRequests().isEmpty(), "Storage에 묻기 전에 멈춘다");
    }

    @Test
    void usesPrivateAddressWithKeyAndNeverPrintsKey() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        PrintStream old = System.out, oldErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
            fetch.fetch(release(body), out);
        } finally {
            System.setOut(old);
            System.setErr(oldErr);
        }
        assertFalse(buf.toString(StandardCharsets.UTF_8).contains("TESTONLY"), "키를 출력하지 않는다");
        for (String[] r : FakeStorage.seenRequests()) {
            assertFalse(r[0].contains("/public/"), "공개 주소는 쓰지 않는다");
            assertEquals(FakeStorage.SECRET, r[1]);
        }
        assertFalse(FakeStorage.seenRequests().isEmpty());
    }

    @Test
    void previewDoesNotDownload() throws Exception {
        byte[] body = catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.preview(release(body), out);
        assertFalse(Files.exists(out));
        assertTrue(FakeStorage.seenRequests().isEmpty());
    }
}
