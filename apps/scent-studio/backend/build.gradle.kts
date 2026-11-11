// 서버(백엔드팀). Java 21 + Spring Boot 4.1 (docs/server_design.md). DB 마이그레이션은 서버가 아니라 ./gradlew dbMigrate로만.
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

// 실제 DB 작업 도구 (src/ops, 앱 서버 코드 아님): ./gradlew dbVersion | dbMigrate | dbCheck ...
val ops: SourceSet by sourceSets.creating

dependencies {
    "opsImplementation"("org.flywaydb:flyway-core:13.11.1")
    "opsRuntimeOnly"("org.flywaydb:flyway-database-postgresql:13.11.1")
    "opsImplementation"("org.postgresql:postgresql:42.7.14")
    "opsImplementation"("tools.jackson.core:jackson-databind:3.1.7") // 카탈로그 배포(서버와 같은 버전)
}

// 카탈로그 배포: 기본은 미리 보기. -Papply=true일 때만 Storage·DB를 바꾼다(CatalogPublish)
tasks.register<JavaExec>("dbCatalogPublish") {
    group = "db"
    classpath = ops.runtimeClasspath
    mainClass = "com.perfumerlab.scentstudio.ops.DbOps"
    args("catalog-publish", (findProperty("file") ?: "").toString(), (findProperty("minApp") ?: "").toString(), (findProperty("apply") ?: "false").toString())
    workingDir = projectDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// 실제 카탈로그 받기(비공개 버킷 → 앱): 기본은 미리 보기. -Papply=true일 때만 받아서 해시 확인 뒤 씀(CatalogFetch)
tasks.register<JavaExec>("dbCatalogFetch") {
    group = "db"
    classpath = ops.runtimeClasspath
    mainClass = "com.perfumerlab.scentstudio.ops.DbOps"
    args("catalog-fetch", (findProperty("out") ?: "").toString(), (findProperty("apply") ?: "false").toString())
    workingDir = projectDir
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

for ((task, arg) in listOf("dbVersion" to "version", "dbMigrate" to "migrate", "dbCheck" to "check",
    "dbServerPassword" to "server-password", "dbServerCheck" to "server-check", "dbCron" to "cron", "dbAuthCheck" to "auth-check")) {
    tasks.register<JavaExec>(task) {
        group = "db"
        // DbOps는 서버 코드(main 클래스)를 쓰지 않고 마이그레이션 SQL(main 리소스)만 쓴다. Spring을 섞지 않으려고 리소스만 넣는다(리뷰 Gemini-2)
        classpath = ops.runtimeClasspath + files(sourceSets["main"].output.resourcesDir)
        mainClass = "com.perfumerlab.scentstudio.ops.DbOps"
        args(arg)
        workingDir = projectDir
        jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
        dependsOn("processResources")
    }
}

dependencies {
    // Spring Boot 버전표(BOM). 묶음 1부터 최종에 쓸 부품을 다 넣어 메모리·시작 시간을 실제와 같게 잰다
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")
    runtimeOnly("org.postgresql:postgresql:42.7.14")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 내장 Postgres (Docker 필요 없음). Supabase와 같은 17.11
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
    testImplementation(enforcedPlatform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.11.0"))

    testImplementation("org.flywaydb:flyway-core:13.11.1")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql:13.11.1")
    testImplementation("org.postgresql:postgresql:42.7.14")
    testImplementation(ops.output) // 카탈로그 배포 도구의 안전장치(비공개 버킷·공개 주소 막힘)도 테스트
}

tasks.bootJar {
    archiveFileName = "app.jar"
}
tasks.jar { enabled = false }

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT }
    // docs/openapi.json 다시 만들기: ./gradlew test --tests "*OpenApiDocTest*" -Dopenapi.write=true
    systemProperty("openapi.write", System.getProperty("openapi.write") ?: "false")
    inputs.file("../docs/openapi.json").optional()   // 파일만 손으로 고쳐도 테스트가 다시 돈다
    // 레드팀용 로컬 서버(RedTeamRig): ./gradlew test --tests "*RedTeamRig*" -Dredteam.rig=true -Dredteam.minutes=40
    systemProperty("redteam.rig", System.getProperty("redteam.rig") ?: "false")
    systemProperty("redteam.minutes", System.getProperty("redteam.minutes") ?: "40")
}
