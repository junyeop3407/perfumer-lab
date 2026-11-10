### [심각도: 높음] Jackson 체크 예외 처리 누락
- **파일명 및 줄 번호:** `BlendsController.java` (74행, 169행)
- **문제 이유:** `tools.jackson.databind.json.JsonMapper`의 `readValue`와 `writeValueAsString` 메서드는 체크 예외(Checked Exception)인 `IOException` 또는 `JsonProcessingException`을 던집니다. 하지만 현재 코드에는 `try-catch` 블록이나 메서드 `throws` 선언이 없습니다. 이로 인해 컴파일 에러가 발생하거나, 런타임에 예외 발생 시 전파 경로에서 적절히 처리되지 못해 서버 장애로 이어질 수 있습니다. 특히 `RowMapper` 람다 내부(74행)에서는 `SQLException` 외의 체크 예외를 직접 던질 수 없습니다.
- **수정 제안:** 해당 호출부를 `try-catch`로 감싸고, 예외 발생 시 `UncheckedIOException`이나 런타임 예외로 전환하여 던지도록 수정해야 합니다.

### [심각도: 중간] RowMapper 내 ResultSet Null 처리 미흡
- **파일명 및 줄 번호:** `BlendsController.java` (74행)
- **문제 이유:** `rs.getString("notes")`의 결과가 `null`인 경우 `JSON.readValue`에 `null`이 전달되어 `NullPointerException`이 발생할 수 있습니다. `V3` 마이그레이션에서 `error_reports.notes` 컬럼이 `null` 허용으로 설계된 점을 고려할 때, `app.blends.notes` 또한 데이터 상태에 따라 `null`이 반환될 가능성이 있다면 방어 코드가 필요합니다.
- **수정 제안:** `rs.getString("notes")` 결과가 `null`인지 먼저 확인하거나, `Optional` 등을 사용하여 `null`일 경우 빈 리스트(`new ArrayList<>()`)를 반환하도록 처리하십시오.

### [심각도: 낮음] Key 길이 제한 불일치
- **파일명 및 줄 번호:** `ReportsController.java` (44행) vs `BlendsController.java` (147행)
- **문제 이유:** `ReportsController`에서는 `MAX_KEY`를 200으로 설정하여 `perfumeKey`나 `noteKey`를 검사하지만, 공유 검사 로직인 `BlendsController.checkNotesShape` 내부에서는 `Library.key(n.noteKey(), 100)`을 호출하여 길이를 100으로 제한하고 있습니다. 이로 인해 단일 필드 신고 시에는 200자까지 허용되지만, `notes` 리스트 내의 key는 100자까지만 허용되는 불일치가 발생합니다.
- **수정 제안:** 전체 시스템의 Key 길이 정책에 맞춰 제한 값을 통일하거나, `checkNotesShape` 호출 시 최대 길이를 인자로 전달할 수 있도록 리팩토링하십시오.

### [심각도: 낮음] JSON 직렬화 시 순서 의존성 (추정)
- **파일명 및 줄 번호:** `BlendsController.java` (162-167행)
- **문제 이유:** `LinkedHashMap`을 사용하여 JSON 키 순서(`note_key`, `layer`, `strength`)를 고정하고 있습니다. 이는 DB의 `check` 제약 조건(`app.valid_blend_notes`)이 특정 키 순서에 의존하고 있을 가능성을 시사합니다. 만약 DB 함수가 키 순서에 민감하게 작성되어 있다면, 향후 필드 추가나 변경 시 취약점이 될 수 있습니다.
- **수정 제안:** DB의 `valid_blend_notes` 제약 조건이 키의 존재 여부와 값의 유효성만 검사하도록 설계되어 있는지 확인하고, 코드와 DB 간의 결합도를 낮추는 것을 권장합니다.


## Claude 판단
- 신고 당시 조합 칸(기준 HEAD=10b0a82, 커밋 전 변경분) Gemini 4건 모두 해당 없음: ① Jackson 3(`tools.jackson`)은 unchecked 예외라 컴파일·테스트 통과 ② `app.blends.notes`는 V1에서 NOT NULL ③ 노트 key 100자는 DB `app.valid_blend_notes`와 같은 규칙(조합과 같은 검사가 사장 결정) ④ jsonb는 키 순서와 상관없음. QA팀은 `review.md` "신고 당시 조합 칸".
