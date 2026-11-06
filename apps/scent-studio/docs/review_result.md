리뷰를 완료했습니다. 주요 보안 및 잠재적 버그 사항을 중심으로 정리했습니다.

### [심각도: 중간] `apps/scent-studio/backend/src/main/resources/db/migration/V1__init.sql` (92-94행)
- **문제 이유**: Postgres의 POSIX 정규식(`~`, `regexp_replace`)은 기본적으로 `\uXXXX`와 같은 유니코드 이스케이프 시퀀스를 지원하지 않습니다. 현재 작성된 방식은 `\`와 `u` 문자 등의 리터럴로 해석되어 의도한 유니코드 범위(제어 문자, 보이지 않는 공백 등)를 정확히 필터링하지 못할 가능성이 큽니다.
- **수정 제안**: 유니코드 문자를 직접 파일에 넣는 대신 `chr()` 함수를 사용하여 결합하거나, 정규식 내에서 `\xXXXX` 형태의 16진수 이스케이프를 사용해야 합니다(단, `E'...'` 문법과 함께 사용해야 안전함).
    ```sql
    -- 예시 (E'...' 문법 사용)
    name !~ E'[\\x01-\\x1f\\x7f-\\x9f]'
    ```

### [심각도: 낮음] `apps/scent-studio/backend/src/main/resources/db/migration/V1__init.sql` (113행)
- **문제 이유**: `rank`는 SQL 표준 및 Postgres의 예약어(Window Function)입니다. 컬럼명으로 사용하는 것은 가능하나, 쿼리 작성 시 따옴표(`"rank"`)로 감싸지 않으면 구문 오류나 모호함이 발생할 위험이 있습니다.
- **수정 제안**: 가능한 경우 `feedback_rank` 또는 `engine_rank`와 같이 예약어와 겹치지 않는 이름으로 변경하는 것을 권장합니다.

### [심각도: 낮음] `apps/scent-studio/tests/run.sh` (29-30행)
- **문제 이유**: `./gradlew test` 실행 시 Java 21 설치 여부를 사전에 검사하지 않습니다. 환경에 Java가 없거나 버전이 낮은 경우 Gradle이 불친절한 오류 메시지를 내며 중단될 수 있습니다.
- **수정 제안**: 스크립트 상단에서 `java -version`을 체크하여 적절한 안내 메시지를 출력하는 로직을 추가하면 개발 환경 세팅 시 혼란을 줄일 수 있습니다.

### [기타 확인 사항]
- **성능**: `app.events` 표의 `purge_old_events` 함수가 `received_at` 인덱스를 사용하므로 대량 데이터 삭제 시에도 성능 문제는 없을 것으로 보입니다.
- **보안**: `alter default privileges revoke execute on functions from public;` 설정과 RLS 정책이 촘촘하게 설계되어 보안적으로 우수합니다.
- **예외 처리**: `valid_blend_notes` 함수에서 JSON 구조와 키 개수, 타입을 엄격히 검사하고 있어 데이터 무결성이 잘 보장됩니다.

전반적으로 DB 설계와 테스트 코드가 매우 꼼꼼하게 작성되어 있습니다. 위 정규식 이슈만 해결하면 안정적인 운영이 가능할 것으로 판단됩니다.

## Claude 판단 (2026-11-06)
- **[중간] 정규식 `\u` 미지원 → 틀림, 반영 안 함.** Postgres 정규식(ARE)은 `\uwxyz`(16진 4자리) 표기를 지원하고, 이 파일은 standard_conforming_strings가 켜진 일반 문자열이라 백슬래시가 그대로 정규식에 간다. 증거: `DbReviewFixesTest.rejectsInvisibleOnlyBlendNames`(U+0009·000A·00A0·200B·2060·3000·FEFF)가 고치기 전 실패, 고친 뒤 통과.
- **[낮음] `rank` 예약어 → 틀림, 반영 안 함.** Postgres에서 rank는 예약 키워드가 아니고 윈도 함수 이름이다. 따옴표 없이 만들기·넣기·읽기가 테스트에서 된다.
- **[낮음] run.sh Java 확인 → 맞음, 반영.** Java 21 미만이면 설치 명령을 알려 주고 멈춘다.
- 전체 표는 `docs/review.md`.
