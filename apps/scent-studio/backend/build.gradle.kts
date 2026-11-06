// 서버(백엔드팀). 단계 3: DB 마이그레이션 + 내장 Postgres 테스트만. Spring 서버는 단계 4에서 더한다.
plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 내장 Postgres (Docker 필요 없음). Supabase와 같은 17 버전대
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.11.0"))

    testImplementation("org.flywaydb:flyway-core:13.11.1")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql:13.11.1")
    testImplementation("org.postgresql:postgresql:42.7.14")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT }
}
