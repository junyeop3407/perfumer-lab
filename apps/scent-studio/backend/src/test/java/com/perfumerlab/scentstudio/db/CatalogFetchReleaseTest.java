package com.perfumerlab.scentstudio.db;

import com.perfumerlab.scentstudio.ops.CatalogFetch;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 5-1: dbCatalogFetch가 catalog_releases에서 "지금 버전" 한 줄을 읽는다(내장 Postgres). */
class CatalogFetchReleaseTest extends DbTestBase {

    static final String SHA_A = "a".repeat(64), SHA_B = "b".repeat(64);

    void release(String version, String sha, int size, boolean current) throws Exception {
        sql("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes, storage_path, keys_storage_path,"
                + " min_app_version, is_current) values (?, 8, ?, ?, ?, ?, ?, '1.0.0', ?)",
                version, sha, sha, size, "catalog-" + version + ".json.gz", "catalog-" + version + ".keys.json.gz", current);
    }

    @Test
    void readsOnlyTheCurrentRelease() throws Exception {
        try (Connection c = ds.getConnection()) {
            assertEquals(Optional.empty(), CatalogFetch.current(c), "줄이 없으면 비어 있음");
        }
        release("2099.01.01", SHA_A, 100, false);
        try (Connection c = ds.getConnection()) {
            assertEquals(Optional.empty(), CatalogFetch.current(c), "지금 버전 표시가 없으면 비어 있음");
        }
        release("2099.01.02", SHA_B, 200, true);
        try (Connection c = ds.getConnection()) {
            CatalogFetch.Release r = CatalogFetch.current(c).orElseThrow();
            assertEquals("2099.01.02", r.version());
            assertEquals(SHA_B, r.sha256());
            assertEquals(200, r.size());
            assertEquals("catalog-2099.01.02.json.gz", r.storagePath());
        }
    }
}
