// 추천 엔진: 순수 Java 17 라이브러리. 앱과 서버가 같은 코드를 쓴다 (docs/design.md 8절)
plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // 안드로이드에서도 쓰므로 Java 17 문법까지만 (설치된 JDK 버전과 상관없이)
    options.release.set(17)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.gson)
}
