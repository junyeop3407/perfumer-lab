package com.perfumerlab.scentstudio.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DB 테스트 공통 준비: 내장 Postgres 17 + Supabase 흉내(supabase_stub.sql) + Flyway 마이그레이션, 도우미 함수. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DbTestBase {

    static final String CHECK = "23514", UNIQUE = "23505", FOREIGN_KEY = "23503", NO_PRIVILEGE = "42501";
    static final String[] TABLES = {"user_preferences", "blends", "favorites", "tasting_feedback", "events", "consents"};

    EmbeddedPostgres pg;
    DataSource ds;

    @BeforeAll
    void startDatabase() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        ds = pg.getPostgresDatabase();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(resource("/supabase_stub.sql"));
        }
        // 실제 DB와 같은 설정: 기록 표는 공개 API 밖의 flyway 스키마 (DbOps.flyway)
        Flyway.configure().dataSource(ds).schemas("flyway").createSchemas(true).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    void stopDatabase() throws IOException {
        if (pg != null) pg.close();
    }

    // ---------- 도우미 ----------

    static String resource(String path) throws IOException {
        try (InputStream in = DbTestBase.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 표 주인(postgres)으로 실행 */
    void sql(String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection()) {
            run(c, sql, args);
        }
    }

    /** 역할을 바꿔 실행 (scent_server, anon, authenticated) */
    void as(String role, String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection()) {
            try (Statement s = c.createStatement()) {
                s.execute("set role " + role);
            }
            run(c, sql, args);
        }
    }

    static void run(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            p.execute();
        }
    }

    long count(String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    long countAs(String role, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("set role " + role);
            try (ResultSet r = s.executeQuery(sql)) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    static void fails(String sqlState, Executable action) {
        SQLException e = assertThrows(SQLException.class, action::run);
        assertEquals(sqlState, e.getSQLState(), e.getMessage());
    }

    @FunctionalInterface
    interface Executable {
        void run() throws Exception;
    }

    UUID newUser() throws SQLException {
        UUID id = UUID.randomUUID();
        sql("insert into auth.users (id, email) values (?, ?)", id, id + "@test.invalid");
        return id;
    }

    static final String ONE_NOTE = "[{\"note_key\":\"lemon\",\"layer\":\"top\",\"strength\":\"strong\"}]";

    UUID newBlend(UUID user) throws SQLException {
        UUID id = UUID.randomUUID();
        sql("insert into app.blends (id, user_id, name, notes) values (?, ?, '여름 조합', ?::jsonb)", id, user, ONE_NOTE);
        return id;
    }

    void insertFeedback(UUID user, UUID blend, int satisfaction) throws SQLException {
        sql("insert into app.tasting_feedback (user_id, perfume_key, blend_id, satisfaction, similarity, engine_version, catalog_version)"
                + " values (?, 'Brand:perfume', ?, ?, 3, 8, '2026.11.05')", user, blend, satisfaction);
    }
}
