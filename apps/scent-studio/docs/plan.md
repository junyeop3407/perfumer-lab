# scent-studio 4단계 계획 (E안: Expo 앱 하나로 웹·안드로이드·아이폰)

> 2026-10-31 계획 모드에서 세워 사장 승인. 단계가 끝날 때마다 상태 칸과 HANDOFF를 갱신한다. 이전(Kotlin) 계획의 M0·M1·M2(Java)는 끝났고 결과는 그대로 쓴다.

## 왜 이 계획인가
사장이 2026-10-31 "안드로이드 전용"을 "웹 + 안드로이드 + 아이폰"으로 바꾸고 설계 E안(Expo, React Native)을 골랐다. 기획서·설계서·테스트 목록(41개)은 재승인 완료, Kotlin 앱은 지웠다. 이제 같은 화면 코드 하나로 웹(폰·PC 브라우저)과 안드로이드 앱(Play 내부 테스트)을 만들고, 나중에 아이폰 앱스토어에도 그대로 올릴 수 있게 한다.

이미 있는 것(그대로 씀): `pipeline/`(데이터 정리·Python 참조 엔진·평가, pytest 12/12), `tests/golden/recommend_cases.json`, `tests/fixtures/test_catalog.json.gz`, Java 엔진 `engine/`(11/11, TS로 옮길 때 기준).

## 정한 것 (이번 계획 모드에서)
- 앱 ID: `com.perfumerlab.scentstudio` (안드로이드·아이폰 공통)
- 앱 이름: **Perfumer-LAB(향수 공방소)**. 홈 화면 아이콘 아래처럼 자리가 좁은 곳은 짧은 이름 "Perfumer-LAB"
- Expo는 최신 안정 SDK, TypeScript. 정확한 버전은 단계 1에서 `NOTES.md`에 적는다

## 단계 (각 단계 끝 = 테스트 통과 → plan.md·HANDOFF 갱신 → 커밋·푸시)
| # | 팀 | 할 일 | 끝났다는 기준 |
|---|---|---|---|
| 1 | 프론트 + QA | **Expo 앱 뼈대 + 승인 목록 테스트 코드(3단계 남은 일).** `app/`에 Expo(Expo Router, NativeWind, TypeScript) 생성, 탭 4개 빈 화면, 화면 요소 이름표 `src/ui/testIds.ts`(예전 Kotlin `Tags.kt` 이름표를 그대로 옮김 — git 0a2788a), 가짜 카탈로그를 읽는 테스트 모드. Playwright 설정(웹 내보내기 → 정적 서버, 폰 360·412 / PC 1280 프로젝트, 가짜 서버는 `page.route`, 로그인 흉내). 승인 목록 41개(A~I)를 테스트 코드로 | 뼈대가 웹으로 열림, 41개 테스트가 **실행되고**(화면이 없어 실패하는 게 정상) 이름표·검사 내용이 tests.md와 1:1 |
| 2 | 백엔드 | **TypeScript 엔진** `app/src/engine/`: `RecommendationEngine.java`·`EngineParams`·`CatalogModel`을 8절 명세대로 옮김. Java 골든·성질 테스트를 Vitest로 옮김 | Vitest 골든(순위 같음, 점수 차 ≤1e-9)·성질 통과 → Java `engine/`·Gradle 루트 삭제, `run.sh`에서 Gradle 대신 Vitest |
| ★ | 백엔드 | **엔진 마무리 사장 결정**: 레몬 "강" 문제(프로필 방식 × 강 값 비교표, `evaluate.py` 방식으로 재측정) → 블라인드 비교 → 눈으로 평가 (HANDOFF "다음 세션에서 할 일" 1·2) | 사장 OK, 기준(재현 15, 블라인드 6/10, 눈 3.5) 통과 |
| 3 | 백엔드 | **DB 구축** (design.md 12·12-1절, 저장할 데이터 목록은 2026-10-30 사장 OK). ① 표 만들기: Flyway 마이그레이션 `backend/src/main/resources/db/migration/V1__init.sql` — `app` 스키마에 `user_preferences`·`blends`·`favorites`·`tasting_feedback`·`events` 5개 표, 필수값·범위 검사(만족도 1~5, 메모 500자, 이름 40자, 층·강도 값), `user_id` 인덱스, 찜은 (user_id, perfume_key) 중복 불가 ② 막기: 모든 표 RLS 켜고 정책 없음(앱의 공개 키로는 아무것도 못 읽음), `app` 스키마는 공개 API에 노출 안 함, 서버 전용 DB 계정 ③ 테스트 DB: 내장 Postgres(zonky, Docker 불필요)로 마이그레이션이 처음부터 끝까지 돌고, 검사 규칙이 잘못된 값을 막는지 확인 ④ 실제 DB: 사장이 Supabase 프로젝트를 만들고(무료, 서울 지역) 접속 정보를 `.env`에만 넣음(`.env.example`에 이름만) → 서버를 처음 켜면 Flyway가 표를 만듦 → 구글 로그인 켜기 ⑤ 운영: 7일 쉬면 일시 정지되므로 하루 한 번 깨우는 GitHub Actions 예약 작업, 무료 요금제 백업 한계를 NOTES에 기록 | 내장 Postgres로 마이그레이션·검사 테스트 통과. 실제 Supabase에 표 5개가 생기고 공개 키로 읽기가 막힌 것을 확인 |
| 4 | 백엔드 | Spring 서버 `backend/`(Spring Boot 4.1, Java 21): 보안(JWT/JWKS, 사용자 id는 토큰에서만), 사용자 API(11절: 설정·조합·찜·피드백), 계정 삭제(한 번에 내 데이터 전부 + Supabase 계정, 사용 기록은 익명으로), 사용 기록 받기, 카탈로그 배포 + 서버 테스트(토큰 없으면 401, 남의 데이터 못 읽음, 잘못된 입력 400, 삭제 후 데이터 없음) | `./gradlew test` 통과, `run.sh`에 추가 |
| ★ | 디자인 | `docs/DESIGN.md`(design.md 3절 토큰 → `tailwind.config.js`) + Playwright 시안 캡처(라이트·다크, 폰·PC) | 사장 OK |
| 5 | 프론트 | 화면: 카탈로그 불러오기 → 새 조합 흐름 S02~S06 → S07·S08 → 로그인(Supabase, 구글, 방법 목록 구조)·보관함·마이 → PC 넓은 배치(왼쪽 메뉴, 목록·상세 나란히, 옆 패널) | 승인 목록 41/41 |
| 6 | 프론트 | 사용 기록, 오류·빈 상태, 웹 오프라인(서비스 워커)·홈 화면 추가(매니페스트, 이름 Perfumer-LAB), EAS 안드로이드 빌드 설정, 개인정보·계정 삭제 안내 페이지 | 승인 목록 그대로 통과 + 폰 앱 확인(6단계 전) |

- 단계 1의 41개 테스트는 화면이 없어 실패하는 상태라 **단계 1 끝에는 커밋하지 않는다**(규칙). 단계 2까지 끝나도 승인 목록은 실패 중이므로, 단계 1~5 사이에는 승인 테스트를 `run.sh`에 넣지 않고 별도 명령(`npx playwright test`)으로 돌리고, 단계 5에서 41/41이 되면 `run.sh`에 넣는다 — 이 방식이 실패 커밋 규칙을 피하는 우회가 아니도록 HANDOFF·NOTES에 "승인 목록 N/41"을 매 단계 적는다. (예전 Kotlin 때와 같은 방식: 그때는 사장 결정으로 작업 브랜치에 커밋)
- 단계 1~5는 작업 브랜치 `wip/scent-studio-stage4`에서 하고, 41/41 + 5단계 리뷰 뒤 main에 합친다.
- 외부 계정(Supabase 프로젝트, Expo 계정, Play 개발자 계정)이 필요한 순간에는 멈추고 사장에게 `!` 한 줄 또는 만드는 방법을 부탁한다. 키는 대화창에 붙이지 않게 안내한다.

## 고칠·만들 주요 파일
- 새로: `app/`(Expo: `app/`(라우트), `src/engine/`, `src/ui/testIds.ts`, `src/strings.ko.ts`, `tailwind.config.js`, `app.json`(이름·ID), `eas.json`, `playwright.config.ts`, `vitest.config.ts`, `tests/acceptance/*.spec.ts`, `tests/unit/*.test.ts`), `backend/`, `docs/DESIGN.md`
- 고침: `tests/run.sh`, `docs/plan.md`(이 계획으로 교체), `docs/spec.md`(앱 이름·ID), `HANDOFF.md`, `docs/NOTES.md`
- 지움(단계 2 뒤): `engine/`, `settings.gradle.kts`, 루트 `build.gradle.kts`·`gradle/`·`gradlew*`·`gradle.properties`(서버는 `backend/` 안에 자체 Gradle)
- 재사용: 이름표 `git show 0a2788a:apps/scent-studio/app/src/main/java/com/perfumerlab/scentstudio/ui/Tags.kt`, 가짜 서버 동작 `.../acceptance/harness/FakeUserServer.kt`, Java 엔진·테스트(옮길 원본), `pipeline/build_catalog.py --fixture`, `pipeline/make_golden.py`

## 확인 방법
- 매 단계: `bash tests/run.sh`(pytest + Vitest + 서버) 통과, 승인 목록은 `cd app && npx playwright test`로 N/41 기록
- 단계 2: TS 엔진 골든이 Python이 만든 `recommend_cases.json`과 순위·점수 일치
- 단계 3: 내장 Postgres 마이그레이션 테스트 통과 + 실제 Supabase에서 표 5개 확인, 공개 키로 `app` 표 읽기 시도가 막힘
- 단계 5 끝: 승인 목록 41/41, 화면 캡처로 폰·PC·다크·글자 200% 확인
- 그다음 5단계(별도 에이전트 리뷰 + Gemini 교차 리뷰) → 6단계 폰 앱 확인·사장 직접 확인

## 이전 계획에서 정한 것 (그대로 유효)
- 원본에 실재하지 않는 노트 이름 30개(각 58~80개 향수에 고르게 섞임)가 있어 버린다 (`pipeline/manual/junk_notes.csv`)
- 향수 key는 원본 Number가 겹쳐서 상품 페이지 주소로 만든다
- 같은 노트는 한 조합에 한 번만 (Python·Java가 똑같이 계산되게)
- 파이프라인과 엔진 검증은 **Python 기본 기능만** 쓴다(pandas·Jupyter 설치 없음). 노트북 대신 `pipeline/evaluate.py`가 `docs/engine_report.md`를 만든다
- 서버는 Spring Boot **4.1**(최신 안정판). design.md 1절에 반영

## 진행 상태
| 단계 | 상태 |
|---|---|
| 1 | ✅ 2026-10-31 뼈대 + 승인 테스트 41개 작성, 승인 목록 1/41 (화면 전이라 정상) |
| 2 | ✅ 2026-10-31 TS 엔진 Vitest 36/36, Java 엔진·Gradle 삭제 |
| ★엔진 | 🔄 레몬 문제 v3 적용(2026-10-31). 다음: 사장 검토 + 블라인드 비교 → 눈으로 평가 |
| 3 | |
| 4 | |
| ★디자인 | |
| 5 | |
| 6 | |
