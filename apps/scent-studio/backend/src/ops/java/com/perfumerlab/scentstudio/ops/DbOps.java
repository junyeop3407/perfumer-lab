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
            case "server-password" -> serverPassword(env);
            case "server-check" -> serverCheck(env);
            case "cron" -> cron(env);
            case "auth-check" -> { // 읽기만: 세션 확인 함수 설계용 (auth.sessions 모양과 권한)
                try (Connection c = connect(env)) {
                    print(c, "select column_name, data_type, is_nullable from information_schema.columns where table_schema = 'auth' and table_name = 'sessions' order by ordinal_position");
                    print(c, "select r as 계정, has_schema_privilege(r, 'auth', 'usage') as auth_usage, has_table_privilege(r, 'auth.sessions', 'select') as sessions_select"
                            + " from unnest(array['postgres','scent_server','anon','authenticated']) r");
                    print(c, "select tableowner from pg_tables where schemaname = 'auth' and tablename = 'sessions'");
                    print(c, "select count(*) as 세션수 from auth.sessions");
                }
            }
            default -> throw new IllegalArgumentException("version | migrate | check | server-password | server-check | cron");
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

    /**
     * scent_server 비밀번호를 .env의 DB_SERVER_PASSWORD로 정한다. 드라이버가 이 컴퓨터에서 SCRAM-SHA-256 해시를 만들어
     * "alter user ... password '<해시>'"만 보낸다(원문은 DB로 가지 않음). 원문·해시는 어디에도 출력하지 않는다.
     */
    static void serverPassword(Map<String, String> env) throws SQLException {
        String pw = env.getOrDefault("DB_SERVER_PASSWORD", "");
        if (!pw.matches("[A-Za-z0-9]{32,}")) throw new IllegalStateException("DB_SERVER_PASSWORD는 영문·숫자 32자 이상이어야 합니다");
        char[] chars = pw.toCharArray();
        try (Connection c = connect(env)) {
            try {
                c.unwrap(org.postgresql.PGConnection.class).alterUserPassword("scent_server", chars, "scram-sha-256");
            } catch (SQLException e) {
                // 메시지에 비밀번호나 해시가 섞여 있으면 가린다
                String msg = String.valueOf(e.getMessage()).replace(pw, "<가림>").replaceAll("SCRAM-SHA-256\\$\\S*", "<해시 가림>");
                Throwable cause = e.getCause();
                String why = cause == null ? "" : " / 원인: " + cause.getClass().getSimpleName() + " "
                        + String.valueOf(cause.getMessage()).replace(pw, "<가림>").replaceAll("SCRAM-SHA-256\\$\\S*", "<해시 가림>");
                throw new IllegalStateException("비밀번호 설정 실패: " + e.getSQLState() + " " + msg + why);
            }
            try (Statement s = c.createStatement()) {
                s.execute("alter role scent_server login");
            }
            print(c, "select rolname, rolcanlogin as 로그인가능 from pg_roles where rolname = 'scent_server'");
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
        System.out.println("scent_server 비밀번호 설정 완료 (SCRAM-SHA-256, 원문·해시 출력 안 함)");
    }

    /** scent_server로 Session pooler에 실제로 접속해, 할 수 있는 것과 없는 것을 확인한다(쓰기 없음). */
    static void serverCheck(Map<String, String> env) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", env.get("DB_SERVER_USER"));
        p.setProperty("password", env.get("DB_SERVER_PASSWORD"));
        p.setProperty("ApplicationName", "scent-studio-dbops");
        try (Connection c = DriverManager.getConnection(env.get("DB_URL"), p)) {
            System.out.println("접속 성공 (사용자 이름 형식 scent_server.<참조값>, Session pooler)");
            print(c, "select current_user as 현재계정, session_user as 세션계정, current_setting('server_version') as 버전, inet_server_port() as 포트");
            print(c, "select (select count(*) from app.blends) as blends줄, (select count(*) from app.consents) as consents줄, (select count(*) from app.user_preferences) as prefs줄");
            for (String sql : new String[]{"select count(*) from app.events", "select count(*) from auth.users", "delete from app.consents",
                    "create table app.sneaky (id int)", "select app.purge_old_events()"}) {
                c.setAutoCommit(false);
                try (Statement s = c.createStatement()) {
                    s.execute(sql);
                    System.out.println("안 막힘 (문제!): " + sql);
                } catch (SQLException e) {
                    System.out.println("막힘 " + e.getSQLState() + ": " + sql);
                } finally {
                    c.rollback();
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("scent_server 접속 실패: " + e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse(""));
        }
    }

    /** 사용 기록 매일 정리(UTC 03:00)를 pg_cron에 등록하고 확인한다. 같은 이름이면 덮어쓴다. */
    static void cron(Map<String, String> env) throws SQLException {
        try (Connection c = connect(env)) {
            print(c, "select extname, extversion from pg_extension where extname = 'pg_cron'");
            print(c, "select cron.schedule('purge-old-events', '0 3 * * *', $$select app.purge_old_events()$$) as jobid");
            print(c, "select jobid, jobname, schedule, command, username, database, active from cron.job");
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
