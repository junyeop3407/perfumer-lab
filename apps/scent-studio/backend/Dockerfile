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

# 2) 실행: JRE만. CDS(클래스 미리 읽어 둔 파일)를 같은 JVM·같은 옵션으로 만들어 시작 시간을 줄인다
FROM eclipse-temurin:21-jre-noble
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
# 실행 파일은 root 소유·읽기 전용: 서버가 뚫려도 app.jar·lib·app.jsa를 고쳐 쓸 수 없게(리뷰 묶음 1 QA-5)
COPY --from=build /app /app

# 메모리: 힙은 컨테이너의 55%(512MB면 약 280MB), 나머지는 server_design.md 6-1 표.
# glibc 메모리 구역을 2개로 줄이고(MALLOC_ARENA_MAX) direct 버퍼 상한을 둬서 컨테이너 OOM kill을 막는다(QA-4)
ENV MALLOC_ARENA_MAX=2
ENV JAVA_OPTS_BASE="-XX:MaxRAMPercentage=55 -XX:InitialRAMPercentage=25 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:TieredStopAtLevel=1 -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# CDS 학습 실행: 실행 때와 같은 옵션으로, 설정까지만 읽고 바로 끝낸다(DB에 붙지 않음, 가짜 값이라 비밀값 없음).
# 그다음 -Xshare:on으로 한 번 더 띄워, 아카이브가 안 맞으면 조용히 넘어가지 않고 빌드가 실패하게 한다(QA-3)
RUN export DB_URL=jdbc:postgresql://127.0.0.1:1/cds DB_SERVER_PASSWORD=none \
 && java $JAVA_OPTS_BASE -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -jar app.jar > /dev/null \
 && java $JAVA_OPTS_BASE -Xshare:on -XX:SharedArchiveFile=app.jsa -Dspring.context.exit=onRefresh -jar app.jar > /dev/null \
 && chmod -R a+rX,go-w /app

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_BASE -XX:SharedArchiveFile=app.jsa -Xlog:cds=off -Xlog:cds+dynamic=off -jar app.jar"]
