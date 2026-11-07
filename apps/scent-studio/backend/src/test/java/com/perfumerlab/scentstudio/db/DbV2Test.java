package com.perfumerlab.scentstudio.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 단계 4 묶음 2: V2 마이그레이션 테스트 (docs/server_design.md 3-1·4-2·7-1·7-2).
 * ① 세션 확인 함수 app.session_alive ② 카탈로그 버전 표 app.catalog_releases ③ events.client_event_id 중복 무시
 * ④ app 표 목록이 기준 목록(DbTestBase.APP_TABLES)과 정확히 같은지.
 */
class DbV2Test extends DbTestBase {

    // ---------- ④ 표 목록 ----------

    @Test
    void appTablesMatchTheExpectedListExactly() throws SQLException {
        List<String> actual = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select c.relname from pg_class c where c.relnamespace = 'app'::regnamespace"
                     + " and c.relkind in ('r', 'p', 'v', 'm', 'f') order by 1")) {
            while (r.next()) actual.add(r.getString(1));
        }
        assertEquals(APP_TABLES.stream().sorted().toList(), actual, "모르는 표가 생기거나 빠졌다 → DbTestBase.APP_TABLES와 맞출 것");
    }

    @Test
    void migrationV2Applied() throws SQLException {
        assertEquals(1, count("select count(*) from flyway.flyway_schema_history where version = '2' and success"));
    }

    // ---------- ① 세션 확인 함수 ----------

    UUID newSession(UUID user, String notAfter) throws SQLException {
        UUID id = UUID.randomUUID();
        sql("insert into auth.sessions (id, user_id, not_after) values (?, ?, " + notAfter + ")", id, user);
        return id;
    }

    long alive(Object session, Object user) throws SQLException {
        return countAs("scent_server", "select case when app.session_alive("
                + (session == null ? "null" : "'" + session + "'") + "::uuid, "
                + (user == null ? "null" : "'" + user + "'") + "::uuid) then 1 else 0 end");
    }

    @Test
    void liveSessionIsAlive() throws SQLException {
        UUID u = newUser();
        assertEquals(1, alive(newSession(u, "null"), u), "끝나는 시각 없음");
        assertEquals(1, alive(newSession(u, "now() + interval '1 hour'"), u), "끝나는 시각 전");
    }

    @Test
    void deletedSessionIsNotAlive() throws SQLException {
        UUID u = newUser();
        UUID s = newSession(u, "null");
        sql("delete from auth.sessions where id = ?", s);
        assertEquals(0, alive(s, u));
        assertEquals(0, alive(UUID.randomUUID(), u), "없는 세션");
    }

    @Test
    void sessionWithAnotherUserIsNotAlive() throws SQLException {
        UUID owner = newUser(), other = newUser();
        UUID s = newSession(owner, "null");
        assertEquals(0, alive(s, other));
    }

    @Test
    void expiredSessionIsNotAlive() throws SQLException {
        UUID u = newUser();
        assertEquals(0, alive(newSession(u, "now() - interval '1 second'"), u));
    }

    @Test
    void nullInputIsFalseNotNull() throws SQLException {
        UUID u = newUser();
        UUID s = newSession(u, "null");
        assertEquals(0, alive(null, u));
        assertEquals(0, alive(s, null));
        assertEquals(0, alive(null, null));
        assertEquals(1, count("select count(*) where app.session_alive(null, null) is not null"), "null이 아니라 false를 돌려준다");
    }

    @Test
    void sessionIsNotAliveAfterAccountDeletion() throws SQLException {
        UUID u = newUser();
        UUID s = newSession(u, "null");
        sql("delete from auth.users where id = ?", u);
        assertEquals(0, alive(s, u));
    }

    @Test
    void onlyServerRoleCanCallSessionAlive() throws SQLException {
        assertEquals(1, count("select count(*) where has_function_privilege('scent_server', 'app.session_alive(uuid, uuid)', 'execute')"));
        for (String role : new String[]{"anon", "authenticated"}) {
            assertEquals(0, count("select count(*) where has_function_privilege(?, 'app.session_alive(uuid, uuid)', 'execute')", role), role);
            fails(NO_PRIVILEGE, () -> as(role, "select app.session_alive(gen_random_uuid(), gen_random_uuid())"));
        }
        assertEquals(0, count("select count(*) from pg_proc p, aclexplode(p.proacl) a where p.oid = 'app.session_alive(uuid, uuid)'::regprocedure"
                + " and a.grantee = 0"), "PUBLIC에는 실행 권한 없음");
    }

    @Test
    void serverRoleStillCannotReadAuthTablesDirectly() {
        fails(NO_PRIVILEGE, () -> as("scent_server", "select count(*) from auth.sessions"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "select count(*) from auth.users"));
    }

    @Test
    void sessionAliveIsSecurityDefinerWithEmptySearchPathOwnedByMigrationRole() throws SQLException {
        assertEquals(1, count("select count(*) from pg_proc p where p.oid = 'app.session_alive(uuid, uuid)'::regprocedure"
                + " and p.prosecdef and p.proconfig = array['search_path=\"\"'] and p.provolatile = 's'"
                + " and pg_get_userbyid(p.proowner) = current_user"));
    }

    // ---------- ② 카탈로그 버전 표 ----------

    static final String HASH = "a".repeat(64), KEYS_HASH = "0123456789abcdef".repeat(4);

    void insertRelease(String version, boolean current) throws SQLException {
        sql("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes, storage_path, keys_storage_path,"
                + " min_app_version, is_current) values (?, 8, ?, ?, 2341384, ?, ?, '1.0.0', ?)",
                version, HASH, KEYS_HASH, "catalog-" + version + ".json.gz", "catalog-" + version + ".keys.json.gz", current);
    }

    String insertSql(String col, String value) {
        String version = col.equals("version") ? value.replace("'", "") : "2026.01.01";
        String[][] cols = {
                {"version", "'" + version + "'"}, {"engine_version", "8"}, {"sha256", "'" + HASH + "'"}, {"keys_sha256", "'" + KEYS_HASH + "'"},
                {"size_bytes", "100"}, {"storage_path", "'catalog-" + version + ".json.gz'"},
                {"keys_storage_path", "'catalog-" + version + ".keys.json.gz'"}, {"min_app_version", "'1.0.0'"}, {"note", "null"}};
        StringBuilder names = new StringBuilder(), values = new StringBuilder();
        for (String[] c : cols) {
            if (names.length() > 0) { names.append(", "); values.append(", "); }
            names.append(c[0]);
            values.append(c[0].equals(col) ? value : c[1]);
        }
        return "insert into app.catalog_releases (" + names + ") values (" + values + ")";
    }

    @Test
    void acceptsValidRelease() throws SQLException {
        insertRelease("2026.11.05", false);
        insertRelease("2026.11.05.2", false);
        assertEquals(1, count("select count(*) from app.catalog_releases where version = '2026.11.05.2' and published_at is not null and not is_current"));
    }

    @ParameterizedTest(name = "잘못된 버전: {0}")
    @ValueSource(strings = {"'2026.1.5'", "'2026.13.01'", "'2026.11.32'", "'2026.11.05.0'", "'2026.11.05.x'", "'v2026.11.05'",
            "'2026.11.05 '", "'2026-11-05'", "''", "null"})
    void rejectsBadVersion(String v) {
        // 경로도 같은 버전에서 만들어지므로, 버전이 틀리면 버전 검사(또는 not null)에 걸린다
        String version = v.equals("null") ? "x" : v.substring(1, v.length() - 1);
        String state = v.equals("null") ? "23502" : CHECK;
        fails(state, () -> sql("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes, storage_path,"
                + " keys_storage_path, min_app_version) values (" + v + ", 8, ?, ?, 100, ?, ?, '1.0.0')",
                HASH, KEYS_HASH, "catalog-" + version + ".json.gz", "catalog-" + version + ".keys.json.gz"));
    }

    @ParameterizedTest(name = "잘못된 값: {0}")
    @ValueSource(strings = {
            "sha256='ABCDEF" + "0000000000000000000000000000000000000000000000000000000000" + "'",  // 대문자
            "sha256='abc'",
            "keys_sha256='" + "g000000000000000000000000000000000000000000000000000000000000000" + "'",
            "keys_sha256='" + "a0000000000000000000000000000000000000000000000000000000000000000" + "'", // 65자
            "engine_version=0", "engine_version=-1",
            "size_bytes=0", "size_bytes=50000001",
            "storage_path='catalog-other.json.gz'", "storage_path='../catalog.json.gz'",
            "keys_storage_path='catalog-2026.01.01.json.gz'",
            "min_app_version='1.0'", "min_app_version='1.0.0-beta'", "min_app_version='01.0.0'", "min_app_version=''",
            "note=repeat('가', 201)"
    })
    void rejectsBadReleaseValues(String colValue) {
        String col = colValue.substring(0, colValue.indexOf('=')), value = colValue.substring(colValue.indexOf('=') + 1);
        fails(CHECK, () -> sql(insertSql(col, value)));
    }

    @Test
    void storagePathMustMatchItsVersion() {
        fails(CHECK, () -> sql("insert into app.catalog_releases (version, engine_version, sha256, keys_sha256, size_bytes, storage_path,"
                + " keys_storage_path, min_app_version) values ('2026.02.01', 8, ?, ?, 100, 'catalog-2026.02.02.json.gz',"
                + " 'catalog-2026.02.01.keys.json.gz', '1.0.0')", HASH, KEYS_HASH));
    }

    @Test
    void onlyOneCurrentRelease() throws SQLException {
        insertRelease("2026.03.01", true);
        fails(UNIQUE, () -> insertRelease("2026.03.02", true));
        // 지금 버전 바꾸기는 한 트랜잭션에서 (옛 것 내리고 새 것 올림)
        insertRelease("2026.03.03", false);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("update app.catalog_releases set is_current = false where is_current");
            s.execute("update app.catalog_releases set is_current = true where version = '2026.03.03'");
            c.commit();
        }
        assertEquals(1, count("select count(*) from app.catalog_releases where is_current"));
        assertEquals(1, count("select count(*) from app.catalog_releases where is_current and version = '2026.03.03'"));
    }

    @Test
    void duplicateVersionOrPathRejected() throws SQLException {
        insertRelease("2026.04.01", false);
        fails(UNIQUE, () -> insertRelease("2026.04.01", false));
    }

    @Test
    void serverRoleCanOnlyReadReleases() throws SQLException {
        insertRelease("2026.05.01", false);
        assertTrue(countAs("scent_server", "select count(*) from app.catalog_releases") >= 1);
        String ins = insertSql("version", "'2026.05.02'");
        fails(NO_PRIVILEGE, () -> as("scent_server", ins));
        fails(NO_PRIVILEGE, () -> as("scent_server", "update app.catalog_releases set is_current = true where version = '2026.05.01'"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "delete from app.catalog_releases"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "truncate app.catalog_releases"));
    }

    @Test
    void publicRolesHaveNothingOnReleases() throws SQLException {
        for (String role : new String[]{"anon", "authenticated"}) {
            fails(NO_PRIVILEGE, () -> as(role, "select count(*) from app.catalog_releases"));
            assertEquals(0, count("select count(*) from unnest(array['select','insert','update','delete','truncate','references','trigger']) p"
                    + " where has_table_privilege(?, 'app.catalog_releases', p)", role), role);
        }
    }

    @Test
    void rlsOnReleasesWithReadOnlyPolicyForServer() throws SQLException {
        assertEquals(1, count("select count(*) from pg_class where oid = 'app.catalog_releases'::regclass and relrowsecurity"));
        assertEquals(1, count("select count(*) from pg_policy where polrelid = 'app.catalog_releases'::regclass"));
        assertEquals(1, count("select count(*) from pg_policy where polrelid = 'app.catalog_releases'::regclass and polcmd = 'r'"
                + " and polroles = array['scent_server'::regrole::oid]"));
    }

    // ---------- ③ 사용 기록 중복 무시 ----------

    // 서버가 쓰는 모양 그대로. 충돌 칸을 적으면(on conflict (install_id, client_event_id)) 그 칸 읽기 권한이 필요해져서
    // scent_server가 사용 기록을 못 읽는 원칙이 깨진다 → 칸 없이 쓴다. events의 유일 검사는 id(자동 번호)와 이 인덱스뿐이다.
    static final String EVENT_INSERT = "insert into app.events (install_id, client_event_id, name, app_version, occurred_at)"
            + " values (?, ?, 'app_open', '1.0.0', now()) on conflict do nothing";

    @Test
    void sameClientEventIdTwiceIsStoredOnce() throws SQLException {
        UUID install = UUID.randomUUID(), event = UUID.randomUUID();
        as("scent_server", EVENT_INSERT, install, event);
        as("scent_server", EVENT_INSERT, install, event);
        assertEquals(1, count("select count(*) from app.events where install_id = ?", install));
    }

    @Test
    void duplicateWithoutOnConflictIsRejected() throws SQLException {
        UUID install = UUID.randomUUID(), event = UUID.randomUUID();
        String plain = EVENT_INSERT.substring(0, EVENT_INSERT.indexOf(" on conflict"));
        sql(plain, install, event);
        fails(UNIQUE, () -> sql(plain, install, event));
    }

    @Test
    void sameClientEventIdOnAnotherInstallIsStored() throws SQLException {
        UUID event = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        as("scent_server", EVENT_INSERT, a, event);
        as("scent_server", EVENT_INSERT, b, event);
        assertEquals(1, count("select count(*) from app.events where install_id = ?", a));
        assertEquals(1, count("select count(*) from app.events where install_id = ?", b));
    }

    @Test
    void oldEventsWithoutClientEventIdStillAllowed() throws SQLException {
        UUID install = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            as("scent_server", "insert into app.events (install_id, name, app_version, occurred_at) values (?, 'app_open', '1.0.0', now())", install);
        }
        assertEquals(2, count("select count(*) from app.events where install_id = ? and client_event_id is null", install));
    }

    @Test
    void eventsHaveOnlyTheseUniqueChecks() throws SQLException {
        // 칸 없는 on conflict do nothing은 모든 유일 검사 충돌을 건너뛴다 → events에 유일 검사가 더 생기면 여기서 알린다
        assertEquals(List.of("events_install_client_event_uq", "events_pkey"), uniqueIndexes("app.events"),
                "events에 유일 검사가 바뀌었다 → 이름만 고치지 말고, 서버의 칸 없는 on conflict do nothing 사용처를 다시 검토할 것(리뷰 묶음 2 QA-3)");
    }

    List<String> uniqueIndexes(String table) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select i.relname from pg_index x join pg_class i on i.oid = x.indexrelid"
                     + " where x.indrelid = '" + table + "'::regclass and x.indisunique order by 1")) {
            while (r.next()) out.add(r.getString(1));
        }
        return out;
    }

    @Test
    void serverRoleStillCannotReadOrChangeEvents() {
        fails(NO_PRIVILEGE, () -> as("scent_server", "select client_event_id from app.events"));
        fails(NO_PRIVILEGE, () -> as("scent_server", "update app.events set client_event_id = gen_random_uuid()"));
    }
}
