# scent-studio 서버 이미지 (Render 무료: 메모리 512MB, CPU 0.1). docs/server_design.md 6절.
# 빌드: Render가 이 폴더(apps/scent-studio/backend)를 받아 이 파일로 만든다. 비밀값은 이미지에 넣지 않는다(Render 환경 변수).

# 1) 빌드: Gradle 래퍼로 실행 파일(app.jar)을 만든다. 테스트는 이 컴퓨터에서 돌리므로 여기서는 건너뛴다
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q help
COPY src/main src/main
RUN ./gradlew --no-daemon -q bootJar -x test \
 && java -Djarmode=tools -jar build/libs/app.jar extract --destination /app

# 2) 실행: JRE만. CDS(클래스 미리 읽어 둔 파일)를 이 JVM으로 만들어 시작 시간을 줄인다
FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app /app
# CDS 학습 실행: 설정까지만 읽고 바로 끝낸다(DB에 붙지 않음, 비밀값 없음)
RUN DB_URL=jdbc:postgresql://127.0.0.1:1/cds DB_SERVER_PASSWORD=none \
    java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -jar app.jar \
 && chown -R app /app
USER app
EXPOSE 8080
# 메모리: 힙은 컨테이너의 55%(512MB면 약 280MB), 나머지는 server_design.md 6-1 표
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=55", "-XX:InitialRAMPercentage=25", \
  "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=48m", "-XX:TieredStopAtLevel=1", \
  "-Xss512k", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", \
  "-XX:SharedArchiveFile=app.jsa", "-Xlog:cds=off", "-Xlog:cds+dynamic=off", \
  "-jar", "app.jar"]
