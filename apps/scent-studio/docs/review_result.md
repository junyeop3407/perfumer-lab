리뷰 결과를 보고합니다. 2026년 11월 기준 기술 스택(Spring Boot 4.1 등)과 프로젝트 제약 조건(Render 무료 티어: 512MB RAM)을 고려하여 분석했습니다.

### 1. JVM 메모리 및 성능 설정 위험
*   **[심각도: 중간]** `apps/scent-studio/backend/Dockerfile` (30~31행)
*   **문제 이유**: `MaxMetaspaceSize=128m`와 `ReservedCodeCacheSize=48m` 설정이 Spring Boot 애플리케이션(Springdoc, Security, OAuth2, Validation 포함)치고는 상당히 타이트합니다. 특히 `ReservedCodeCacheSize`가 가득 차면 JIT 컴파일이 중단되어 애플리케이션 성능이 심각하게 저하(Interpreted mode로만 동작)될 수 있습니다. 512MB 환경이라 하더라도 Spring의 복잡도를 고려할 때 위험한 수치입니다.
*   **수정 제안**: 최소한 `ReservedCodeCacheSize=64m` 이상을 권장하며, 실제 운영 환경에서 `Metaspace` 사용량을 모니터링하여 160m 정도로 여유를 두는 것이 안전합니다.

### 2. Gradle Task 클래스패스 구성 오류 (추정)
*   **[심각도: 중간]** `apps/scent-studio/backend/build.gradle.kts` (28행)
*   **문제 이유**: `dbVersion` 등 DB 관련 커스텀 태스크의 클래스패스를 `ops.runtimeClasspath + sourceSets["main"].output`에서 `ops.runtimeClasspath + files(sourceSets["main"].output.resourcesDir)`로 변경했습니다. 만약 `DbOps` 클래스나 그 의존성이 `src/main/java`에 있는 클래스를 단 하나라도 참조하고 있다면 `NoClassDefFoundError`가 발생합니다. 단순히 리소스(SQL 파일 등)만 필요해서 변경한 것인지 확인이 필요합니다.
*   **수정 제안**: `main` 소스셋의 컴파일된 클래스까지 포함하도록 `sourceSets["main"].output`을 그대로 유지하거나, 리소스만 필요하다면 의도를 명확히 주석으로 남겨야 합니다.

### 3. 미인증 엔드포인트의 메모리 할당 공격 위험
*   **[심각도: 낮음]** `apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/web/DebugController.java` (56행)
*   **문제 이유**: `allowed` 메서드에서 `given.getBytes(StandardCharsets.UTF_8)`를 호출하기 전에 `given` 헤더의 길이를 체크하지 않습니다. `SecurityConfig`에서 이 주소들은 `permitAll()` 상태이므로, 악의적인 사용자가 매우 큰 `X-Debug-Token` 헤더를 보낼 경우 `getBytes()` 호출 시 heap 메모리 할당 부하를 유도할 수 있습니다. (Tomcat의 기본 `max-http-header-size`인 8KB로 1차 방어는 되지만 애플리케이션 레벨의 방어가 없습니다.)
*   **수정 제안**: `given` 문자열의 길이를 먼저 확인(예: `given.length() > 128`이면 즉시 탈락)하는 로직을 추가하십시오.

### 4. 시스템 파일 읽기 예외 처리 효율성
*   **[심각도: 낮음]** `apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/web/DebugController.java` (95, 125행)
*   **문제 이유**: `/proc/self/status` 및 `/sys/fs/cgroup` 파일을 읽을 때 `Exception`을 catch하여 무시하고 있습니다. 리눅스가 아닌 환경(개발자의 로컬 윈도우/맥)에서 해당 API가 호출될 때마다 매번 `NoSuchFileException`이 발생하고 이를 catch하는 오버헤드가 발생합니다.
*   **수정 제안**: `Files.exists()`를 먼저 체크하여 불필요한 예외 발생을 방지하는 것이 성능 및 디버깅 측면에서 유리합니다.

### 5. 측정 스크립트의 JSON 파싱 취약성
*   **[심각도: 낮음]** `apps/scent-studio/backend/scripts/render_measure.sh` (13행)
*   **문제 이유**: `pick()` 함수가 `grep`과 `cut`을 이용해 JSON을 파싱합니다. 만약 응답 JSON에 공백이 섞이거나 구조가 조금만 바뀌어도 잘못된 값을 가져올 수 있습니다.
*   **수정 제안**: 가능하다면 `jq` 도구를 사용하거나, `sed`를 이용해 좀 더 견고하게 파싱하도록 개선이 필요합니다.

그 외의 부분(CDS 적용, SerialGC 사용, 비밀값 노출 방지 테스트 등)은 제한된 자원 환경에 맞춰 매우 세심하게 설계되었습니다. 특히 `DebugController`에서 JVM 인자 출력 시 `-XX`와 `-Xss`만 필터링하여 `-D`로 전달될 수 있는 비밀값 누출을 막은 점은 훌륭한 보안 처리입니다.

## Claude 판단 (묶음 1, 2026-11-06)
- **[중간] Metaspace 128m·CodeCache 48m이 빠듯함 → 확인 필요(실측으로 판단).** `-XX:TieredStopAtLevel=1`(C1만)에서는 컴파일 코드가 작아 48m이 C1 전용 기본값과 같다. 이 컴퓨터에서 쉬는 상태 non-heap 21MB. 측정 주소에 `metaspaceUsedMB`·`codeCacheUsedMB`를 더해 Render 10분 부하에서 보고, 상한의 80%를 넘으면 늘린다.
- **[중간] ops 작업 classpath → 맞음(의도 설명 부족), 반영.** DbOps는 main 클래스를 쓰지 않고 마이그레이션 SQL만 쓴다. 주석을 달았다.
- **[낮음] 큰 X-Debug-Token 헤더 → 반영.** Tomcat 8KB 한도가 있지만 256자 넘으면 바로 거절.
- **[낮음] /proc 파일 예외 비용 → 반영 안 함.** 토큰으로 잠긴 측정 주소에서만, 리눅스(Render)에서는 예외가 나지 않는다.
- **[낮음] 스크립트 JSON 파싱 → 반영 안 함.** 응답은 이 서버가 만드는 공백 없는 JSON이라 지금 방식으로 충분하고, jq를 따로 깔지 않는다.
- 전체 표는 `docs/review.md` "묶음 1". (단계 3 DB 리뷰의 Gemini 원문은 커밋 2391672의 이 파일)
