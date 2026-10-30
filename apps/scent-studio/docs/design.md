# scent-studio 설계 (1차)

> 작성: 2026-10-30 · /build-app 2단계 결과물
> 기술은 CLAUDE.md "팀별 기본 기술"을 따른다. Python은 화면 뒤에서 한 번씩 돌리는 데이터 작업에만 쓴다.
> **저장소 밖 문서와 내용이 다르면 이 문서가 기준이다.**

## 1. 고른 안: D안 — Kotlin 안드로이드 앱 + Spring, 데이터 작업만 Python
> 2026-10-30 변경: 사장이 "안드로이드 앱 전용"으로 정해 A안(React 웹 + Capacitor)에서 바꿨다. 서버·추천 엔진·데이터 작업은 A안 그대로다.
> 기술 규칙은 CLAUDE.md "안드로이드 앱 전용" 항목.

| 영역 | 맡는 팀 | 기술 |
|---|---|---|
| 화면 (안드로이드 앱) | 프론트엔드팀 | Kotlin + Jetpack Compose(Material 3), Gradle(`./gradlew`), 최소 Android 8.0(API 26). 화면 상태 ViewModel + StateFlow(조합 임시 상태 포함), 화면 이동 Navigation Compose, 서버 통신 Retrofit + kotlinx.serialization, 구글 로그인 Credential Manager + Supabase Kotlin 클라이언트(Auth만) |
| 디자인 | 디자인팀 | `docs/DESIGN.md` + Compose 테마(색·글꼴·모양) (4단계 시작에 작성). 시안 캡처는 Roborazzi |
| 서버·API·로그인 검증 | 백엔드팀 | Java 21 + Spring Boot 3, Gradle(`./gradlew`), Spring Web, Spring Data JPA, Flyway, Spring Security(OAuth2 Resource Server), springdoc-openapi |
| 추천 엔진 | 백엔드팀 | 순수 Java 패키지 `engine` (DB·Spring을 모름). 서버 시작 시 카탈로그를 메모리에 올려 계산 |
| DB · 로그인 | 백엔드팀 | Supabase를 **PostgreSQL 호스팅과 구글 로그인(Auth)으로만** 사용. 데이터 접근 제한은 Spring이 한다 |
| 데이터 정리 | 백엔드팀 | Python 3.11 + pandas (`pipeline/`). 원본 정리 → DB 적재 → 골든 테스트 파일 생성 |
| 엔진 검증 실험 | 백엔드팀 | Jupyter 노트북 (`notebooks/`), Python 참조 엔진 사용 |
| 테스트 | QA팀 | 승인 목록: Compose UI 테스트(Robolectric, 에뮬레이터 없이) · 엔진/API·화면 계산: JUnit · 시안 캡처: Roborazzi · 실제 기기 확인: 에뮬레이터 테스트(6단계에 따로) · 전체 실행: `bash tests/run.sh` |

## 2. 버린 안과 이유
| 안 | 내용 | 버린 이유 |
|---|---|---|
| A | React 웹 + Spring, Capacitor로 감싸 안드로이드 배포 (처음 고른 안) | 사장이 웹 없이 안드로이드 앱 전용으로 정함. 앱 안의 화면이 웹 화면이라 조작감이 진짜 앱보다 떨어짐 |
| B | FastAPI(Python) + Expo(React Native) | 서버까지 Python으로 바뀌어 작업장 기본 기술(Spring)과 충돌. React Native만 쓰는 변형도 검토했으나, 앱 전용이면 안드로이드 정식 언어(Kotlin)가 조작감·성능이 가장 좋아 사장이 Kotlin을 고름 |
| C | 서버 없이 React만, 브라우저에서 추천 계산 | 카탈로그가 수만 건이라 첫 로딩이 무겁고, 로그인·저장·피드백을 위해 결국 서버가 필요 |

## 3. 디자인 방향
**미정 — 사장 선택 필요.** 보기(추천 순): ① 깔끔한 흰색 + 파란 포인트 하나 ② 따뜻한 베이지·향수 매장 느낌 ③ 어두운 고급 느낌.
고른 결과를 여기에 적고, 디자인팀은 4단계 시작에 `docs/DESIGN.md`를 쓴다. 공통 원칙: AI가 만든 티가 나지 않는 절제된 화면, 계열마다 정해진 색 하나.

## 4. 폴더 구조
```
apps/scent-studio/
├── HANDOFF.md
├── docs/            requirements.md, design.md (+ 이후 tests.md, plan.md, DESIGN.md, NOTES.md, review.md)
├── android/         Kotlin + Compose (Gradle 프로젝트)
│   └── app/src/
│       ├── main/java/.../  ui/screens(S01~S12), ui/components, ui/theme(DESIGN.md 적용), blend/BlendDraftViewModel.kt, data/api(Retrofit), auth(구글 로그인)
│       ├── test/           승인 목록(Compose UI 테스트, Robolectric) · 화면 계산 JUnit · Roborazzi 캡처
│       └── androidTest/    에뮬레이터 테스트 (6단계에 따로 실행)
├── backend/         Spring Boot (Gradle)
│   └── src/main/java/.../
│       ├── engine/  추천 엔진 (순수 Java)
│       ├── api/     컨트롤러 (notes, blends, recommendations, perfumes, favorites, feedback, me)
│       ├── domain/  엔티티·리포지토리
│       └── security/ Supabase JWT 검증 설정
│   └── src/main/resources/db/migration/   Flyway SQL (스키마는 여기서만 바꾼다)
├── pipeline/        Python: normalize.py, build_profiles.py, load.py, engine_ref.py, make_golden.py
│   └── data/raw/    원본 CSV (커밋하지 않음, .gitignore)
├── notebooks/       engine_validation.ipynb
└── tests/
    ├── run.sh       빠른 테스트를 차례로 실행 (HANDOFF의 테스트 명령. 에뮬레이터 테스트는 넣지 않음)
    └── golden/      catalog.json(고정 소형 카탈로그), recommend_cases.json(기대 결과)
```
경로는 모두 앱 폴더 기준 상대 경로. 비밀값(DB 주소, Supabase 키)은 `.env`에 두고 `.env.example`에 이름만 적는다.

## 5. 데이터 모델 (PostgreSQL)
### 카탈로그 (읽기 전용, 파이프라인이 채움)
| 테이블 | 컬럼 | 비고 |
|---|---|---|
| brands | id, name, country | |
| perfumes | id, brand_id, name, year, gender, concentration, rating, rating_count, is_candidate, source, source_id, updated_at | is_candidate = 1차 추천 후보 여부 |
| families | code, name_ko, wheel_order, color | citrus, floral, woody … |
| notes | id, name_en, name_ko, family_code, description, popularity | |
| note_aliases | alias, note_id | "Calabrian Bergamot" → Bergamot |
| perfume_notes | perfume_id, note_id, layer | layer ∈ top/middle/base |
| accords | id, name_en, name_ko, family_code, color | 어코드도 계열에 속한다 (취향 점수용) |
| perfume_accords | perfume_id, accord_id, strength | 향수별 합 100으로 정규화 |
| note_accord_profiles | note_id, accord_id, weight | 노트별 합 1로 정규화 |
| note_pairings | note_id, paired_note_id, score | 궁합 힌트 상위 3개 |

### 사용자 데이터 (서버가 로그인한 user_id 것만 읽고 쓴다)
| 테이블 | 컬럼 |
|---|---|
| user_preferences | user_id(PK, Supabase uid), preferred_families text[], disliked_note_ids int[] |
| blends | id, user_id, name, families text[], created_at |
| blend_notes | blend_id, note_id, layer, intensity (0.5 / 1.0 / 1.5) |
| favorites | user_id, perfume_id, created_at |
| tasting_feedback | id, user_id, perfume_id, blend_id(선택), satisfaction 1~5, similarity 1~5, memo, tested_at |

## 6. 데이터 파이프라인 (Python, 사람이 필요할 때 실행)
데이터 소스별 내용·주의점과 노트 정규화 사례는 `reference.md` 1·2절, 계열 순서(`wheel_order`)와 궁합 문구 재료는 3절.

1. 원본 수집: FragDB 샘플·Parfumo CSV → `pipeline/data/raw/` (출처·받은 날짜 기록)
2. 노트 정규화: 소문자·공백·악센트 정리 → `note_aliases`로 대표 노트에 합침 → 미매핑 노트는 `unmapped_notes.csv`
3. 한글명·계열: FragDB 번역 사용, 없으면 상위 300개 수작업
4. 어코드 정규화: 향수별 합 100
5. 후보 표시 `is_candidate`: 노트 3개 이상 **그리고** 탑·미들·베이스 구분 있음 **그리고** 어코드 있음
6. `note_accord_profiles`: 노트별로, 그 노트가 든 후보 향수들의 어코드 비율 평균 → 합 1로 정규화
7. `note_pairings`: 같은 향수에 함께 나온 빈도(PMI)로 노트별 상위 3개
8. DB 적재(upsert) + `report.md` (향수 수, 미매핑 비율, 어코드 누락 비율). 미매핑 비율 5% 이하가 기준
9. 골든 파일 생성: `make_golden.py` → `tests/golden/` (아래 9절)

## 7. 추천 엔진 명세 (Python 참조 엔진과 Java 엔진이 **똑같이** 따라야 함)
입력: 고른 노트 목록 `[(note_id, layer, intensity)]`, 불호 노트 집합, 선호 계열 집합(없을 수 있음), k(기본 10)

1. **후보:** `is_candidate = true`이고, 불호 노트를 어느 층에도 갖지 않은 향수
2. **향수 어코드 벡터 a:** perfume_accords.strength (합 100)
3. **조합 어코드 벡터 b:** 고른 노트마다 `intensity × note_accord_profiles(note)`를 더한다 (층은 반영하지 않음)
   - 화면용 비율: `b / sum(b) × 100`을 반올림해 정수로 (표시만, 계산에는 반올림 전 값 사용)
4. **어코드 유사도:** `accord_sim = cos(a, b)` (0~1, 둘 중 하나라도 0벡터면 0)
5. **노트 일치도:** 고른 노트 n마다
   - 가중치 `w_n = ln(1 + N / df_n)` (N = 후보 향수 수, df_n = n을 가진 후보 수)
   - 점수 `c_n` = 1.0(같은 층에 있음) / 0.5(다른 층에만 있음) / 0.3(n은 없지만 같은 계열 노트가 있음) / 0
   - `note_match = Σ w_n·c_n / Σ w_n` (강도는 반영하지 않음)
6. **취향 적합도:** `pref = (향수 어코드 중 계열이 선호 계열인 strength의 합) / 100`
7. **점수:** 선호 계열이 있으면 `0.6·accord_sim + 0.3·note_match + 0.1·pref`, 없으면 `(0.6/0.9)·accord_sim + (0.3/0.9)·note_match` (반올림한 0.6667을 쓰지 말고 이 식 그대로)
8. **정렬:** 점수 내림차순 → 같으면(차이 1e-9 이하) rating_count 내림차순 → perfume_id 오름차순. 상위 k개
9. **반환:** perfume_id, score, accord_sim, note_match, 향수 상위 어코드 3개, matched_notes(note_id, layer), missing_notes(향수 상위 노트 중 고르지 않은 것 최대 3개)

계산은 모두 double. 가중치(0.6/0.3/0.1, 1.0/0.5/0.3)는 한 곳(설정 상수)에만 두고, 바꿀 때는 Python·Java·골든 파일을 함께 바꾼다.

## 8. API (Spring Boot, `/api/v1`, JSON)
로그인은 안드로이드 앱이 Supabase Auth(구글)로 하고, 받은 JWT를 `Authorization: Bearer`로 보낸다. Spring Security가 검증한다.
| 메서드 · 경로 | 로그인 | 설명 | 화면 |
|---|---|---|---|
| GET /families | — | 계열 목록 | S02 |
| GET /notes?family=&q=&layer= | — | 노트 목록·검색(한글/영문/별칭) | S03 |
| GET /notes/{id} | — | 노트 상세 + 궁합 노트 3개 | S04 |
| POST /blends/analyze | — | 조합의 어코드 비율 + 균형 경고 | S05 |
| POST /recommendations | 선택 | Top k. 로그인 시 불호·선호 자동 반영 | S06 |
| GET /perfumes/{id}?blend= | — | 향수 상세 (+ 조합과 비교) | S07 |
| GET /perfumes?q= | — | 향수 검색 | S08 |
| GET·POST /blends, DELETE /blends/{id} | 필수 | 내 조합 | S01, S09 |
| GET /favorites, PUT·DELETE /favorites/{perfumeId} | 필수 | 찜 | S07, S09 |
| GET·POST /feedback | 필수 | 시향 피드백 | S09, S10 |
| GET·PUT /me/preferences | 필수 | 불호 노트, 선호 계열 | S11 |

- 조합 검증(서버에서도): 층당 최대 3개, 전체 최소 1개, intensity ∈ {0.5, 1.0, 1.5}
- 오류 형식: `{"error": {"code": "NOTE_NOT_FOUND", "message": "..."}}`
- 앱의 Kotlin 데이터 클래스는 springdoc이 만든 OpenAPI 문서에서 생성한다 (손으로 맞추지 않음)
- 개발 중 에뮬레이터에서 PC의 서버에 붙을 때 주소는 `http://10.0.2.2:8080` (빌드 설정값으로 두고 코드에 박지 않음)

## 9. 테스트 설계 (QA팀 3단계 재료)
- **승인 목록(Compose UI 테스트 + Robolectric, `android/app/src/test/`)**: 3단계에서 사장 승인. 에뮬레이터 없이 PC에서 앱 화면을 사람처럼 눌러 확인한다. 서버는 가짜 응답(MockWebServer)으로 대신한다. 후보: 노트 고르기 → 조합 요약 → 추천 10개 표시, 불호 노트 제외, 저장·피드백 후 보관함에 보임, 비로그인 시 저장에서 로그인 요구, 작은 폰·큰 폰 화면에서 동작
- **실제 기기 확인(에뮬레이터, `android/app/src/androidTest/`)**: 승인 목록 중 핵심 흐름(노트 고르기 → 추천)을 에뮬레이터에서 실제 서버와 함께 돌린다. 느려서 훅에 넣지 않고 6단계 QA 보고 전에 `./gradlew connectedDebugAndroidTest`로 돌려 결과를 보고에 적는다
- **골든 테스트(JUnit)**: `tests/golden/catalog.json`(고정 소형 카탈로그 약 200개)과 `recommend_cases.json`(기준 조합 20개 + 맹점 조합의 기대 결과, Python 참조 엔진이 생성)을 Java 엔진에 넣어 **순위가 같고 점수 차가 1e-6 이하**인지 확인. DB 없이 돈다
- **엔진 합격 기준** (노트북으로 확인, 결과를 백엔드팀 사장 검토 때 함께 보여 줌):
  | 항목 | 기준 |
  |---|---|
  | 기준 세트 재현 (실제 향수 20개의 노트를 넣으면 그 향수가 Top 3) | 20개 중 16개 이상 |
  | 맹점 테스트 (레몬 강 + 오스만투스 중 + 샌달우드 약) | Top 5 중 4개 이상이 시트러스·플로럴 메인 |
  | 눈으로 평가 (조합 10개 × Top 5, 1~5점) | 평균 3.5 이상 |
  | 계산 시간 (후보 3만 건) | 1회 200ms 이하 |
- 화면 계산(JUnit, ViewModel 단위): 어코드 비율 막대 합이 100, 층당 3개 제한 등
- 전체 실행 방법: 3단계에서 `tests/run.sh`(백엔드 `./gradlew test` → 앱 `./gradlew testDebugUnitTest`)를 만들고 HANDOFF의 테스트 명령 줄을 `bash tests/run.sh`로 채운다. 훅이 5분 안에 끝나야 하므로 시간이 넘으면 `NOTES.md`에 적고 줄일 방법을 찾는다

## 10. 4단계 구현 순서 (plan.md에 옮길 것)
1. 백엔드팀: Flyway 스키마, 파이프라인으로 데이터 적재
2. 백엔드팀: Python 참조 엔진 + 노트북 검증 → 합격 기준표 → 골든 파일
3. 백엔드팀: Java 엔진(골든 테스트 통과) → API
4. ★ 백엔드팀 사장 검토: 저장할 데이터·기능 목록 + 엔진 합격 기준표
5. ★ 디자인팀 사장 검토: DESIGN.md + 시안 캡처
6. 프론트엔드팀: 안드로이드 화면 (S02→S05 흐름 먼저, 그다음 S06·S07, 마지막 보관함·마이·로그인)
7. 프론트엔드팀: 에뮬레이터에서 실제 서버와 함께 실행 확인, 출시용(release) 빌드 확인

## 11. 배포 (6단계, 공개는 사장이 `!` 한 줄로 실행)
| 부분 | 위치 | 메모 |
|---|---|---|
| API | Render (Docker) | 무료 512MB에서 Spring이 빠듯할 수 있음 → JVM 메모리 상한 지정, 느리면 유료 플랜 검토 |
| DB · 로그인 | Supabase 무료 티어 | |
| 안드로이드 | `./gradlew bundleRelease`(앱 묶음 파일) → Play Console 내부 테스트 | 계정 등록과 앱 서명 키 보관은 사장이 직접 (서명 키는 커밋하지 않음) |

## 12. 위험과 대응
| 위험 | 대응 |
|---|---|
| Python과 Java 엔진 결과가 어긋남 | 7절 명세 한 곳, 골든 테스트로 자동 확인 |
| 어코드 강도는 커뮤니티 투표 값(실제 처방 비율 아님) | 화면에 "메인 테마 추정치", "데이터상 유사도"로 표기 |
| 데이터 라이선스 | 공개 배포 전 확인, 출처 표시 |
| 유명 브랜드만 상위에 몰림 | 노트북에서 상위 결과 브랜드 분포 확인 |
| 무료 서버에서 Spring 메모리 부족 | 11절 대응 |
| 안드로이드 빌드가 느려 훅(5분)이 시간 초과 | 훅에는 에뮬레이터 없는 테스트만. 그래도 넘으면 Gradle 설정(캐시·데몬)부터 손보고 `NOTES.md`에 기록 |
| 이 컴퓨터에 Android Studio(SDK)가 없음 | 4단계 시작 전 사장에게 설치 부탁 (루트 HANDOFF "처음 쓰는 컴퓨터 준비" 6번) |
