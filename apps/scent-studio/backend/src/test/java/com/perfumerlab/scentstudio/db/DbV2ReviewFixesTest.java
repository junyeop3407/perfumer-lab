package com.perfumerlab.scentstudio.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 단계 4 묶음 2(V2) 리뷰에서 나온 것 (docs/review.md "묶음 2"). */
class DbV2ReviewFixesTest extends DbTestBase {

    static final String ROW = "(?, ?, 'app_open', '1.0.0', now())";
    static final String HEAD = "insert into app.events (install_id, client_event_id, name, app_version, occurred_at) values ";

    // QA-2 ①③: 한 문장(여러 줄) 안에 같은 기록이 두 번 있어도 오류 없이 한 번만 저장
    @Test
    void duplicateInsideOneMultiRowInsertStoredOnce() throws SQLException {
        UUID install = UUID.randomUUID(), e = UUID.randomUUID(), other = UUID.randomUUID();
        as("scent_server", HEAD + ROW + ", " + ROW + ", " + ROW + " on conflict do nothing", install, e, install, e, install, other);
        assertEquals(2, count("select count(*) from app.events where install_id = ?", install));
    }

    // QA-2 ②: 이미 있는 것과 새 것이 섞인 묶음 → 새 것만 저장
    @Test
    void mixedBatchStoresOnlyNewOnes() throws SQLException {
        UUID install = UUID.randomUUID(), old = UUID.randomUUID(), fresh = UUID.randomUUID();
        as("scent_server", HEAD + ROW + " on conflict do nothing", install, old);
        as("scent_server", HEAD + ROW + ", " + ROW + " on conflict do nothing", install, old, install, fresh);
        assertEquals(2, count("select count(*) from app.events where install_id = ?", install));
        assertEquals(1, count("select count(*) from app.events where install_id = ? and client_event_id = ?", install, fresh));
    }

    // QA-2: JDBC 배치(서버가 쓸 수 있는 다른 모양)로도 같다
    @Test
    void jdbcBatchWithDuplicatesStoredOnce() throws SQLException {
        UUID install = UUID.randomUUID(), e = UUID.randomUUID();
        try (Connection c = ds.getConnection()) {
            try (Statement s = c.createStatement()) { s.execute("set role scent_server"); }
            try (PreparedStatement p = c.prepareStatement(HEAD + ROW + " on conflict do nothing")) {
                for (int i = 0; i < 3; i++) {
                    p.setObject(1, install);
                    p.setObject(2, e);
                    p.addBatch();
                }
                p.executeBatch();
            }
        }
        assertEquals(1, count("select count(*) from app.events where install_id = ?", install));
    }

    // QA-4: 끝나는 시각 = 지금이면 이미 끝난 것
    @Test
    void sessionEndingExactlyNowIsNotAlive() throws SQLException {
        UUID u = newUser(), s = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            c.setAutoCommit(false); // 한 트랜잭션 안에서 now()는 같은 값
            st.execute("insert into auth.sessions (id, user_id, not_after) values ('" + s + "', '" + u + "', now())");
            st.execute("set local role scent_server");
            try (ResultSet r = st.executeQuery("select app.session_alive('" + s + "', '" + u + "')")) {
                r.next();
                assertFalse(r.getBoolean(1));
            }
            c.rollback();
        }
    }

    // QA-5: 부르는 쪽이 search_path를 바꿔 가짜 sessions 표를 끼워 넣어도 결과가 안 바뀐다
    @Test
    void callerSearchPathCannotSubstituteSessionsTable() throws SQLException {
        UUID u = newUser(), fake = UUID.randomUUID();
        sql("create schema if not exists evil");
        sql("create table if not exists evil.sessions (id uuid, user_id uuid, not_after timestamptz)");
        sql("grant usage on schema evil to scent_server");
        sql("grant select on evil.sessions to scent_server");
        sql("insert into evil.sessions values (?, ?, null)", fake, u);
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("set role scent_server");
            st.execute("set search_path = evil, public");
            try (ResultSet r = st.executeQuery("select app.session_alive('" + fake + "', '" + u + "')")) {
                r.next();
                assertFalse(r.getBoolean(1), "가짜 표의 줄로 true가 나오면 안 된다");
            }
        } finally {
            sql("drop schema evil cascade");
        }
    }
}
