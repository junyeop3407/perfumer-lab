제시된 변경 사항을 리뷰한 결과, 여러 곳에서 **컴파일 오류**가 발생할 것으로 예상되며 일부 예외 처리 및 성능 관련 주의사항이 발견되었습니다.

### [심각도: 높음] 컴파일 오류 및 예외 처리 미비
1. **`apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/library/BlendsController.java`**
   - **줄 번호:** 75, 143 (추정)
   - **문제 이유:** Jackson(`tools.jackson`)의 `readValue`와 `writeValueAsString`은 체크드 예외(Checked Exception)인 `JsonProcessingException`(또는 `IOException`)을 던집니다. 하지만 `rows` RowMapper 람다식과 `check` 메서드 내부에서 이를 `try-catch`로 처리하지 않았으며, 메서드 시그니처에 `throws` 선언도 없습니다. 이로 인해 컴파일이 되지 않습니다.
   - **수정 제안:** 해당 호출 부분을 `try-catch` 블록으로 감싸 `UncheckedIOException` 등으로 전환하거나, 적절한 `ApiException`을 던지도록 수정해야 합니다.

2. **`apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/catalog/CatalogKeys.java`**
   - **줄 번호:** 46, 56 (추정)
   - **문제 이유:** `JsonNode` 클래스에는 `asString()`이라는 메서드가 없습니다. 문자열 값을 가져오려면 `asText()`를 사용해야 합니다.
   - **수정 제안:** `asString("")`을 `asText("")`로, `asString()`을 `asText()`로 변경하십시오.

### [심각도: 중간] 성능 및 자원 관리
3. **`apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/library/Library.java`**
   - **줄 번호:** 43 (추정)
   - **문제 이유:** `pg_advisory_xact_lock`을 위해 `hashtext(user.toString())`을 사용하고 있습니다. `hashtext`는 32비트 정수 해시를 반환하므로, 사용자 수가 많아질 경우 서로 다른 UUID임에도 해시 충돌이 발생할 수 있습니다. 충돌 발생 시, 논리적으로는 문제가 없으나 **서로 다른 사용자의 요청이 동일한 락을 기다리게 되어** 불필요한 성능 저하가 발생할 수 있습니다.
   - **수정 제안:** `pg_advisory_xact_lock(bigint)` 시그니처를 사용하여 UUID의 상위/하위 64비트를 직접 키로 사용하거나, 해시 충돌 가능성을 인지하고 모니터링하십시오.

### [심각도: 낮음] 예외 처리 누락 (추정)
4. **`apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/library/FeedbackController.java`**
   - **줄 번호:** 101-105 (추정)
   - **문제 이유:** `sqlState(e, "23503")`(외래 키 위반) 발생 시 `NOT_FOUND`를 던지는 로직은 `blend_id` 위반을 타겟팅하고 있습니다. 하지만 만약 `perfume_key`가 (이론적으로) DB 외래 키로 걸려 있고 카탈로그 검증을 통과한 직후 DB에서 사라지는 극히 드문 상황이 발생한다면, 원인과 무관하게 `NOT_FOUND`가 발생할 수 있습니다.
   - **수정 제안:** 현재 비즈니스 로직상 `perfume_key` 위반 가능성이 낮다면 수용 가능하지만, 정확한 진단을 위해 제약 조건 이름(Constraint Name)을 함께 확인하는 것이 더 안전합니다.

5. **`apps/scent-studio/backend/src/main/java/com/perfumerlab/scentstudio/library/BlendsController.java`**
   - **줄 번호:** 127 (추정)
   - **문제 이유:** `LAYERS.contains(n.layer())` 호출 시 `n.layer()`가 `null`이면 `Set.of`로 생성된 Set은 `NullPointerException`을 던집니다. 앞선 조건문에서 `n.layer() == null`을 체크하고는 있으나, 논리 연산자 순서에 주의해야 합니다.
   - **수정 제안:** 현재 코드는 `n.layer() == null || !LAYERS.contains(n.layer())` 순서이므로 단락 평가(Short-circuit) 덕분에 안전하지만, 가독성을 위해 명확히 유지하십시오. (확인 결과 현재 순서는 안전함)

리뷰 요약: **`BlendsController`와 `CatalogKeys`의 컴파일 오류를 우선적으로 해결해야 합니다.** 그 외 로직은 설계 의도에 맞게 견고하게 구현된 것으로 보입니다.

## Claude 판단 (묶음 5, 2026-11-08)
| # | Gemini 지적 | 판단 | 처리 |
|---|---|---|---|
| 1 | (높음) BlendsController `readValue`·`writeValueAsString`이 체크 예외라 컴파일 안 됨 | 틀림. 이 서버는 Jackson 3(`tools.jackson`)이라 예외가 unchecked. 실제로 컴파일·테스트 통과 | 반영 안 함 |
| 2 | (높음) CatalogKeys `asString()` 없음 → `asText()` | 틀림. Jackson 3에서 `asText`가 `asString`으로 바뀜. 묶음 4 코드이고 통과 중 | 반영 안 함 |
| 3 | (중간) `hashtext` 32비트 해시 겹치면 다른 사용자끼리 잠깐 기다림 | 맞지만 영향 작음: 잠금은 수 ms, 겹쳐도 결과는 맞음(본인도 지적에서 인정). 종류별 번호(5101~5103)로 이미 나눔 | 반영 안 함(NOTES에 기록) |
| 4 | (낮음) 외래 키 위반을 모두 "조합 없음 404"로 | 부분적으로 맞음: 탈퇴 직후 토큰(세션 보관 30초)이 blendId를 보내면 user_id 외래 키 위반도 404. 탈퇴 직후 토큰 문제는 묶음 8(삭제 때 세션 보관 바로 지우기, 남은 것 #2·#4)에서 함께 | 미룸(묶음 8) |
| 5 | (낮음) `Set.of`에 null을 물으면 예외 — 지금 순서는 안전 | 맞음, 이미 테스트가 잡아 고친 것(null 먼저 확인) | 할 일 없음 |
