package com.perfumerlab.scentstudio.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 3 DB 테스트 (docs/db_design.md 3판 12절). 내장 Postgres 17에 Supabase 흉내(supabase_stub.sql)를 깔고
 * Flyway 마이그레이션을 처음부터 돌린 뒤, 검사 규칙·삭제 규칙·권한·RLS를 확인한다.
 * 테스트마다 새 사용자를 만들어 서로 섞이지 않게 한다.
 */
class DbMigrationTest extends DbTestBase {

    // ---------- 마이그레이션 ----------

    @Test
    void migrationCreatesSixTables() throws SQLException {
        assertEquals(6, count("select count(*) from information_schema.tables where table_schema = 'app'"));
        assertEquals(1, count("select count(*) from flyway_schema_history where version = '1' and success"));
    }

    @Test
    void serverRoleCannotLogInUntilPasswordIsSet() throws SQLException {
        assertEquals(0, count("select count(*) from pg_roles where rolname = 'scent_server' and rolcanlogin"));
        assertEquals(0, count("select count(*) from pg_roles where rolname = 'scent_server' and (rolsuper or rolbypassrls or rolcreaterole)"));
    }

    // ---------- 잘못된 값 막기 ----------

    @Test
    void rejectsSatisfactionSix() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> insertFeedback(u, null, 6));
        fails(CHECK, () -> insertFeedback(u, null, 0));
    }

    @Test
    void rejectsMemoOver500() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> sql("insert into app.tasting_feedback (user_id, perfume_key, satisfaction, similarity, memo, engine_version, catalog_version)"
                + " values (?, 'k', 3, 3, ?, 8, 'v')", u, "가".repeat(501)));
        sql("insert into app.tasting_feedback (user_id, perfume_key, satisfaction, similarity, memo, engine_version, catalog_version)"
                + " values (?, 'k', 3, 3, ?, 8, 'v')", u, "가".repeat(500));
    }

    @Test
    void rejectsBlendNameOver40OrBlank() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, ?, ?::jsonb)", u, "가".repeat(41), ONE_NOTE));
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, '   ', ?::jsonb)", u, ONE_NOTE));
        sql("insert into app.blends (user_id, name, notes) values (?, ?, ?::jsonb)", u, "가".repeat(40), ONE_NOTE);
    }

    @ParameterizedTest(name = "잘못된 조합 노트: {0}")
    @ValueSource(strings = {
            // 층에 노트 4개
            "[{\"note_key\":\"a\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"b\",\"layer\":\"top\",\"strength\":\"weak\"},"
                    + "{\"note_key\":\"c\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"d\",\"layer\":\"top\",\"strength\":\"weak\"}]",
            // 같은 노트 두 번 (층이 달라도)
            "[{\"note_key\":\"rose\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"rose\",\"layer\":\"base\",\"strength\":\"weak\"}]",
            // 모르는 강도 / 숫자 강도
            "[{\"note_key\":\"a\",\"layer\":\"top\",\"strength\":\"extreme\"}]",
            "[{\"note_key\":\"a\",\"layer\":\"top\",\"strength\":3.0}]",
            // 모르는 층, 빈 목록, 칸 부족·초과, 배열이 아님
            "[{\"note_key\":\"a\",\"layer\":\"heart\",\"strength\":\"weak\"}]",
            "[]",
            "[{\"note_key\":\"a\",\"layer\":\"top\"}]",
            "[{\"note_key\":\"a\",\"layer\":\"top\",\"strength\":\"weak\",\"extra\":1}]",
            "{\"note_key\":\"a\"}"
    })
    void rejectsBadBlendNotes(String notes) throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, '조합', ?::jsonb)", u, notes));
    }

    @Test
    void acceptsFullBlendOfNineNotes() throws SQLException {
        UUID u = newUser();
        StringBuilder sb = new StringBuilder("[");
        String[] layers = {"top", "middle", "base"};
        for (int i = 0; i < 9; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"note_key\":\"n").append(i).append("\",\"layer\":\"").append(layers[i / 3]).append("\",\"strength\":\"medium\"}");
        }
        sql("insert into app.blends (user_id, name, notes) values (?, '꽉 찬 조합', ?::jsonb)", u, sb.append(']').toString());
    }

    @Test
    void rejectsBadPreferenceLists() throws SQLException {
        UUID u = newUser();
        String[] tooMany = new String[201];
        for (int i = 0; i < tooMany.length; i++) tooMany[i] = "note" + i;
        try (Connection c = ds.getConnection()) {
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, disliked_note_keys) values (?, ?)",
                    u, c.createArrayOf("text", tooMany)));
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, disliked_note_keys) values (?, ?)",
                    u, c.createArrayOf("text", new String[]{"patchouli", null})));
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, disliked_note_keys) values (?, ?)",
                    u, c.createArrayOf("text", new String[]{"patchouli", "patchouli"})));
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, preferred_families) values (?, ?)",
                    u, c.createArrayOf("text", new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"})));
            run(c, "insert into app.user_preferences (user_id, disliked_note_keys) values (?, ?)",
                    u, c.createArrayOf("text", java.util.Arrays.copyOf(tooMany, 200)));
        }
    }

    @Test
    void rejectsUnknownEventNameAndBadProps() throws SQLException {
        String insert = "insert into app.events (install_id, name, props, app_version, occurred_at) values (?, ?, ?::jsonb, '1.0.0', now())";
        UUID install = UUID.randomUUID();
        fails(CHECK, () -> sql(insert, install, "hacked", "{}"));
        fails(CHECK, () -> sql(insert, install, "app_open", "[1,2]"));
        fails(CHECK, () -> sql(insert, install, "app_open", "{\"x\":\"" + "a".repeat(2100) + "\"}"));
        sql(insert, install, "recs_viewed", "{\"engine_version\":8}");
    }

    @Test
    void rejectsDuplicateFavorite() throws SQLException {
        UUID u = newUser();
        sql("insert into app.favorites (user_id, perfume_key) values (?, 'Brand:perfume')", u);
        fails(UNIQUE, () -> sql("insert into app.favorites (user_id, perfume_key) values (?, 'Brand:perfume')", u));
    }

    @Test
    void rejectsLinkingAnotherUsersBlend() throws SQLException {
        UUID owner = newUser(), other = newUser();
        UUID blend = newBlend(owner);
        fails(FOREIGN_KEY, () -> insertFeedback(other, blend, 4));
        insertFeedback(owner, blend, 4);
    }

    @Test
    void rejectsUnknownConsentTypeAndSameVersionTwice() throws SQLException {
        UUID u = newUser();
        String insert = "insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, ?, ?, '1.0.0')";
        fails(CHECK, () -> sql(insert, u, "marketing", "2026-11-06"));
        sql(insert, u, "terms", "2026-11-06");
        fails(UNIQUE, () -> sql(insert, u, "terms", "2026-11-06"));
        sql(insert, u, "terms", "2026-12-01"); // 새 버전은 줄이 하나 더 쌓인다
        sql(insert, u, "age_14", "2026-11-06");
        assertEquals(3, count("select count(*) from app.consents where user_id = ?", u));
    }

    // ---------- 삭제 규칙 ----------

    @Test
    void deletingAccountRemovesUserDataAndAnonymizesEvents() throws SQLException {
        UUID u = newUser(), bystander = newUser();
        UUID blend = newBlend(u);
        sql("insert into app.user_preferences (user_id) values (?)", u);
        sql("insert into app.favorites (user_id, perfume_key) values (?, 'Brand:perfume')", u);
        insertFeedback(u, blend, 5);
        sql("insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, 'privacy', '2026-11-06', '1.0.0')", u);
        UUID install = UUID.randomUUID();
        sql("insert into app.events (install_id, user_id, name, app_version, occurred_at) values (?, ?, 'app_open', '1.0.0', now())",
                install, u);
        newBlend(bystander);

        // Supabase처럼 로그인 시스템 계정(auth.users 주인, app 권한 없음)이 지운다
        as("supabase_auth_admin", "delete from auth.users where id = ?", u);

        for (String t : new String[]{"user_preferences", "blends", "favorites", "tasting_feedback", "consents", "events"}) {
            assertEquals(0, count("select count(*) from app." + t + " where user_id = ?", u), t);
        }
        assertEquals(1, count("select count(*) from app.events where install_id = ? and user_id is null", install), "사용 기록은 그 줄 그대로 익명으로 남는다");
        assertEquals(1, count("select count(*) from app.blends where user_id = ?", bystander), "다른 사람 데이터는 그대로");
    }

    @Test
    void deletingBlendKeepsFeedbackWithoutLink() throws SQLException {
        UUID u = newUser();
        UUID blend = newBlend(u);
        insertFeedback(u, blend, 4);
        sql("delete from app.blends where id = ?", blend);
        assertEquals(1, count("select count(*) from app.tasting_feedback where user_id = ? and blend_id is null", u));
    }

    // ---------- updated_at ----------

    @Test
    void updatedAtChangesOnUpdate() throws SQLException {
        UUID u = newUser();
        UUID blend = newBlend(u);
        sql("insert into app.user_preferences (user_id, updated_at) values (?, '2000-01-01')", u);
        sql("update app.blends set updated_at = '2000-01-01' where id = ?", blend); // 트리거가 덮어써야 한다

        sql("update app.user_preferences set preferred_families = '{floral}' where user_id = ?", u);
        sql("update app.blends set name = '새 이름', updated_at = '1999-01-01' where id = ?", blend);

        assertEquals(1, count("select count(*) from app.user_preferences where user_id = ? and updated_at > now() - interval '1 minute'", u));
        assertEquals(1, count("select count(*) from app.blends where id = ? and updated_at > now() - interval '1 minute'", blend));
    }

    // ---------- 사용 기록 정리 ----------

    @Test
    void purgeRemovesOnlyEventsOlderThanTwelveMonths() throws SQLException {
        UUID install = UUID.randomUUID();
        String insert = "insert into app.events (install_id, name, app_version, occurred_at, received_at) values (?, 'app_open', '1.0.0', now(), now() - ?::interval)";
        sql(insert, install, "13 months");
        sql(insert, install, "11 months");
        sql(insert, install, "1 day");
        // 다른 테스트가 오래된 기록을 넣을 수 있으므로 지운 수는 1 이상, 이 기기의 줄은 정확히 확인
        assertTrue(count("select app.purge_old_events()") >= 1);
        assertEquals(0, count("select count(*) from app.events where install_id = ? and received_at < now() - interval '12 months'", install));
        assertEquals(2, count("select count(*) from app.events where install_id = ?", install));
    }

    // ---------- 권한 · RLS ----------

    @ParameterizedTest(name = "{0}는 app.{1}을 못 읽는다")
    @CsvSource({
            "anon,user_preferences", "anon,blends", "anon,favorites", "anon,tasting_feedback", "anon,events", "anon,consents",
            "authenticated,user_preferences", "authenticated,blends", "authenticated,favorites",
            "authenticated,tasting_feedback", "authenticated,events", "authenticated,consents"
    })
    void publicRolesCannotReadOrWrite(String role, String table) {
        fails(NO_PRIVILEGE, () -> countAs(role, "select count(*) from app." + table));
        fails(NO_PRIVILEGE, () -> as(role, "delete from app." + table));
    }

    @Test
    void rlsHidesRowsEvenIfAccessIsGrantedByMistake() throws SQLException {
        UUID u = newUser();
        newBlend(u);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("grant usage on schema app to anon");
            s.execute("grant select on app.blends to anon");
            s.execute("set local role anon");
            try (ResultSet r = s.executeQuery("select count(*) from app.blends")) {
                r.next();
                assertEquals(0, r.getLong(1), "RLS 규칙이 없는 역할은 줄을 하나도 못 본다");
            }
            c.rollback();
        }
    }

    @Test
    void serverRoleCanDoItsJob() throws SQLException {
        UUID u = newUser();
        UUID blend = UUID.randomUUID();
        as("scent_server", "insert into app.user_preferences (user_id) values (?)", u);
        as("scent_server", "update app.user_preferences set disliked_note_keys = '{patchouli}' where user_id = ?", u);
        as("scent_server", "insert into app.blends (id, user_id, name, notes) values (?, ?, '조합', ?::jsonb)", blend, u, ONE_NOTE);
        as("scent_server", "update app.blends set name = '바꾼 이름' where id = ?", blend);
        as("scent_server", "insert into app.favorites (user_id, perfume_key) values (?, 'Brand:perfume')", u);
        as("scent_server", "delete from app.favorites where user_id = ?", u);
        as("scent_server", "insert into app.tasting_feedback (user_id, perfume_key, blend_id, satisfaction, similarity, engine_version, catalog_version)"
                + " values (?, 'Brand:perfume', ?, 4, 3, 8, '2026.11.05')", u, blend);
        as("scent_server", "delete from app.tasting_feedback where user_id = ?", u); // 본인 기록 삭제 (1차)
        as("scent_server", "insert into app.events (install_id, user_id, name, app_version, occurred_at) values (?, ?, 'blend_saved', '1.0.0', now())",
                UUID.randomUUID(), u);
        as("scent_server", "insert into app.consents (user_id, doc_type, doc_version, app_version) values (?, 'terms', '2026-11-06', '1.0.0')", u);
        as("scent_server", "delete from app.blends where id = ?", blend);
        assertEquals(1, countAs("scent_server", "select count(*) from app.consents where user_id = '" + u + "'"));
    }

    @ParameterizedTest(name = "scent_server 금지: {0}")
    @ValueSource(strings = {
            "select count(*) from app.events",
            "update app.events set name = 'app_open'",
            "update app.consents set doc_version = 'x'",
            "delete from app.consents",
            "update app.favorites set perfume_key = 'x'",
            "update app.tasting_feedback set satisfaction = 5",
            "drop table app.blends",
            "alter table app.blends disable row level security",
            "create table app.sneaky (id int)",
            "select app.purge_old_events()",
            "select count(*) from auth.users"
    })
    void serverRoleCannotOverstep(String forbidden) {
        fails(NO_PRIVILEGE, () -> as("scent_server", forbidden));
    }

    @Test
    void keepaliveIsTheOnlyPublicFunction() throws SQLException {
        assertEquals(1, countAs("anon", "select public.keepalive()"));
        fails(NO_PRIVILEGE, () -> as("anon", "select app.purge_old_events()"));
        fails(NO_PRIVILEGE, () -> as("anon", "select app.valid_keys('{}', 1)"));
    }
}
