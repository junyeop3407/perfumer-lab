package com.perfumerlab.scentstudio.api;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 서버 테스트용 DB: 내장 Postgres 17 하나를 모든 서버 테스트가 같이 쓴다(테스트 시간을 줄이려고).
 * Supabase 흉내(supabase_stub.sql) + 실제 DB와 같은 Flyway 설정 + scent_server에 테스트용 비밀번호.
 */
final class TestDb {
    static final String SERVER_PASSWORD = "testServerPassword0123456789abcdefXYZ";
    private static EmbeddedPostgres pg;

    private TestDb() {}

    static synchronized String jdbcUrl() {
        if (pg == null) start();
        return pg.getJdbcUrl("postgres", "postgres");
    }

    private static void start() {
        try {
            pg = EmbeddedPostgres.builder().start();
            DataSource ds = pg.getPostgresDatabase();
            try (Connection c = ds.getConnection(); Statement s = c.createStatement();
                 InputStream in = TestDb.class.getResourceAsStream("/supabase_stub.sql")) {
                s.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            Flyway.configure().dataSource(ds).schemas("flyway").createSchemas(true).locations("classpath:db/migration").load().migrate();
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("alter role scent_server with login password '" + SERVER_PASSWORD + "'");
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { pg.close(); } catch (Exception ignored) { }
            }));
        } catch (Exception e) {
            throw new IllegalStateException("테스트 DB 시작 실패", e);
        }
    }
}
