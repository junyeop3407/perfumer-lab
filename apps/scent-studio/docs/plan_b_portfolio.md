# Plan B — 스토어 출시 없이 GitHub 포트폴리오로 공개

작성 2026-11-11 · 상태: **사장 판단 반영(2026-11-11)**, 1-3 ①·1-4 정리 끝 · 결정일 **2026-11-25**(2026-11-11 일정 변경)

- **Plan A**: 지금 계획대로 Play 비공개 테스트(12명·14일) 뒤 스토어 공개. 전제는 **Fragrantica와 Parfumo 둘 다 데이터 사용 허가**(기본 카탈로그가 Parfumo라서, 사장 결정 2026-11-11).
- **Plan B**: 앱은 끝까지 완성한다. 스토어에는 내지 않고, GitHub 포트폴리오로 공개해 면접에서 보여 준다.
- **바꾸는 조건(일정 2026-11-11 사장 변경)**: 11-11 Parfumo(info@parfumo.com) 첫 메일 → 11-14 Fragrantica 추가 메일 → 11-18 Parfumo 추가 메일(답이 없으면) → **11-25 Plan A/B 결정**. **Fragrantica와 Parfumo 둘 다 허가해야 Plan A, 아니면 Plan B.**

---

## 1. 데이터 공개 범위 (가장 중요)

### 1-1. 데이터가 어디서 왔나
| 데이터 | 출처 | 라이선스 | 앱에서 쓰는 곳 |
|---|---|---|---|
| 향수 목록·노트·어코드 이름·평가 수 | Parfumo (TidyTuesday 2024-12-10 CSV, `pipeline/fetch.py`) | **표시 없음**(수집 데이터) | 카탈로그 전체, 노트 프로필 계산 |
| 어코드·노트 **강도**, "비슷한 향수" 투표 | **Fragrantica** (Kaggle Decant, `pipeline/fetch_decant.py`) | Kaggle 표기 CC BY-NC-SA 4.0. Fragrantica 자체 약관은 **허가 대기** | 엔진 v7부터 점수 계산, 엔진 v8 추정치, 사람 유사 시험 |
| 한글 노트 이름·계열 보정·제외 목록 | 직접 만듦 (`pipeline/manual/`) | 우리 것 | 카탈로그 만들 때 |

### 1-2. 공개 저장소(perfumer-lab, PUBLIC) 확인 결과 (2026-11-11, 모든 브랜치의 커밋 기록 포함)
| 무엇 | 지금 파일 | git 기록 | 판정 |
|---|---|---|---|
| 실제 카탈로그(앱 안 엔진 6 `app/assets/catalog/catalog.json.gz`, 배포본 2026.11.05 엔진 8) | 없음(`.gitignore`) | **한 번도 커밋된 적 없음**(200KB 넘는 기록은 package-lock·아이콘뿐) | ✓ 깨끗함. 비공개 저장소·Supabase 비공개 버킷에만 있음 |
| 테스트 픽스처·골든 (`tests/fixtures/*`, `tests/golden/recommend_cases.json`, 지워진 옛 `app/src/test/resources/catalog/test_catalog.json.gz`) | 있음 | 있음 | ✓ **가짜 브랜드 8개로 만든 합성 데이터**(Aurelle, Noirwood…, 주소 `example.invalid`) |
| 스크린샷 | 없음(앱 아이콘만) | 없음 | ✓ |
| **Fragrantica(Decant) 향수별 수치** | 있었음 2곳 ↓ → **2026-11-11 ①로 지움** | 있음(519690d, 629b522부터) → Plan B면 1-5로 걸러냄 | ✓ 현재 파일 / ✗ 기록 |
| └ `docs/engine_v8_trial.md` 126~130줄 | 실제 향수 5개의 "실제 어코드 상위 3 (비중)" | | |
| └ `docs/plan_v8.md` 81~82줄 | 향수 1개의 Decant 어코드 비중 5개 + 노트 강도 3개 | | |
| Fragrantica 집계 통계 (`engine_similar_votes.md`, `engine_v8_*.md`, `data_cleaning_words.md`, `pipeline/report.md`) | 있음 | 있음 | △ 향수별 값 없이 비율·개수만. 남겨도 된다고 봄(사장 판단) |
| Fragrantica 이름 대응표 (`pipeline/manual/fragrantica_accords.csv`·`fragrantica_note_aliases.csv`·`decant_brand_aliases.csv`) | 있음 | 있음 | △ 어코드·노트 이름 짝, 브랜드 표기 짝(직접 만든 것, 수치 없음) |
| 엔진 결과 목록 (블라인드·눈 평가 `*_pairs.json`·`*_combos.json`·`engine_blind*.md`) | 있음 | 있음 | △ 실제 향수 key·이름만(v7·v8 순위는 Decant 강도로 정해짐), 수치 없음 |
| **참고: Parfumo 향수별 데이터** (`engine_grades_clones.md`·`engine_strong*.md`·`engine_blind*.md`·`engine_report.md` 등 약 20개 문서, `pipeline/copy_suspects.csv`) | 있음 | 있음 | △ 향수 이름·연도·평가 수·조향사·Parfumo 주소. Fragrantica는 아니지만 **역시 라이선스 표시 없는 수집 데이터** → 같은 원칙을 적용할지 사장 판단 |
| 예전 커밋 4개의 실제 향수 key 1개 (plan_stage4 남은 것 8) | 없음 | 있음 | △ 이름 하나. 기록 고쳐 쓰기 금지라 그대로 두기로 했던 것 |

### 1-3. 정리 방법 (사장 결정 2026-11-11: 지금 ①, Plan B 확정 시 ③ = 1-5)
| 방법 | 하는 일 | 장점 | 단점 |
|---|---|---|---|
| ① 지금 파일에서만 지움 | 위 ✗ 2곳을 "향수 A: 아쿠아틱 계열이 가장 강함"처럼 수치·이름 없는 설명으로 바꾸고 커밋 | 간단, 규칙 안 | 옛 커밋에는 남음(누구나 볼 수 있음) |
| ② 기록에서도 지움 | `git filter-repo`로 옛 커밋까지 고쳐 쓰고 강제 푸시 | 기록에서도 사라짐 | **강제 푸시·기록 고쳐 쓰기는 절대 규칙·훅으로 금지**(사장이 따로 풀어 줘야 함). 이미 받은 복사본·GitHub 캐시에는 남을 수 있음. 커밋 번호가 바뀌어 HANDOFF·NOTES의 커밋 번호가 다 틀어짐 |
| ③ **포트폴리오용 새 공개 저장소** | 코드·합성 데이터·정리한 문서만 담아 새 기록으로 시작(예: `perfumer-lab-portfolio`). 지금 `perfumer-lab`은 **비공개로 전환** | 옛 기록 문제가 통째로 없어짐, 강제 푸시 없음, 면접관에게 깔끔한 저장소 | 저장소가 둘이 됨(작업은 계속 perfumer-lab에서, 공개본은 내보내기 스크립트로). Render·GitHub Actions 연결 확인 필요 |

**사장 결정(2026-11-11): 지금 ①, Plan B 확정 시 ③(빈 저장소가 아니라 `git filter-repo`로 문제 파일만 걸러낸 사본, 1-5).** ① Fragrantica 2곳은 2026-11-11에 처리함(수치만 지우고 이름·설명은 남김). 기존 저장소에는 강제 푸시하지 않는다.

(처음 추천: ①을 지금 + Plan B 확정 시 ③.) ✗ 2곳은 Plan A·B와 상관없이 지금 지우는 게 맞다. 허가가 오기 전이기 때문이다. 기록 문제는 Plan B로 공개 저장소를 새로 만들 때 함께 사라진다. Plan A로 가면 허가를 받은 상태라 기록에 남아도 문제가 작다.

### 1-4. Parfumo 향수별 데이터 정리 목록 (①과 같은 기준, **2026-11-11 사장 OK → 정리함**)
기준(사장 OK):
- **대량 목록 파일(향수 수십 개 이상)**: 향수 이름이 든 표 줄은 모두 비공개로 옮긴다. **검증 방법·표본 수·정확도·판정 분포 같은 결과 숫자는 남긴다**(포트폴리오의 엔진 검증 근거). 빠진 표 자리에는 "(향수 N개 표는 비공개 — 원본 …)" 한 줄을 둔다.
- **설명 속 예시(향수 1~5개)**: 이름은 남긴다(①과 같음). 향수별 값은 지운다: 평점, 평가 수, 어코드·노트 목록, 조향사, Parfumo 주소, 향수별 점수.
- **원본은 모두 보관**: 비공개 저장소 `perfumer-lab-private/pipeline-data-out/private_docs/`(+ 로컬 `pipeline/data/out/private_docs/`, 커밋 안 함). 이 문서들을 만드는 스크립트도 이제 이 비공개 폴더에 쓴다(`catalog_rules.private_doc`). 다시 돌려도 공개 docs/에 향수 이름이 되살아나지 않는다.

| # | 파일 | 들어 있던 것 | 한 일 (2026-11-11) |
|---|---|---|---|
| P1 | `pipeline/copy_suspects.csv` | 복사 의심 333행: 브랜드·연도·판정·이유 | **공개 저장소에서 지움**, 비공개로 옮김. `build_catalog.py`가 이제 `pipeline/data/out/private_docs/copy_suspects.csv`에 씀(테스트는 이 파일을 읽지 않음) |
| P2 | `docs/engine_grades_clones.md` | 향수 약 40개: 이름·연도·평가 수·어코드·조향사·주소 | 향수 표 42줄 비공개, 등급 분포·표본 설명 남김. 생성 `grades_clones_check.py` → 비공개 폴더 |
| P3 | `docs/engine_accord_order_check.md` | 향수 20개: 주소·어코드 순서 | 체크표 20줄 비공개, 설명 남김 |
| P4 | `docs/engine_strong.md`, `docs/engine_strong_candidate.md` | 각 약 110개: 이름·어코드 | 조합별 Top 5 표 비공개(109·111줄), **요약 표(강 값별 1위·계열 비율) 남김**. 생성 `strong_value_test.py` → 비공개 폴더 |
| P5 | `docs/engine_blind.md`, `engine_blind_v3.md`, `engine_blind_v6.md`, `engine_blind_vs_v3.md`, `engine_blind_vs_v6.md` | 각 50~60개: 이름·어코드 | A/B 향수 표 비공개, 조합(노트)·안내 남김. 판정 결과는 `engine_report.md` 3절에 그대로. 생성 `evaluate.py` → 비공개 폴더 |
| P6 | `docs/engine_blind_v3_pairs.json`, `_v6_pairs.json`, `_v7_pairs.json`, `_vs_v3_pairs.json`, `_vs_v6_pairs.json`, `pipeline/experiments/v4_blind_top5.json` | 향수 key 각 50~120개 | **공개 저장소에서 지움**, 비공개로 옮김. `evaluate.py`(고정본 채점)·`strong_both.py`가 비공개 폴더에서 읽음. 없으면 채점을 건너뛰거나(evaluate) 받는 법을 알리고 멈춤(strong_both) |
| P7 | `docs/engine_variants.md` | 향수 약 20개: 이름·어코드·점수 | 향수 표 22줄 비공개, 방식별 결과·등급 기준값 남김. 생성 `strong_lemon_variants.py` → 비공개 폴더 |
| P8 | `docs/engine_report.md` | 향수 약 20개: 이름·순위 | 향수별 순위 표 비공개, **재현 20/20·맹점 4/5·블라인드 7/12 등 결과 남김**. 생성 `evaluate.py` → 비공개 폴더 |
| P9 | `docs/engine_how_it_works.md` 178~ | 예시 Top 5 이름·점수 | 이름을 가상 향수 A~E로 바꿈(점수는 계산 예시 그대로) |
| P10 | `docs/engine_weights.md` 20~23 | 이름만 | 그대로 둠 |
| P11 | `docs/engine_v8_trial.md` 126~130, `docs/engine_v8_remeasure.md` 57 | 향수별 코사인·노트 일치 / 점수 기여 | 값을 "(비공개)"·설명으로 바꿈, 이름·순위 남김. 생성 `engine_v8_trial.py`·`engine_v8_remeasure.py` → 비공개 폴더(공개본은 손질한 사본) |
| P12 | `docs/NOTES.md` 100·115 근처 | 예시 이름 + 평가 수 | 평가 수 지움 |
| P13 | `HANDOFF.md`(앱), `pipeline/experiments/engine_v8_trial.py`·`engine_v8_remeasure.py` | 이름만 | 그대로 둠 |

남기는 것(사장 결정): 집계 통계 문서, 이름 대응표(`fragrantica_*.csv`·`decant_brand_aliases.csv`), `docs/design.md` 204 브랜드별 개수, 노트 이름, 합성 픽스처.

### 1-5. Plan B 확정 시 ③: 걸러낸 사본으로 새 공개 저장소 (**실행은 아직 안 함**)
목적: 커밋 기록은 살리고, 문제 파일만 기록에서 뺀다. 기존 `perfumer-lab`에는 강제 푸시하지 않는다(그대로 두고 비공개로 전환).

**걸러낼 것 (기록 전체에서) — 1-3 ①·1-4에서 정리한 파일 전부**

(가) 파일째 기록에서 빼고(`--invert-paths`), 마지막에 **지금의 정리된 판만 다시 넣는** 파일 — 향수별 내용이 문서 대부분이라 옛 판을 줄 단위로 고치기 어려움:
- `docs/engine_v8_trial.md`, `docs/plan_v8.md` (1-3 ① Fragrantica)
- `docs/engine_grades_clones.md`, `docs/engine_accord_order_check.md`, `docs/engine_strong.md`, `docs/engine_strong_candidate.md` (P2·P3·P4)
- `docs/engine_blind.md`, `docs/engine_blind_v3.md`, `docs/engine_blind_v6.md`, `docs/engine_blind_vs_v3.md`, `docs/engine_blind_vs_v6.md` (P5)
- `docs/engine_variants.md`, `docs/engine_report.md`, `docs/engine_how_it_works.md`, `docs/engine_v8_remeasure.md` (P7·P8·P9·P11)

(나) 파일째 기록에서 빼고 **다시 넣지 않는** 파일(지금도 공개 저장소에 없음):
- `pipeline/copy_suspects.csv` (P1)
- `docs/engine_blind_v3_pairs.json`, `docs/engine_blind_v6_pairs.json`, `docs/engine_blind_v7_pairs.json`, `docs/engine_blind_vs_v3_pairs.json`, `docs/engine_blind_vs_v6_pairs.json`, `pipeline/experiments/v4_blind_top5.json` (P6)
- `docs/engine_blind_answers.txt`(기록에만 있음, 답안)

(다) 줄만 바꾸는 파일(`--replace-text`, 기록이 길어 파일째 빼면 손해가 큼):
- `docs/NOTES.md` (P12: 평가 수 `(1,130)`·`(1,117)`·`694`·`695`·`1,537`·`1,535` 문자열 → 지움)
- 예전 커밋 4개의 실제 향수 key 1개(plan_stage4 남은 것 8) → 가짜 key로
- (나)·(가)에서 걸러지지 않은 옛 판의 향수별 값은 6번 검사에서 찾아 여기에 더한다

(라) 커밋 메시지: 향수 이름·수치가 있으면 `--message-callback`으로 바꿈(실행 전 `git log --all --format=%B`로 확인)

**순서**
1. 1-3 ①·1-4 정리를 `perfumer-lab`에서 먼저 끝낸다 — ✅ 2026-11-11
2. 임시 폴더(scratchpad)에 `git clone --mirror`로 새 사본 → 원본 저장소는 건드리지 않음
3. `git filter-repo --invert-paths --paths-from-file drop.txt` ((가)+(나))
4. `git filter-repo --replace-text replace.txt` ((다))
5. (필요하면) `--message-callback` ((라))
6. 사본에 (가) 파일의 지금 정리된 판을 한 커밋으로 다시 넣음("공개용 정리본")
7. 검사: 전체 기록에서 브랜드 목록·`parfumo.com`·향수 key 모양·Decant 수치 모양·비밀값(`sb_secret_`·`.env`·`SCENT_KEY_SECRET`)을 `git log -p --all`로 검색 → 0건이어야 함. 사본에서 `bash tests/run.sh` 통과
8. 사장 확인 → GitHub에 **빈 새 공개 저장소**(예: `perfumer-lab-portfolio`) 만들고 사본 푸시(새 저장소라 강제 푸시 아님)
9. `perfumer-lab`을 비공개로 전환(사장). Render·GitHub Actions(keepalive)는 `perfumer-lab`에 그대로 연결
10. 이후 공개본 갱신: 같은 걸러내기를 다시 돌리는 내보내기 스크립트

주의: `drop.txt`·`replace.txt`에는 실제 향수 이름·key가 들어가므로 **비공개 저장소에 둔다**. 커밋 번호는 사본에서만 바뀌고 원본은 그대로다. 앞으로 1-4 같은 정리를 더 하면 이 목록에도 같이 더한다.

### 1-6. 원칙
- **코드는 공개, Fragrantica 기반 데이터는 비공개.** 실제 카탈로그·강도·투표·향수별 수치는 `perfumer-lab-private`와 Supabase 비공개 버킷(`catalog`)에만 둔다. 공개 문서에는 집계(비율·개수)만 싣는다.
- **공개 데모는 직접 만든 샘플 카탈로그로 돌린다.** 가짜 브랜드·가짜 향수(지금 테스트 픽스처 방식)를 쓴다. 노트 이름은 일반 단어("레몬", "샌달우드")라 그대로 쓴다. **노트 프로필(어코드 비율)은 실제 데이터에서 계산한 값이라 쓰지 않고, 샘플용으로 새로 만든다.**
- **Parfumo 파생 데이터도 같은 원칙(사장 결정 2026-11-11): 향수별 데이터는 공개하지 않는다.** 정리 목록은 1-4. 집계 통계와 대응표는 남긴다.

### 1-7. 문구 초안
**데이터 출처·라이선스 화면 (Plan B 공개 데모)**
> 이 데모의 향수와 브랜드는 모두 **가상의 샘플 데이터**입니다. 실제 향수가 아닙니다.
> 추천 엔진은 실제 서비스용으로 Parfumo 공개 데이터와 Fragrantica 커뮤니티 데이터(Decant, CC BY-NC-SA 4.0)로 만들고 검증했습니다. 이 데이터는 라이선스 확인 전이라 공개하지 않습니다.
> 강도는 함량 측정값이 아니라 커뮤니티가 느낀 강도입니다.
> 오픈소스 라이선스: (자동 생성 목록)

**데이터 출처·라이선스 화면 (Plan A 출시본)** — 허가 조건에 맞춰 최종 문구를 쓴다. 지금 초안:
> 향수 정보: Parfumo · 어코드·노트 강도: Fragrantica (Decant 데이터셋, CC BY-NC-SA 4.0, 허가: [회신 내용]) · 강도는 함량이 아니라 커뮤니티가 느낀 강도 · 비상업 무료 서비스

**README "데이터" 절**
> 이 저장소에는 **코드와 합성(가짜) 데이터만** 있습니다. 엔진 개발·평가에는 Parfumo 공개 데이터와 Fragrantica 커뮤니티 데이터(Decant, CC BY-NC-SA 4.0)를 썼습니다. 원본과 이를 가공한 카탈로그는 라이선스 확인 전이라 비공개 저장소에 둡니다. 문서의 엔진 성능 수치는 그 데이터로 잰 **집계값**입니다. 데모는 직접 만든 가상 카탈로그(샘플 생성기, Plan B에서 만들 예정)로 돌아갑니다.

---

## 2. "GitHub에서 열 수 있게" 하는 방법 비교

| | (a) GitHub Pages: Expo 웹 빌드 + 샘플 데이터 | (b) clone 후 로컬 실행 (docker compose: Postgres + Spring 서버 + 웹) | (c) 데모 영상·GIF + 스크린샷 |
|---|---|---|---|
| 면접관이 보는 법 | 링크 클릭 → 바로 조합·추천·상세 | Docker·Java 설치, 명령 몇 줄 | README 안에서 바로 |
| 보여 주는 것 | 화면·엔진(앱 안에서 계산) | 서버·DB·로그인·제한까지 전부 | 녹화한 흐름(실제 데이터 화면도 가능, 영상은 데이터 파일 공개가 아님 — 단 향수별 수치가 크게 보이는 장면은 피함) |
| 서버(Render·Supabase) | **필요 없음**(추천·상세는 원래 서버 없이 동작). 로그인·저장은 "데모 모드(기기에만 저장)"로 | 필요 없음(로컬 Postgres + 가짜 로그인, `RedTeamRig`와 같은 방식) | 필요 없음 |
| 비용 | 0원 | 0원 | 0원 |
| 만들 일 | 샘플 카탈로그, 데모 모드, Expo `baseUrl`(Pages 하위 경로), Pages 배포 워크플로 | compose 파일, 가짜 로그인 서버 모드, 실행 안내 | 녹화·편집 |
| 단점 | 서버 쪽 실력(보안·제한)은 직접 안 보임 | 면접관 대부분은 안 돌려 봄 | 직접 만져 볼 수 없음 |

**추천 조합: (a) + (c), (b)는 선택.**
- 링크 하나로 바로 써 보게(a) 하고, README 맨 위에 30초 GIF와 스크린샷(c)을 둔다.
- 서버 쪽은 README의 구조도·테스트·레드팀 기록과 코드로 보여 준다.
- (b)는 "직접 돌려 보고 싶은 분께"용이다. 시간이 남으면 만든다.
- Render·Supabase를 데모에 쓰지 않는 이유: 무료 서버는 자다 깨는 데 30~45초(실측 33.6초·43.5초)가 걸려 면접관 첫인상이 나쁘다. Supabase는 7일 동안 요청이 없으면 일시 정지된다.

---

## 3. Plan A/B 공통 작업과 Plan A 전용 작업

### 3-1. 분류
**공통 (그대로 진행)**
- 단계 5 화면 전부(승인 목록 56개), 디자인(DESIGN.md), 단계 6 QA·레드팀
- 1차 기능 7개 중 6개: 로그아웃, 동의 화면(약관·개인정보·만 14세 — Plan B는 문구를 "데모"용으로 짧게), 데이터 출처·라이선스 화면(문구는 1-7), "추천이 이상해요" 신고, 첫 실행 안내, 앱 정보
- plan_stage4 남은 것: 6 바구니 지도 나누기(낮음), 11 동의 문구·버전 올리기, 16 OpenAPI → 앱 타입
- 배포 전 점검: 베이스 이미지 digest 고정, CORS(웹 출처 목록)·403 모양, 로그에 토큰·이메일 없는지(서버 테스트 + Render 로그), Tomcat HTML 400 → JSON(레드팀 4번), 단계 5 항목(로그인 왕복 중 조합 유지, 깨어나는 동안 안내, 시간 초과·다시 시도, 연속 클릭 잠금, 키보드·뒤로 가기·safe area, 저사양 폰 성능)
- **단계 5 할 일: 실제 앱 빌드 결과물(웹 번들·안드로이드 APK/AAB) 비밀값 검사 — 체크리스트 9번 ②.** `sb_secret_`·`service_role`·`secret`·`password` 검색, `EXPO_PUBLIC_`에는 주소·모드만. 묶음 9 레드팀은 화면 전이라 소스·테스트 번들만 봤다.

**Plan A 전용 (Plan B면 보류)**
- 카카오 로그인(비즈 앱 전환부터, `kakao_setup.md`)
- Play: 개발자 계정·비공개 테스트 12명·14일·주변인 테스트, targetSdk 36, 릴리스 권한 점검, Play Console 데이터 보안·콘텐츠 등급·타깃 연령·계정 삭제 웹 주소, 앱 서명 키 SHA-1
- 구글 OAuth 동의 화면 "테스트" → "프로덕션"
- Supabase Redirect URLs에 운영 웹 주소·앱 스킴 `perfumerlab://`
- 앱 안 카탈로그를 실제 배포본으로 교체(남은 것 7), 실제 카탈로그 배포·Storage(이미 함)
- Render 계속 운영, keepalive main 반영, 콜드 스타트 대책

**Plan B에서 새로 생기는 일**
- 1-3 데이터 정리(① + ③), 공개 저장소 내보내기 스크립트
- **샘플 카탈로그 생성기**(가짜 향수 100~300개, 노트 프로필 새로 만들기, 엔진 v8 형식 그대로) + 샘플 골든
- **데모 모드**: 서버 없이 기기 저장만, 로그인 화면은 "데모에서는 로그인 없이 저장돼요" 안내
- GitHub Pages 배포(Expo 웹, `baseUrl`), Actions 워크플로
- README(4절), 구조도, 데모 GIF·스크린샷
- (선택) docker compose 로컬 실행
- 엔진 평가 문서의 공개용 요약(집계만)

### 3-2. 미뤄 둔 세 가지
| 할 일 | Plan A | Plan B | 추천과 이유 |
|---|---|---|---|
| 테스트 계정 삭제 | **출시 직전** 삭제, 다시 만들지 않음 | **단계 5 끝** 삭제 | **둘 다 삭제, 시점은 사장 결정(2026-11-11)대로 미룸** — 단계 5에서 로그인을 확인할 때 테스트 계정이 필요하다. 면접관용 데모 계정은 필요 없다. Pages 데모는 서버를 쓰지 않는다. 서버까지 보여 주려면 (b) 로컬 실행의 가짜 로그인이 안전하다. 공개 데모에 진짜 계정과 비밀번호를 적으면 누구나 실제 DB에 쓸 수 있다 |
| 이메일 로그인 끄기 | 출시 직전, 계정 삭제 뒤 | 단계 5 끝, 계정 삭제 뒤 | **둘 다 끈다(계정 삭제 바로 뒤).** 참고: Supabase에는 "이메일 새 가입만 막고 기존 계정 로그인 허용" 설정이 따로 없다. 전체 "Allow new users to sign up"을 끄면 구글 새 가입도 막히고, Email 공급자를 끄면 기존 이메일 로그인도 막힌다. `before_user_created` 훅으로 provider가 email인 새 가입만 거절하면 된다(기존 계정은 훅을 안 거침, 2026-11-11 조사). 켜 두면 공개 키로 누구나 실제 Supabase에 계정을 만들 수 있다(`api_check.sh`: 지금 on). 끄는 데 비용이 없다 **훅은 만들지 않는다 — 조사 결과만 남김(사장 결정 2026-11-11)**. 실제로 막을 때 다시 정한다. |
| keepalive main 반영 | 반영(Render health 단계 포함) | Render 단계는 반영 안 함. 지금 main의 Supabase keepalive만 유지(데이터 보존, 무료) | **11-25 결정까지 지금 그대로**(main은 Supabase만 깨우는 중). 11-25에 A로 정해지면 그날 반영하고, B면 반영하지 않는다. B에서 Render를 쓰지 않으면 깨울 이유가 없다 |

---

## 4. 포트폴리오에서 보여 줄 것

### 4-1. README 구성안
1. **한 줄 + 데모 링크 + 30초 GIF**: "노트를 골라 조합하면 어코드가 가장 비슷한 실제 향수를 추천하는 앱(데모는 가상 데이터)"
2. **문제**: 향수 입문자는 "이 향 조합이 어떤 향수와 비슷한지" 알기 어렵다. 공개 데이터는 어코드 비율이 없고(이름 5개 고정), 복사·오염 행이 섞여 있다.
3. **해결**: 노트 프로필(함께 든 노트 몫 빼기, 음수 없는 최소제곱) + 커뮤니티 강도. 앱 안에서 계산해 서버 없이 추천한다. 블라인드·사람 유사 시험으로 엔진을 골랐다(집계 수치만).
4. **구조도**(Mermaid): Expo 앱(웹·안드로이드·iOS 한 코드, 엔진 내장) ↔ Spring 서버(Render) ↔ Supabase(Postgres·Auth·비공개 Storage). 파이프라인(Python)은 카탈로그를 만들어 비공개 버킷에 올리고, 서버가 해시를 확인한 뒤 앱에 전달한다.
5. **기술 선택 이유**: Expo 한 벌(사장 결정, 1인 개발), 엔진은 앱 안(오프라인·서버 비용 0), Spring + JdbcClient(작은 메모리), Supabase 무료, Render 무료(512MB 실측으로 확인).
6. **테스트·레드팀·보안**
   - 테스트: 파이프라인 121 · 엔진(Vitest) 37 · 서버·DB 639(내장 Postgres, 실제 HTTP). 승인 테스트는 훅으로 보호하고, 변조 테스트(일부러 망가뜨려 잡는지)를 매 묶음 했다.
   - 훅: 실패 상태 커밋, 비밀값·`.env` 커밋, 강제 푸시, 승인 테스트 수정을 시스템으로 막는다. 민감한 파일을 고치면 레드팀 다시 돌리기 표시가 생긴다.
   - 비밀값 관리: 서버 전용 키는 Render 환경 변수에만 둔다. 앱에는 공개 키만 둔다. 전체 커밋 기록을 검사했고 비밀값은 없었다(레드팀 2026-11-11).
   - 위조 토큰만 막는 IP 제한: 레드팀이 "공유 IP 사용자까지 막힘"을 찾았다. 이를 고쳐 위조 토큰만 429를 주고, 만료·로그아웃 토큰은 세지 않는다. Render 실측으로 X-Forwarded-For 조작이 통하지 않음을 확인했다.
   - 계정 삭제 cascade: Supabase 관리 API → 외래 키 연쇄 삭제 → 세션 보관 즉시 지움 + 60초 "지워짐" 표시 → 남은 줄 0 확인 → 204. 실제 계정으로 확인했다.
   - 동시 요청 1번 처리: 사용자 단위 잠금으로 한도를 지킨다. 조합 99개 상태에서 25개를 동시에 보내도 1개만 성공한다. 같은 찜 20개를 동시에 보내도 1줄만 생긴다.
7. **측정 결과** (Render 무료 0.1 CPU / 512MB, 싱가포르)

   | 항목 | 값 | 기준 |
   |---|---|---|
   | 10분 전체 API 부하 중 프로세스 메모리 최고 | 208MB (컨테이너 216MB) | 420MB 이하 |
   | 서버 시작 | 26.5초 | — |
   | 잠든 뒤 첫 요청 | 33.6초 (묶음 1) · 43.5초 (2026-11-10) | 화면에 "연결 중" 안내 |
   | 응답 시간 — **서버 깨어 있을 때 단건 기준**(한국→싱가포르, 주소별 20번) | 중앙값 214~299ms, p95 307~465ms | — |

8. **Claude Code 팀 운영 방식**: 사장(사람)은 결정·검토만, 직원(Claude 팀: 기획·디자인·프론트·백엔드·QA)이 묶음 단위로 개발한다. 테스트 먼저 → 구현 → 분리 리뷰(QA 에이전트) + Gemini 교차 리뷰 → 레드팀 에이전트 → 사장 OK 순서다. HANDOFF·NOTES로 여러 컴퓨터를 오가며 이어 작업한다.
9. **데이터·라이선스**(1-7 문구), **직접 돌려 보기**((b)를 만들면)

### 4-2. 면접에서 설명할 포인트 5개
1. **데이터 품질을 숫자로 다룬 과정**: 복사 오류 333개, 오염 1,015개 제외, Decant 연결. 사람 유사 시험 Top 50 38.4% → 44.3%. 블라인드 결과가 유의하지 않았을 때(p=1.0) "개선됐다"고 말하지 않은 판단.
2. **"서버 없이 추천"이라는 구조 선택**: 엔진을 앱에 넣어 무료 서버의 콜드 스타트·메모리 한계를 피했다. 서버는 저장·로그인만 맡고, 512MB를 실측으로 검증했다.
3. **레드팀이 찾은 구멍과 고친 방식**: 보안 기능(IP 막기)이 오히려 공유 IP 사용자를 막는 서비스 방해가 됐다. 위조와 만료 토큰을 구분하는 방법(서명 확인 뒤에만 나는 예외)으로 고쳤고, 회귀 테스트와 변조 테스트로 확인했다.
4. **동시성·삭제의 정확성**: 한도·중복을 DB 잠금으로 한 번에 처리했다. 계정 삭제 중 먼저 출발한 요청이 "살아 있음"을 되살리는 틈을 찾아 막았다.
5. **AI 팀을 관리한 방식**: 사람은 결정만 하고, 훅과 승인 테스트로 AI가 규칙을 못 넘게 했다. 교차 리뷰(Claude + Gemini)와 사장 검토 지점을 문서로 운영했다.

---

## 5. 결정 일정

| 날짜 | 할 일 | 누가 |
|---|---|---|
| **11-11** | Parfumo(info@parfumo.com) 첫 메일 | 사장 (✓ 발송) |
| 11-11 | 1-3 ① Fragrantica 2곳·1-4 Parfumo 정리(✓), 단계 5 계획(`plan_stage5.md`) | 직원 |
| **11-14** | Fragrantica 추가 메일 | 사장 |
| **11-18** | Parfumo 추가 메일(그때까지 답이 없으면) | 사장 |
| 11-11~11-24 | 단계 5 공통 작업(사장 OK 뒤). keepalive는 결정까지 보류. 테스트 계정 삭제·이메일 끄기는 단계 5 끝(B) 또는 출시 직전(A) | 직원 |
| **11-25** | **Plan A/B 결정**: Fragrantica와 Parfumo **둘 다** 허가면 Plan A, 아니면 Plan B. A면 keepalive main 반영(Render health 단계) | 사장 (+ 직원) |
| Plan B 확정 뒤 | 3-1 "Plan B에서 새로 생기는 일", 1-5 걸러낸 사본으로 새 공개 저장소, perfumer-lab 비공개 전환. 테스트 계정 삭제 → 이메일 끄기는 **단계 5 끝** | 직원 + 사장 |

### 허가가 오면 Plan A로 돌아가는 법 (다시 켤 것)
0. 조건: Fragrantica와 Parfumo **둘 다** 허가. 하나만 오면 Plan B 유지 + 받은 쪽 조건만 기록
1. 허가 메일 내용(조건: 출처 표기·비상업 등)을 `docs/NOTES.md`·출처 화면 문구에 반영
2. 카카오 로그인 재시작: 비즈 앱 전환부터(`kakao_setup.md` "카카오 로그인 보류")
3. 앱 안 카탈로그를 실제 배포본(2026.11.05 엔진 8)으로 교체(남은 것 7)
4. **출시 직전**에 테스트 계정 삭제 → 이메일 새 가입 막기(위 훅 또는 Email 끄기) → `api_check.sh`로 확인
5. keepalive main 반영(Render health 단계) — 11-25 Plan A 결정 당일
6. Play 개발자 계정 → 비공개 테스트(15명 초대, 14일) → 공개 신청. 구글 OAuth 프로덕션, Redirect URLs, targetSdk 36
7. Plan B용으로 만든 것(샘플 카탈로그·데모 모드·Pages)은 지우지 않는다. 포트폴리오용으로 계속 쓸 수 있다
8. 1-3 ① 정리는 이미 했다면 그대로 둔다(허가와 상관없이 공개 문서에 향수별 수치는 안 싣는 편이 깔끔하다)

> 회신이 **거절**이면 Plan B에 더해 할 일이 있다. 앱 안에서 Decant 강도를 빼야 하는지 다시 판단해야 한다(spec D4: 거절 시 강도 데이터 제거 → v7·v8 개선 잃음). Plan B는 실제 데이터를 공개하지 않으니 영향은 작다. 다만 "실제 데이터로 개발·평가했다"는 README 문구는 회신 내용에 맞게 고친다.
