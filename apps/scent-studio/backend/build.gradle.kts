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

// 실제 DB 작업 도구 (src/ops, 앱 서버 코드 아님): ./gradlew dbVersion | dbMigrate | dbCheck
val ops: SourceSet by sourceSets.creating

dependencies {
    "opsImplementation"("org.flywaydb:flyway-core:13.11.1")
    "opsRuntimeOnly"("org.flywaydb:flyway-database-postgresql:13.11.1")
    "opsImplementation"("org.postgresql:postgresql:42.7.14")
}

for ((task, arg) in listOf("dbVersion" to "version", "dbMigrate" to "migrate", "dbCheck" to "check",
    "dbServerPassword" to "server-password", "dbServerCheck" to "server-check", "dbCron" to "cron")) {
    tasks.register<JavaExec>(task) {
        group = "db"
        classpath = ops.runtimeClasspath + sourceSets["main"].output
        mainClass = "com.perfumerlab.scentstudio.ops.DbOps"
        args(arg)
        workingDir = projectDir
        jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    }
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
