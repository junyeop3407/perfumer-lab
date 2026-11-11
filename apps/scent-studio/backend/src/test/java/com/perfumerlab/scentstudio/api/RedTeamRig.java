package com.perfumerlab.scentstudio.api;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * 레드팀용 로컬 서버(테스트 아님, 평소에는 건너뜀). 내장 Postgres + 가짜 로그인(JWKS) + 가짜 Storage·관리 API로
 * 실제 서버 코드를 이 컴퓨터에서 띄우고, 사용자 토큰을 build/redteam/rig.env에 적은 뒤 정한 시간 동안 기다린다.
 * 실제 Supabase·Render에는 아무것도 보내지 않는다. 시계는 진짜 시계(요청 제한이 실제처럼 돈다).
 *   ./gradlew test --tests '*RedTeamRig*' -Dredteam.rig=true -Dredteam.minutes=40
 * rig.env: PORT, TOKEN_A·TOKEN_B(동의함), TOKEN_C(동의 안 함), USER_A·USER_B(id), PERFUME_KEY, NOTE_KEYS, CATALOG_VERSION
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RedTeamRig extends ApiTestBase {

    static final Path OUT = Path.of("build", "redteam", "rig.env");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", TestDb::jdbcUrl);
        r.add("spring.datasource.username", () -> "scent_server");
        r.add("spring.datasource.password", () -> TestDb.SERVER_PASSWORD);
        r.add("scent.auth.supabase-url", () -> "https://testref.supabase.co");
        r.add("scent.auth.jwks-uri", TestAuth::jwksUri);
        r.add("scent.catalog.keys-file", () -> "");
        r.add("scent.storage.url", FakeStorage::url);
        r.add("scent.storage.secret-key", () -> FakeStorage.SECRET);
        r.add("scent.admin.url", FakeAuthAdmin::url);
        r.add("scent.admin.secret-key", () -> FakeStorage.SECRET);
    }

    @Test
    void serve() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("redteam.rig"), "레드팀 때만 (-Dredteam.rig=true)");
        TestAuth.admin("delete from app.catalog_releases");
        FakeStorage.reset();
        CatalogTestBase.release(CatalogTestBase.V, CatalogTestBase.FILE, CatalogTestBase.KEYS, true);

        TestAuth.User a = MeApiTestBase.consentedUser(), b = MeApiTestBase.consentedUser(), c = TestAuth.newUser();
        String env = "PORT=" + port + "\n"
                + "TOKEN_A=" + a.token() + "\nUSER_A=" + a.id() + "\n"
                + "TOKEN_B=" + b.token() + "\nUSER_B=" + b.id() + "\n"
                + "TOKEN_C=" + c.token() + "\nUSER_C=" + c.id() + "\n"
                + "PERFUME_KEY=Fake_Maison:123-sample-scent\nNOTE_KEYS=rose,musk\nFAMILY_KEYS=floral\n"
                + "CATALOG_VERSION=" + CatalogTestBase.V + "\n"
                + "READY_AT=" + Instant.now() + "\n";
        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, env, StandardCharsets.UTF_8);
        System.out.println("REDTEAM RIG READY port=" + port + " file=" + OUT.toAbsolutePath());
        Thread.sleep(Duration.ofMinutes(Long.getLong("redteam.minutes", 40)));
        Files.deleteIfExists(OUT);
    }
}
