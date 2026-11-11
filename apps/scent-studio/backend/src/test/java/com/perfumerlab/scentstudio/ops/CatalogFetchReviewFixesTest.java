package com.perfumerlab.scentstudio.ops;

import com.perfumerlab.scentstudio.api.FakeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** 5-1 리뷰(QA 낮음-5·8) 회귀 테스트: 본문이 멈추면 끝나고, 실패해도 키가 안 나오고, 기본은 미리 보기. */
class CatalogFetchReviewFixesTest {

    static final String VERSION = "2099.01.02";
    static final String PATH = "catalog-" + VERSION + ".json.gz";
    static final String OBJECT = "/storage/v1/object/catalog/" + PATH;

    @TempDir
    Path dir;
    Path out;

    @BeforeEach
    void fresh() {
        FakeStorage.reset();
        out = dir.resolve("catalog.json.gz");
    }

    CatalogFetch.Release release(byte[] body) throws Exception {
        return new CatalogFetch.Release(VERSION, CatalogFetchTest.sha(body), body.length, PATH);
    }

    @Test
    void qa5StalledBodyEndsWithTimeout() throws Exception {
        byte[] body = CatalogFetchTest.catalog(VERSION, "Parfumo");
        FakeStorage.stallPath(OBJECT);
        CatalogFetch fetch = new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET, Duration.ofSeconds(1));
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> fetch.fetch(release(body), out));
            assertTrue(e.getMessage().contains("시간"), e.getMessage());
        });
        assertFalse(Files.exists(out));
    }

    @Test
    void qa8ServerErrorWithRightBytesStillStops() throws Exception {
        byte[] body = CatalogFetchTest.catalog(VERSION, "Parfumo");
        FakeStorage.putPath(OBJECT, 500, body);
        assertThrows(IllegalStateException.class, () -> new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET).fetch(release(body), out));
        assertFalse(Files.exists(out));
    }

    @Test
    void qa8BodyBiggerThanRecordStops() throws Exception {
        byte[] body = CatalogFetchTest.catalog(VERSION, "Parfumo");
        byte[] bigger = Arrays.copyOf(body, body.length + 10);
        FakeStorage.putPath(OBJECT, 200, bigger);
        assertThrows(IllegalStateException.class, () -> new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET).fetch(release(body), out));
        assertFalse(Files.exists(out));
    }

    @Test
    void qa8FailureMessagesNeverContainKey() throws Exception {
        byte[] body = CatalogFetchTest.catalog(VERSION, "Parfumo");
        CatalogFetch fetch = new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET);
        for (int status : new int[]{400, 401, 403, 404, 500}) {
            FakeStorage.putPath(OBJECT, status, "{\"error\":\"x\"}");
            Exception e = assertThrows(Exception.class, () -> fetch.fetch(release(body), out));
            assertFalse(String.valueOf(e.getMessage()).contains("TESTONLY"), "상태 " + status);
        }
        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.Release wrong = new CatalogFetch.Release(VERSION, "0".repeat(64), body.length, PATH);
        Exception e = assertThrows(Exception.class, () -> fetch.fetch(wrong, out));
        assertFalse(String.valueOf(e.getMessage()).contains("TESTONLY"));
    }

    @Test
    void qa8PreviewIsTheDefaultAndOnlyExactTrueApplies() throws Exception {
        byte[] body = CatalogFetchTest.catalog(VERSION, "Parfumo");
        CatalogFetch.runWith(release(body), out, false, () -> fail("미리 보기에서는 받지 않는다"));
        assertFalse(Files.exists(out));
        assertFalse(CatalogFetch.applyFlag(null));
        assertFalse(CatalogFetch.applyFlag(""));
        assertFalse(CatalogFetch.applyFlag("false"));
        assertFalse(CatalogFetch.applyFlag("yes"));
        assertTrue(CatalogFetch.applyFlag("true"));

        FakeStorage.putPath(OBJECT, 200, body);
        CatalogFetch.runWith(release(body), out, true, () -> new CatalogFetch(FakeStorage.url(), FakeStorage.SECRET));
        assertTrue(Files.exists(out));
    }
}
