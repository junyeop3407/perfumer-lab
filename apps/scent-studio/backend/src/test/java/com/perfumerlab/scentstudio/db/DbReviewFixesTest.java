package com.perfumerlab.scentstudio.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 5단계 리뷰(QA팀 에이전트 + Gemini 교차 리뷰, 2026-11-06)에서 나온 문제의 회귀 테스트. 승인 목록과 섞지 않는다.
 * 지적 번호와 판단은 docs/review.md.
 */
class DbReviewFixesTest extends DbTestBase {

    // QA-2: 막는 장치 ②(표 권한)와 ③(RLS)를 6개 표 모두 직접 확인 (스키마 권한 없음에 가려지지 않게)
    @Test
    void rlsIsOnForEveryTable() throws SQLException {
        assertEquals(6, count("select count(*) from pg_class where relnamespace = 'app'::regnamespace and relkind = 'r' and relrowsecurity"));
    }

    @Test
    void publicRolesHaveNoTablePrivilegeAtAll() throws SQLException {
        for (String role : new String[]{"anon", "authenticated"}) {
            assertEquals(0, count("select count(*) from pg_class c, unnest(array['select','insert','update','delete','truncate','references','trigger']) p"
                    + " where c.relnamespace = 'app'::regnamespace and c.relkind = 'r' and has_table_privilege(?, c.oid, p)", role), role);
        }
        assertEquals(0, count("select count(*) from information_schema.role_table_grants where table_schema = 'app' and grantee in ('anon', 'authenticated', 'PUBLIC')"));
        assertEquals(0, count("select count(*) from information_schema.role_column_grants where table_schema = 'app' and grantee in ('anon', 'authenticated', 'PUBLIC')"));
    }

    // QA-3: 함수 실행 권한 회수를 스키마 권한과 상관없이 확인
    @Test
    void onlyKeepaliveIsExecutableByPublicRoles() throws SQLException {
        for (String role : new String[]{"anon", "authenticated", "scent_server"}) {
            for (String fn : new String[]{"app.purge_old_events()", "app.touch_updated_at()"}) {
                assertEquals(0, count("select count(*) where has_function_privilege(?, ?, 'execute')", role, fn), role + " " + fn);
            }
        }
        for (String role : new String[]{"anon", "authenticated"}) {
            for (String fn : new String[]{"app.valid_keys(text[], integer)", "app.valid_blend_notes(jsonb)"}) {
                assertEquals(0, count("select count(*) where has_function_privilege(?, ?, 'execute')", role, fn), role + " " + fn);
            }
            assertEquals(1, count("select count(*) where has_function_privilege(?, 'public.keepalive()', 'execute')", role));
        }
    }

    // QA-6: 나중 마이그레이션이 app에 만드는 함수도 기본으로 아무도 실행 못 해야 한다
    @Test
    void newFunctionsInAppAreNotExecutableByDefault() throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("create function app.later_admin_task() returns integer language sql as 'select 1'");
            try (ResultSet r = s.executeQuery("select has_function_privilege('scent_server', 'app.later_admin_task()', 'execute')")) {
                r.next();
                assertFalse(r.getBoolean(1), "scent_server가 새 관리용 함수를 실행할 수 있으면 안 된다");
            }
            c.rollback();
        }
    }

    // QA-4: 공백·보이지 않는 글자만 있는 조합 이름, 제어 문자가 든 이름
    @ParameterizedTest(name = "이름 거부: U+{0}")
    @ValueSource(strings = {"0009", "000A", "3000", "00A0", "200B", "FEFF", "2060"})
    void rejectsInvisibleOnlyBlendNames(String hex) throws SQLException {
        UUID u = newUser();
        String invisible = new String(Character.toChars(Integer.parseInt(hex, 16)));
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, ?, ?::jsonb)", u, invisible + invisible, ONE_NOTE));
    }

    @Test
    void rejectsControlCharactersInsideBlendName() throws SQLException {
        UUID u = newUser();
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, ?, ?::jsonb)", u, "여름\n조합", ONE_NOTE));
        sql("insert into app.blends (user_id, name, notes) values (?, ?, ?::jsonb)", u, "여름 조합 No.1 🌿", ONE_NOTE);
    }

    // QA-7: 키는 카탈로그처럼 눈에 보이는 ASCII만, 앞뒤 공백 없음 (유니코드·공백으로 "중복 없음" 피하기 막기)
    @ParameterizedTest(name = "키 거부: [{0}]")
    @ValueSource(strings = {" ", "\t", " rose", "rose ", "rosé", "rosé", "rose​", "　"})
    void rejectsNonCanonicalKeys(String key) throws SQLException {
        UUID u = newUser();
        try (Connection c = ds.getConnection()) {
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, disliked_note_keys) values (?, ?)", u, c.createArrayOf("text", new String[]{key})));
            fails(CHECK, () -> run(c, "insert into app.user_preferences (user_id, preferred_families) values (?, ?)", u, c.createArrayOf("text", new String[]{key})));
        }
        String notes = "[{\"note_key\":\"" + key.replace("\t", "\\t") + "\",\"layer\":\"top\",\"strength\":\"weak\"}]";
        fails(CHECK, () -> sql("insert into app.blends (user_id, name, notes) values (?, '조합', ?::jsonb)", u, notes));
        fails(CHECK, () -> sql("insert into app.favorites (user_id, perfume_key) values (?, ?)", u, key));
        fails(CHECK, () -> sql("insert into app.tasting_feedback (user_id, perfume_key, satisfaction, similarity, engine_version, catalog_version) values (?, ?, 3, 3, 8, 'v')", u, key));
    }

    @Test
    void acceptsRealCatalogKeyShapes() throws SQLException {
        UUID u = newUser();
        sql("insert into app.favorites (user_id, perfume_key) values (?, ?)", u, "4160_Tuesdays:Our_Modern_Lives__beta_");
        sql("insert into app.blends (user_id, name, notes) values (?, '조합', ?::jsonb)", u,
                "[{\"note_key\":\"black pepper\",\"layer\":\"top\",\"strength\":\"weak\"},{\"note_key\":\"Calabrian bergamot\",\"layer\":\"top\",\"strength\":\"weak\"}]");
    }

    // QA-8: 서버 계정은 바꾸면 안 되는 칸(주인·만든 때·서버 시각·자동 번호)을 못 바꾼다
    @ParameterizedTest(name = "scent_server 칸 금지: {0}")
    @ValueSource(strings = {
            "update app.blends set user_id = gen_random_uuid()",
            "update app.blends set created_at = now()",
            "update app.user_preferences set user_id = gen_random_uuid()",
            "insert into app.events (id, install_id, name, app_version, occurred_at) overriding system value values (999999, gen_random_uuid(), 'app_open', '1', now())",
            "insert into app.events (install_id, name, app_version, occurred_at, received_at) values (gen_random_uuid(), 'app_open', '1', now(), now() - interval '2 years')",
            "insert into app.consents (user_id, doc_type, doc_version, app_version, agreed_at) values (gen_random_uuid(), 'terms', 'v', '1', '2000-01-01')",
            "insert into app.blends (user_id, name, notes, created_at) values (gen_random_uuid(), 'x', '[]', '2000-01-01')",
            "insert into app.tasting_feedback (user_id, perfume_key, satisfaction, similarity, engine_version, catalog_version, created_at) values (gen_random_uuid(), 'k', 3, 3, 8, 'v', '2000-01-01')"
    })
    void serverRoleCannotTouchProtectedColumns(String forbidden) {
        fails(NO_PRIVILEGE, () -> as("scent_server", forbidden));
    }

    // QA-9: "추가만"·"지울 수 없음"을 회귀로 고정
    @ParameterizedTest(name = "scent_server 금지: {0}")
    @ValueSource(strings = {"delete from app.events", "truncate app.events", "truncate app.consents", "truncate app.blends"})
    void serverRoleCannotDeleteLogsOrTruncate(String forbidden) {
        fails(NO_PRIVILEGE, () -> as("scent_server", forbidden));
    }
}
