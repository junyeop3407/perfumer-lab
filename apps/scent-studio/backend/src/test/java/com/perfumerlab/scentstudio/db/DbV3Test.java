package com.perfumerlab.scentstudio.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 4 묶음 7: V3 마이그레이션 테스트 — 오류 신고 표 app.error_reports (docs/server_design.md 7절).
 * 표 모양·칸 검사, 계정 삭제 시 같이 삭제, 권한(scent_server는 정해진 칸 넣기만), RLS.
 */
class DbV3Test extends DbTestBase {

    static final String COLS = "user_id, kind, perfume_key, note_key, memo, rank, score, engine_version, catalog_version, app_version, notes";

    /** 서버가 쓰는 모양 그대로(scent_server로 넣기) */
    void report(String role, UUID user, String kind, String perfume, String note, String memo) throws SQLException {
        as(role, "insert into app.error_reports (" + COLS + ") values (?, ?, ?, ?, ?, null, null, 8, '2026.11.05', '1.0.0', null)",
                user, kind, perfume, note, memo);
    }

    long reports(UUID user) throws SQLException {
        return count("select count(*) from app.error_reports where user_id = ?", user);
    }

    @Test
    void migrationV3Applied() throws SQLException {
        assertEquals(1, count("select count(*) from flyway.flyway_schema_history where version = '3' and success"));
    }

    // ---------- 칸 검사 ----------

    @ParameterizedTest
    @ValueSource(strings = {"not_similar", "wrong_info", "note_name", "other"})
    void acceptsEachKind(String kind) throws SQLException {
        UUID u = newUser();
        report("scent_server", u, kind, "Brand:perfume", null, "메모");
        assertEquals(1, reports(u));
        assertEquals(1, count("select count(*) from app.error_reports where user_id = ? and status = 'open' and created_at <= now()", u),
                "처리 상태 기본값 open, 만든 때는 DB가");
    }

    @Test
    void noteOnlyReportIsAllowed() throws SQLException {
        UUID u = newUser();
        report("scent_server", u, "note_name", null, "bergamot", null);
        assertEquals(1, reports(u));
    }

    @Test
    void needsPerfumeOrNote() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> report("scent_server", u, "other", null, null, "둘 다 없음"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bug", "", "NOT_SIMILAR"})
    void rejectsUnknownKind(String kind) throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> report("scent_server", u, kind, "Brand:perfume", null, null));
    }

    @Test
    void kindIsRequired() throws SQLException {
        UUID u = newUser();
        fails("23502", () -> report("scent_server", u, null, "Brand:perfume", null, null));
    }

    @Test
    void rejectsBadKeys() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> report("scent_server", u, "other", "", null, null));
        fails(CHECK, () -> report("scent_server", u, "other", " lead", null, null));
        fails(CHECK, () -> report("scent_server", u, "other", "k".repeat(201), null, null));
        fails(CHECK, () -> report("scent_server", u, "note_name", null, "trail ", null));
    }

    @Test
    void memoUpTo500() throws SQLException {
        UUID u = newUser();
        report("scent_server", u, "other", "Brand:perfume", null, "가".repeat(500));
        fails(CHECK, () -> report("scent_server", u, "other", "Brand:perfume", null, "가".repeat(501)));
        assertEquals(1, reports(u));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rank = 0", "rank = 11", "score = -0.01", "score = 1.01", "engine_version = 0",
            "catalog_version = ''", "app_version = ''", "catalog_version = repeat('x', 33)", "app_version = repeat('x', 33)", "status = 'done'"})
    void rejectsBadContext(String bad) throws SQLException {
        UUID u = newUser();
        report("scent_server", u, "other", "Brand:perfume", null, null);
        fails(CHECK, () -> sql("update app.error_reports set " + bad + " where user_id = ?", u));
    }

    @Test
    void requiredContextColumns() throws SQLException {
        UUID u = newUser();
        for (String col : new String[]{"engine_version", "catalog_version", "app_version", "user_id"}) {
            report("scent_server", u, "other", "Brand:perfume", null, null);
            fails("23502", () -> sql("update app.error_reports set " + col + " = null where user_id = ?", u));
        }
    }

    // ---------- 신고 당시 조합(사장 결정 2026-11-09): 없어도 되고, 있으면 blends.notes와 같은 검사 ----------

    void reportWithNotes(UUID user, String notes) throws SQLException {
        as("scent_server", "insert into app.error_reports (" + COLS + ") values (?, 'not_similar', 'Brand:perfume', null, null, null, null, 8,"
                + " '2026.11.05', '1.0.0', ?::jsonb)", user, notes);
    }

    @Test
    void blendNotesAcceptedOrNull() throws SQLException {
        UUID u = newUser();
        reportWithNotes(u, "[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"weak\"}]");
        reportWithNotes(u, null);
        assertEquals(1, count("select count(*) from app.error_reports where user_id = ? and notes is null", u));
        assertEquals(2, reports(u));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "{}",
            "\"rose\"",
            "[{\"note_key\":\"rose\",\"layer\":\"heart\",\"strength\":\"weak\"}]",
            "[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"huge\"}]",
            "[{\"note_key\":\"rose\",\"layer\":\"top\"}]",
            "[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"weak\",\"x\":1}]",
            "[{\"note_key\":\" rose\",\"layer\":\"top\",\"strength\":\"weak\"}]",
            "[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"rose\",\"layer\":\"base\",\"strength\":\"weak\"}]",
            "[{\"note_key\":\"a\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"b\",\"layer\":\"top\",\"strength\":\"weak\"},"
                    + "{\"note_key\":\"c\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"d\",\"layer\":\"top\",\"strength\":\"weak\"}]",
    })
    void badBlendNotesRejected(String notes) throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> reportWithNotes(u, notes));
        assertEquals(0, reports(u));
    }

    @Test
    void tenBlendNotesRejected() throws SQLException {
        UUID u = newUser();
        StringBuilder s = new StringBuilder("[");
        for (int i = 1; i <= 10; i++) s.append(i > 1 ? "," : "").append("{\"note_key\":\"n").append(i)
                .append("\",\"layer\":\"").append(new String[]{"top", "middle", "base"}[i % 3]).append("\",\"strength\":\"weak\"}");
        fails(CHECK, () -> reportWithNotes(u, s.append("]").toString()));
    }

    @Test
    void unknownUserRejected() {
        fails(FOREIGN_KEY, () -> report("scent_server", UUID.randomUUID(), "other", "Brand:perfume", null, null));
    }

    // ---------- 계정 삭제 ----------

    @Test
    void reportsAreDeletedWithTheAccount() throws SQLException {
        UUID u = newUser(), other = newUser();
        report("scent_server", u, "not_similar", "Brand:perfume", null, null);
        report("scent_server", u, "note_name", null, "rose", null);
        report("scent_server", other, "other", "Brand:perfume", null, null);
        sql("delete from auth.users where id = ?", u);
        assertEquals(0, reports(u));
        assertEquals(1, reports(other), "다른 사람 신고는 그대로");
    }

    // ---------- 권한 ----------

    @Test
    void serverRoleCanOnlyInsertTheseColumns() throws SQLException {
        UUID u = newUser();
        report("scent_server", u, "other", "Brand:perfume", null, null);
        fails(NO_PRIVILEGE, () -> as("scent_server", "select count(*) from app.error_reports"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "select id from app.error_reports"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "update app.error_reports set memo = 'x'"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "delete from app.error_reports"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "truncate app.error_reports"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "insert into app.error_reports (" + COLS + ", status)"
                + " values (?, 'other', 'Brand:perfume', null, null, null, null, 8, '2026.11.05', '1.0.0', null, 'resolved')", u));
        fails(NO_PRIVILEGE, () -> as("scent_server", "insert into app.error_reports (id, " + COLS + ")"
                + " values (gen_random_uuid(), ?, 'other', 'Brand:perfume', null, null, null, null, 8, '2026.11.05', '1.0.0', null)", u));
        fails(NO_PRIVILEGE, () -> as("scent_server", "insert into app.error_reports (" + COLS + ", created_at)"
                + " values (?, 'other', 'Brand:perfume', null, null, null, null, 8, '2026.11.05', '1.0.0', null, now() - interval '1 day')", u));
        assertEquals(1, reports(u));
    }

    @Test
    void publicRolesHaveNothingOnReports() throws SQLException {
        UUID u = newUser();
        for (String role : new String[]{"anon", "authenticated"}) {
            fails(NO_PRIVILEGE, () -> as(role, "select count(*) from app.error_reports"));
            fails(NO_PRIVILEGE, () -> report(role, u, "other", "Brand:perfume", null, null));
            assertEquals(0, count("select count(*) from unnest(array['select','insert','update','delete','truncate','references','trigger']) p"
                    + " where has_table_privilege(?, 'app.error_reports', p)", role), role);
            assertEquals(0, count("select count(*) from information_schema.column_privileges where table_schema = 'app'"
                    + " and table_name = 'error_reports' and grantee = ?", role), role + " 칸 권한");
        }
    }

    @Test
    void serverHasInsertOnExactlyTheseColumns() throws SQLException {
        assertEquals(11, count("select count(*) from information_schema.column_privileges where table_schema = 'app'"
                + " and table_name = 'error_reports' and grantee = 'scent_server' and privilege_type = 'INSERT'"
                + " and column_name = any(string_to_array(?, ', '))", COLS));
        assertEquals(11, count("select count(*) from information_schema.column_privileges where table_schema = 'app'"
                + " and table_name = 'error_reports' and grantee = 'scent_server'"), "넣기 11칸 말고 다른 권한 없음");
    }

    @Test
    void rlsOnWithInsertOnlyPolicyForServer() throws SQLException {
        assertEquals(1, count("select count(*) from pg_class where oid = 'app.error_reports'::regclass and relrowsecurity"));
        assertEquals(1, count("select count(*) from pg_policy where polrelid = 'app.error_reports'::regclass"));
        assertEquals(1, count("select count(*) from pg_policy where polrelid = 'app.error_reports'::regclass and polcmd = 'a'"
                + " and polroles = array['scent_server'::regrole::oid]"));
    }
}
