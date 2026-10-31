# scent-studio
현재 단계: 4 (구현 — docs/plan.md ★엔진: 레몬 문제 해결(v3) 끝, 블라인드 비교 답 대기)
한 줄 설명: 탑·미들·베이스 노트를 골라 조합하면, 어코드 비중이 가장 비슷한 실제 향수를 추천하는 앱. 웹·안드로이드·아이폰 공통 (Expo/React Native 앱, Spring 서버, 데이터 작업은 Python)
실행 방법: 아직 화면 없음 (뼈대만: `cd app && npx expo start --web`)
테스트 실행: `bash tests/run.sh`
리뷰 기준: a92de1c
결과: 파이프라인 12/12 · TS 엔진(Vitest) 36/36 통과 (2026-10-31 확인). **승인 목록 1/41** (`cd app && npx playwright test`, 화면 전이라 40개 실패는 정상 — run.sh에는 41/41이 되면 넣는다)
사용자에게 남은 질문: (엔진 v4, 어코드 0.35 : 노트 0.55 확정) ① '강' 값 2.0/2.5/3.0 (`docs/engine_strong.md`) ② 등급 기준값 ③ 블라인드 문서를 v4로 다시 만들지(50줄 중 7줄 바뀜). 블라인드·눈으로 평가는 Claude 임시 답안(9.5/10, 3.7점) — 사장 답안 대기
다음 할 일: 아래 "다음 세션에서 할 일" 1 → 2 → 3 순서 (엔진은 이제 TypeScript `app/src/engine/` — 1번의 "Java EngineParams"는 `app/src/engine/params.ts`)

## 2026-10-31 단계 1·2 결과
- 단계 1: Expo 뼈대(`app/`: 탭 4개, PC에서는 왼쪽 메뉴, 이름표 `src/ui/testIds.ts`, 설정 `src/config.ts`) + 승인 목록 41개 Playwright 테스트(`app/tests/acceptance/`, 공통 준비 `harness/`). 테스트 준비에서 정한 것(글자 200% = 브라우저 확대 등)은 `docs/NOTES.md`.
- 단계 2: TS 엔진 `app/src/engine/` + Vitest 골든·성질 36개. Java `engine/`과 Gradle 루트는 지웠다. 서버(`backend/`)는 단계 4에서 자체 Gradle로 만든다.
- 다른 컴퓨터에서 처음이면: `cd app && npm install && npx playwright install chromium`.

## 2026-10-31 방향 변경 (먼저 읽기)
- 사장 결정: 안드로이드 전용 → **웹 + 안드로이드(Play 앱) + 아이폰(1차는 웹 주소, 앱스토어는 다음 차수, 그때 막히지 않게)**, PC 화면도 제대로 지원. 기획서 수정은 사장 OK.
- Claude 파악용 명세서 `docs/spec.md`를 새로 썼다. 이어 하기 전에 먼저 읽는다.
- 2단계: 사장이 **E안 Expo(React Native)**를 골랐다(Claude 추천은 A안 React 웹 + Capacitor). design.md 1·1-1·2·3·4·10·14~17절, spec.md 반영 완료.
- 3단계: 사장이 바뀐 테스트 항목을 재승인했다(41개, tests.md). Kotlin 앱(`app/`)과 Robolectric 테스트는 지웠다. 가짜 카탈로그는 `tests/fixtures/test_catalog.json.gz`로 옮겼다(Java·TS·Playwright 공통).
- Java 엔진(`engine/`, Gradle)은 TypeScript로 옮길 때 기준으로 **아직 남겨 두었다**. TS 엔진이 같은 골든·성질 테스트를 통과하면 지운다.
- 4단계 계획은 계획 모드에서 세워 사장 승인(2026-10-31) → `docs/plan.md`. 앱 이름 Perfumer-LAB(향수 공방소), 앱 ID com.perfumerlab.scentstudio. 사장이 DB 구축 단계를 따로 넣어 달라고 해서 단계 3으로 넣었다.
- (지난 메모) **다음: 4단계 계획 모드.** 계획의 첫 단계는 Expo 앱 뼈대 + 승인 목록 41개 Playwright 테스트 코드(3단계에서 남은 일, 화면 요소 이름표가 필요해 뼈대와 함께 쓴다). 그다음 TS 엔진 → 엔진 문제(아래 1·2) → 서버 → 디자인 시안 → 화면.
- 아래 "다음 세션에서 할 일"의 엔진 문제(1·2)는 화면 기술과 상관없이 그대로 남아 있다.

## 다음 세션에서 할 일 (2026-10-30 사장 요청으로 여기서 멈춤)
> ⚠️ 이 단계의 작업은 승인 테스트 0/40(화면 전) 상태라 main이 아니라 **작업 브랜치 `wip/scent-studio-stage4`**에 올렸다(2026-10-30, 실패 상태 커밋은 사장 결정). 다른 컴퓨터에서는 `git fetch` → `git switch wip/scent-studio-stage4`. 승인 테스트가 모두 통과하면 main에 합친다.
> 원본 데이터가 없으면 먼저 `py pipeline/fetch.py` → `py pipeline/build_catalog.py`.

### 1. ✅ (2026-10-31) 레몬 "강" 문제 — 특징 강조 + 강 2.0 + 최소 등장 수 20 + 등급 0.87/0.79 (engine v3, 이유는 NOTES.md, 비교는 docs/engine_variants.md)
- 사장이 짚은 문제: 맹점 조합(레몬 강·오스만투스·샌달우드 약)의 조합 어코드가 시트러스 22 = 플로럴 22, Top 5 중 시트러스가 1위 어코드인 향수 0개.
- 원인: 노트 프로필은 이미 "전체 평균 빼기"(design.md 8-2절)인데도, 레몬 프로필은 시트러스 48%로 퍼져 있고 오스만투스는 플로럴 55%로 몰려 있음 + "강"(1.5)이 "중"(1.0)과 차이가 작음.
- 비교 실험: `py pipeline/experiments/profile_methods.py` (같은 향수 묶기 반영 후 결과, 재현은 정확히 같은 향수만 센 값)

  | 방식 | 조합 어코드 | 시트러스 1위 | 재현(기준 15) |
  |---|---|---|---|
  | 지금: 평균 빼기, 강 1.5 | 시트러스 22 · 플로럴 22 | 0/5 | 15 |
  | 평균 빼기, 강 2.0 | 시트러스 29 · 플로럴 19 | 2/5 | 15 |
  | 특징 강조(PPMI: m·max(0, ln(m/g))), 강 1.5 | 시트러스 26 · 플로럴 23 | 1/5 | 15 |
  | 특징 강조, 강 2.0 | 시트러스 34 · 플로럴 19 | 2/5 | 15 |
  | 제곱, 강 2.0 | 시트러스 43 · 플로럴 26 | 2/5 | 13 |

  **2026-10-31 다시 잼**: 재현을 `evaluate.py` 방식(같은 향수의 다른 농도도 맞힘)으로 바꿔 다시 쟀다(지금 방식 행이 evaluate.py 결과 15/20과 일치). 특징 강조도 15로 기준을 넘는다. 맹점(시트러스+플로럴) 5/5, 브랜드 81~84개는 모든 방식이 같다. → 둘 다 기준을 넘으므로 **특징 강조 + 강 2.0 추천**. 사장 결정 대기.
- 지난 세션 추천은 "특징 강조 + 강 2.0"이었으나, 같은 향수 묶기를 넣은 뒤 재현이 14로 떨어졌다. **먼저 `evaluate.py` 방식(같은 향수의 다른 농도도 맞힌 것으로 셈)으로 두 후보(특징 강조+강 2.0 / 평균 빼기+강 2.0)를 다시 재고**, 기준(재현 15)을 넘는 쪽을 추천해 사장에게 묻는다. 둘 다 넘으면 시트러스가 더 앞서는 특징 강조를 추천.
- 정해지면 바꿀 곳: 프로필 계산 `pipeline/build_catalog.py` 6번 단계 · "강" 값 `pipeline/engine_ref.py` INTENSITIES와 Java `EngineParams.INTENSITIES`·`ENGINE_VERSION` 3 · `requirements.md` 핵심 개념 표(강도) · `design.md` 8-2·8-3절 · 앱이 강도 이름(strong)을 숫자로 바꾸는 곳(M4에서 만들 때) → `py pipeline/build_catalog.py --fixture && py pipeline/build_catalog.py && py pipeline/make_golden.py && py pipeline/evaluate.py` → `bash tests/run.sh`로 파이프라인·엔진 통과 확인.

### 2. 블라인드 비교 → 눈으로 평가
- 바뀐 엔진으로 `py pipeline/evaluate.py` → 사장에게 `docs/engine_blind.md`를 보여 주고 답을 받는다 (형식: `블라인드: 1A 2B …`).
- 답을 `docs/engine_blind_answers.txt`에 적고(`블라인드: …` 줄) `py pipeline/evaluate.py` → 이제 `engine_report.md`에 지금 엔진의 Top 5가 나오므로 그걸 보여 주고 눈으로 평가 점수 10개를 받아 같은 파일의 `점수: …` 줄에 적고 다시 실행.
- 기준: 블라인드 6/10 이상, 눈으로 평균 3.5 이상. 못 넘으면 원인을 찾아 사장과 다시 정한다.

### 3. 그다음
- ★ 백엔드팀 검토 마무리 → M3 서버(`backend/`, Spring Boot 4.1) → ★ 디자인 시안 → M4 화면 (docs/plan.md).

## 지금까지 한 것
- 1단계(기획)와 2단계(설계)는 사장과 Claude 앱 대화에서 진행했다. 결과물이 이 폴더에 있다:
  - `docs/requirements.md` — 기획서 (목표, 사용자, 기능 우선순위, 하지 않을 것, 화면 흐름도)
  - `docs/design.md` — 고른 안(D안: Kotlin 안드로이드 앱 + Spring, 데이터 작업만 Python)과 버린 안, 추천 엔진 명세, API, 테스트 설계, 구현 순서
  - `docs/reference.md` — 배경 자료 (데이터 소스 목록과 주의점, 노트 정규화 사례, 조향 기초, 참고 사이트). 기준 문서가 아니라 참고용
- 2026-10-30: 사장이 "안드로이드 앱 전용"으로 정해 A안(React 웹 + Capacitor)에서 D안(Kotlin)으로 바꿨다. 기획서 "사용자"·"하지 않을 것"과 설계서 1·2·4·8·9·10·11·12절을 고쳤다. 이 변경은 사장이 OK했다
- 2026-10-30: 사장 요청으로 Claude가 설계서를 보강했다(`design.md` 0절에 요약). 실제 Parfumo 데이터를 실사하고 엔진을 시험해, 노트 프로필 보정, 앱 안 추천 계산(D1), 탭 4개(D2), 로그인 전 설정 저장(D3), 내부 테스트까지만 배포(D4)를 제안했다. 사장이 2026-10-30 추천안대로 OK했고, 0-3절 항목을 requirements.md에 반영했다
- 4단계 시작 전에 이 컴퓨터에 Android Studio가 필요하다 (루트 HANDOFF "처음 쓰는 컴퓨터 준비" 6번)
- 기준은 requirements.md와 design.md 두 문서다. 저장소 밖 문서(Claude 앱의 설계 문서 등)와 다르면 이 폴더의 문서를 따른다.
- 이 앱의 1·2단계를 다시 묻지 않는다. 설계안 3개도 다시 만들지 않는다.

## 3단계 시작 전 확인 (사장에게 짧게, 한 번에 하나씩)
1. ✅ (2026-10-30) 기획서·설계서 보강판을 사장이 OK했다. 고칠 점이 있으면 문서를 고치고 다시 확인받는다.
2. ✅ (2026-10-30) 디자인 방향: 사장이 준 컬러 시스템 + 대비 보정 + 계열 색 없음 + "아기자기하지 않게, 깔끔하고 고급스럽게". `docs/design.md` 3절에 적었다.
3. 그다음 QA팀이 `docs/design.md` 14절(승인 목록 후보 10개)을 재료로 테스트 목록(`docs/tests.md`)을 쉬운 말로 써서 승인받는다.

## 메모
- 테스트 명령 줄은 아직 없다. 3단계에서 테스트를 만들 때 `bash tests/run.sh`로 추가한다(그 전까지 훅이 이 앱을 건너뛴다).
- 4단계 구현 순서와 관문은 `docs/design.md` 15절(M0~M5). 추천 엔진은 화면보다 먼저 노트북으로 검증하고, 합격 기준표를 백엔드팀 사장 검토 때 보여 준다.
- 원본 데이터 CSV는 커밋하지 않는다 (`pipeline/data/raw/`).

## 3단계 결과 (2026-10-30, 아직 커밋 안 함 — 4단계에서 테스트가 통과한 뒤 함께 커밋)
- `docs/tests.md` 승인 목록 40개를 사장이 승인했다.
- 테스트 코드: `app/src/test/java/.../acceptance/` (A~I 9개 파일 = 40개), 공통 준비 `acceptance/harness/`(가짜 서버·가짜 로그인·화면 검사 도우미).
- 화면 요소 이름표 `app/src/main/java/.../ui/Tags.kt`, 앱 조립 약속 `ScentApplication.kt`(`AppConfig`), `auth/AuthGateway.kt`. 이름표를 바꾸면 승인 테스트가 깨지므로 사장 재승인 필요.
- 가짜 데이터: `tests/fixtures/synthetic_parfumo.csv`(지어낸 향수 40개), `tests/fixtures/notes_ko.csv`. M0에서 파이프라인으로 `app/src/test/resources/catalog/test_catalog.json.gz`를 만든다(이 파일은 가짜 데이터라 커밋해도 됨). 테스트용 파이프라인 기준값은 작게: 목록 노출 df≥3, 검색 전용 df≥2, 궁합 함께 나온 횟수≥2.
- 빌드: Gradle 9.8.2(wrapper), AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, Robolectric 4.17(SDK 36), Roborazzi 1.77.0. 이 컴퓨터의 JDK는 25라 Robolectric용 `--add-opens/--add-exports`를 `app/build.gradle.kts`에 넣었다.
- 전체 테스트 실행 시간: 실패 상태에서 약 3분 30초(테스트마다 5초 기다림). 훅 제한 5분에 가까우므로 구현 후 다시 재고 `NOTES.md`에 적는다.
- 서버 Spring Boot는 최신 안정판이 4.1이라 4로 만든다 → 4단계에서 design.md 1절 고칠 것.

## 4단계 진행 (2026-10-30, 아직 커밋 안 함 — 승인 테스트가 통과해야 커밋 가능)
- M0 파이프라인: `pipeline/`(fetch, catalog_rules, build_catalog, manual/*.csv, tests/). 원본은 `py pipeline/fetch.py`로 받는다(커밋 안 함). 보고서 `pipeline/report.md`.
- M1 참조 엔진·검증: `pipeline/engine_ref.py`, `pipeline/evaluate.py` → `docs/engine_report.md`, 정답 `docs/engine_blind_key.md`(사장이 고르기 전에는 열지 않는다).
- M2 Java 엔진: `engine/`. 골든 파일 `tests/golden/recommend_cases.json`은 `py pipeline/make_golden.py`로 만든다.
- 블라인드 비교는 `docs/engine_blind.md`로 분리했다(2026-10-30 사장 요청). 지금 엔진의 추천 목록이 보이는 표는 사장 답(`docs/engine_blind_answers.txt`)이 있을 때만 `engine_report.md`에 나온다. A/B 배치는 고정 난수라 정답 파일을 따로 두지 않는다.
- 추천에서 농도만 다른 같은 향수는 하나만(`group`, 2026-10-30 사장 요청).
- 노트 한글명 1,198개는 에이전트가 초안을 썼다(`pipeline/manual/notes_ko.csv`). 사장 검토 때 이상한 이름이 보이면 고친다.
