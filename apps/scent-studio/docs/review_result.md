리뷰 결과, 보안 및 성능 측면에서 개선이 필요한 항목이 발견되었습니다.

### 1. [심각도: 높음] `ClientIp.java`:35 - 공격자에 의한 DNS 조회 유발 (DoS 위험)

*   **파일명 및 줄 번호**: `apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/limit/ClientIp.java` (35줄)
*   **문제 이유**: `isIp` 메서드에서 IPv6 형식을 검증하기 위해 `InetAddress.getByName(v)`를 호출하고 있습니다. 주석에는 "DNS 풀이는 절대 하지 않는다"고 적혀 있으나, `InetAddress.getByName`은 입력값이 유효한 IP 리터럴이 아닐 경우 시스템의 DNS 리졸버를 호출합니다. 공격자가 `X-Forwarded-For` 헤더에 `malicious-slow-dns.com:80`과 같이 콜론이 포함된 도메인을 넣으면, 서버는 외부 DNS 응답을 기다리며 스레드를 점유하게 됩니다. 설정된 톰캣 스레드가 16개로 적기 때문에, 소수의 요청만으로도 서버 전체를 먹통으로 만드는 DoS 공격이 가능합니다.
*   **수정 제안**: `InetAddress.getByName`을 사용하지 말고, 정규표현식만으로 IPv6 형식을 검증하거나 `InetAddress.getByAddress`와 같이 DNS 조회를 유발하지 않는 대안을 사용하세요. (예: `v.split(":").length`와 16진수 여부 확인 등)

### 2. [심각도: 낮음] `SessionChecker.java`:46 - 캐시 정리 시 성능 저하

*   **파일명 및 줄 번호**: `apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/auth/SessionChecker.java` (46줄)
*   **문제 이유**: 캐시가 가득 찼을 때(`MAX_ENTRIES`인 10,000개 도달 시) `removeIf`를 호출하여 전체 항목을 순회하며 만료된 데이터를 지웁니다. 이 작업은 사용자의 요청 처리 경로(`alive` 메서드) 내에서 동기적으로 일어나므로, 캐시가 꽉 찬 상태에서 요청이 몰리면 매 요청마다 $O(N)$ 연산이 발생하여 응답 시간이 급증할 수 있습니다.
*   **수정 제안**: `removeIf` 대신 `LinkedHashMap`의 `removeEldestEntry`를 활용하여 가장 오래된 항목 하나만 지우는 방식을 사용하거나, 별도의 백그라운드 스케줄러를 통해 주기적으로 만료된 항목을 정리하도록 변경하세요.

### 3. [심각도: 중간] `RateLimiter.java`:71-77 - LRU 맵의 스레드 안전성 관련 추정

*   **파일명 및 줄 번호**: `apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/limit/RateLimiter.java` (71, 77줄)
*   **문제 이유**: `buckets` 맵은 `synchronized` 블록으로 보호되고 있지만, 사용된 `LinkedHashMap`은 `accessOrder`가 `true`일 경우 `get` 호출만으로도 구조적 변경(항목 이동)이 일어납니다. 현재 코드에서는 `computeIfAbsent`만 사용하고 있어 큰 문제는 없으나, `blockedUntil.get(ipKey)`(97줄)의 경우 `get` 호출 시 `synchronized` 블록 내부에서 맵 구조가 변경됩니다. 50,000개의 항목이 있는 맵에서 락을 잡고 구조를 변경하는 작업이 잦아지면 락 경합(Lock Contention)으로 인한 성능 저하가 발생할 수 있습니다.
*   **수정 제안**: `MAX_KEYS`가 50,000개로 작지 않으므로, 성능이 중요하다면 `Caffeine` 같은 전문 캐시 라이브러리를 고려하거나(제약 조건상 어렵다면), `accessOrder`를 `false`(기본값)로 설정하여 `get` 시의 구조 변경을 막고 단순 삽입 순서로만 관리하는 것을 검토하세요.

---
**총평**: 전반적으로 Supabase JWT 검증과 요청 제한 로직이 견고하게 구현되었습니다. 특히 401 에러 원인을 숨기고 IP별로 차단하는 보안 설계가 우수합니다. 위 지적된 DNS 관련 DoS 취약점만 우선적으로 해결하면 안전하게 배포 가능할 것으로 보입니다.

## Claude 판단 (묶음 3, 2026-11-07)
- **[높음] DNS 풀이 → 일부 맞음, 반영.** JDK 21 `InetAddress.getAllByName` 소스를 확인했다: 첫 글자가 16진수나 `:`이면 IP 글자로만 읽고(`:`가 있는데 IP가 아니면 예외, 풀이 없음), 아니면 이름 풀이로 넘어간다. 지금 규칙(`[0-9A-Fa-f:.]`)은 `.`으로 시작하는 값(`.cafe:80`)을 통과시켜 실제로 풀이가 일어났다(테스트 JVM 이름 풀이 기록으로 재현). 첫 글자를 16진수·`:`로 제한했다. Gemini 예시(`malicious-slow-dns.com:80`)는 글자 검사에서 이미 막혔다.
- **[낮음] 세션 보관 removeIf → 반영 안 함.** 가득 찼을 때만 돌고, 정리 뒤에도 가득하면 비우므로 요청마다 O(N)이 계속되지 않는다.
- **[중간] 자물쇠 경합 → 반영 안 함(지금 규모).** 자물쇠 안 작업은 짧고 CPU 0.1·스레드 16이다. QA 지적으로 사용자·IP 바구니 지도는 나눴다.
- 전체 표는 `docs/review.md` "묶음 3". (묶음 2 Gemini 원문은 "문제 없음", 커밋 5ef7314의 이 파일)
