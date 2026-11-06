package com.perfumerlab.scentstudio.ops;

import org.flywaydb.core.Flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 실제 DB(Supabase) 작업 도구. backend/.env를 읽는다. 비밀번호는 절대 출력하지 않는다.
 *   ./gradlew dbVersion   Postgres 버전 확인
 *   ./gradlew dbMigrate   주 버전이 17일 때만 마이그레이션 (표 만들기용 계정)
 *   ./gradlew dbCheck     권한·노출 상태 조회 (읽기만, 확인용 임시 함수는 되돌림)
 */
public final class DbOps {

    static final int EXPECTED_MAJOR = 17;

    public static void main(String[] args) throws Exception {
        Map<String, String> env = readEnv(Path.of(".env"));
        String cmd = args.length > 0 ? args[0] : "";
        switch (cmd) {
            case "version" -> version(env);
            case "migrate" -> migrate(env);
            case "check" -> check(env);
            default -> throw new IllegalArgumentException("version | migrate | check");
        }
    }

    static Map<String, String> readEnv(Path file) throws Exception {
        if (!Files.exists(file)) throw new IllegalStateException("backend/.env가 없습니다 (.env.example을 복사해 채우세요)");
        Map<String, String> m = new HashMap<>();
        for (String line : Files.readAllLines(file)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
            int i = line.indexOf('=');
            m.put(line.substring(0, i).strip(), line.substring(i + 1).strip());
        }
        for (String k : new String[]{"DB_URL", "DB_MIGRATION_USER", "DB_MIGRATION_PASSWORD"}) {
            String v = m.getOrDefault(k, "");
            if (v.isEmpty() || v.startsWith("<")) throw new IllegalStateException(k + " 칸이 비어 있습니다");
        }
        if (!m.get("DB_URL").startsWith("jdbc:postgresql://")) throw new IllegalStateException("DB_URL은 jdbc:postgresql:// 로 시작해야 합니다");
        return m;
    }

    static Connection connect(Map<String, String> env) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", env.get("DB_MIGRATION_USER"));
        p.setProperty("password", env.get("DB_MIGRATION_PASSWORD"));
        p.setProperty("ApplicationName", "scent-studio-dbops");
        return DriverManager.getConnection(env.get("DB_URL"), p);
    }

    static int major(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("show server_version_num")) {
            r.next();
            return Integer.parseInt(r.getString(1)) / 10000;
        }
    }

    static void version(Map<String, String> env) throws SQLException {
        try (Connection c = connect(env)) {
            print(c, "select version()");
            int major = major(c);
            System.out.println("주 버전: " + major + (major == EXPECTED_MAJOR ? " (테스트 DB와 같음)" : " (테스트 DB " + EXPECTED_MAJOR + "와 다름!)"));
        }
    }

    static void migrate(Map<String, String> env) throws SQLException {
        try (Connection c = connect(env)) {
            int major = major(c);
            if (major != EXPECTED_MAJOR) throw new IllegalStateException("Postgres 주 버전 " + major + " ≠ " + EXPECTED_MAJOR + ": 마이그레이션하지 않습니다");
        }
        var result = flyway(env.get("DB_URL"), env.get("DB_MIGRATION_USER"), env.get("DB_MIGRATION_PASSWORD")).migrate();
        System.out.println("마이그레이션 " + result.migrationsExecuted + "개 실행, 지금 버전: " + result.targetSchemaVersion);
    }

    /** 테스트(DbTestBase)와 같은 설정: 기록 표는 공개 API 밖의 flyway 스키마에 둔다 */
    public static Flyway flyway(String url, String user, String password) {
        return Flyway.configure().dataSource(url, user, password)
                .schemas("flyway").createSchemas(true)
                .locations("classpath:db/migration").load();
    }

    static void check(Map<String, String> env) throws SQLException {
        try (Connection c = connect(env)) {
            System.out.println("\n== 버전 ==");
            print(c, "show server_version");
            System.out.println("\n== 마이그레이션 기록 ==");
            print(c, "select version, description, success from flyway.flyway_schema_history order by installed_rank");
            System.out.println("\n== app 표와 RLS ==");
            print(c, "select c.relname as 표, c.relrowsecurity as rls, (select count(*) from pg_policy p where p.polrelid = c.oid) as 규칙수"
                    + " from pg_class c where c.relnamespace = 'app'::regnamespace and c.relkind = 'r' order by 1");
            System.out.println("\n== scent_server 계정 ==");
            print(c, "select rolname, rolcanlogin as 로그인가능, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb from pg_roles where rolname = 'scent_server'");
            System.out.println("\n== 계정별 스키마 권한 (app, flyway) ==");
            print(c, "select r.rolname as 계정, s.nspname as 스키마, has_schema_privilege(r.oid, s.oid, 'usage') as usage_, has_schema_privilege(r.oid, s.oid, 'create') as create_"
                    + " from pg_roles r, pg_namespace s where r.rolname in ('anon','authenticated','service_role','scent_server','authenticator')"
                    + " and s.nspname in ('app','flyway') order by 1, 2");
            System.out.println("\n== 계정별 app 표 권한 (true인 것만, 칸 단위 포함) ==");
            print(c, "select r.rolname as 계정, c.relname as 표, string_agg(p, ',' order by p) as 권한"
                    + " from pg_roles r cross join pg_class c cross join unnest(array['select','insert','update','delete','truncate','references','trigger']) p"
                    + " where r.rolname in ('anon','authenticated','service_role','scent_server') and c.relnamespace = 'app'::regnamespace and c.relkind = 'r'"
                    + " and (has_table_privilege(r.oid, c.oid, p) or (p in ('select','insert','update','references') and has_any_column_privilege(r.oid, c.oid, p)))"
                    + " group by 1, 2 order by 1, 2");
            System.out.println("\n== service_role (Supabase 비밀 키 역할) 성질 ==");
            print(c, "select rolname, rolbypassrls from pg_roles where rolname = 'service_role'");
            System.out.println("\n== 함수 실행 권한 ==");
            print(c, "select r.rolname as 계정, f.fn as 함수, has_function_privilege(r.oid, f.fn, 'execute') as 실행"
                    + " from pg_roles r cross join (values ('public.keepalive()'), ('app.purge_old_events()'), ('app.valid_key(text)'), ('app.touch_updated_at()')) f(fn)"
                    + " where r.rolname in ('anon','authenticated','service_role','scent_server') order by 2, 1");
            System.out.println("\n== postgres 계정의 기본 권한 설정 (pg_default_acl) ==");
            print(c, "select coalesce(n.nspname, '(전체)') as 스키마, d.defaclobjtype as 종류, d.defaclacl::text as 권한"
                    + " from pg_default_acl d left join pg_namespace n on n.oid = d.defaclnamespace"
                    + " where d.defaclrole = 'postgres'::regrole order by 1, 2");
            System.out.println("\n== public에 새 함수를 만들면 누가 실행할 수 있나 (임시로 만들고 되돌림) ==");
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("create function public.zz_probe_default_acl() returns integer language sql as 'select 1'");
                print(c, "select r.rolname as 계정, has_function_privilege(r.oid, 'public.zz_probe_default_acl()', 'execute') as 실행"
                        + " from pg_roles r where r.rolname in ('anon','authenticated','service_role') order by 1");
            } finally {
                c.rollback();
                c.setAutoCommit(true);
            }
            System.out.println("\n== anon으로 app 표 읽기 시도 ==");
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("set local role anon");
                s.executeQuery("select count(*) from app.blends");
                System.out.println("읽힘 (문제!)");
            } catch (SQLException e) {
                System.out.println("막힘: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            } finally {
                c.rollback();
                c.setAutoCommit(true);
            }
            System.out.println("\n== anon으로 keepalive 실행 ==");
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("set local role anon");
                try (ResultSet r = s.executeQuery("select public.keepalive()")) {
                    r.next();
                    System.out.println("결과: " + r.getInt(1));
                }
            } catch (SQLException e) {
                System.out.println("실패: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
            } finally {
                c.rollback();
                c.setAutoCommit(true);
            }
            System.out.println("\n== Data API 설정이 DB에 있는지 (authenticator 역할 설정) ==");
            print(c, "select rolname, rolconfig::text from pg_roles where rolname = 'authenticator'");
        }
    }

    static void print(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            ResultSetMetaData md = r.getMetaData();
            StringBuilder head = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) head.append(i > 1 ? " | " : "").append(md.getColumnLabel(i));
            System.out.println(head);
            int rows = 0;
            while (r.next()) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= md.getColumnCount(); i++) line.append(i > 1 ? " | " : "").append(r.getString(i));
                System.out.println(line);
                rows++;
            }
            if (rows == 0) System.out.println("(없음)");
        }
    }
}
