# scent-studio
현재 단계: 4 (구현 — docs/plan.md "★엔진 마무리": 엔진 v6 완료, 사장 블라인드 답·눈으로 평가 점수 대기)
한 줄 설명: 탑·미들·베이스 노트를 골라 조합하면, 어코드 비중이 가장 비슷한 실제 향수를 추천하는 앱(Perfumer-LAB). 웹·안드로이드·아이폰 공통 (Expo/React Native 앱, Spring 서버, 데이터 작업은 Python)
실행 방법: 아직 화면 없음 (뼈대만: `cd app && npx expo start --web`)
테스트 실행: `bash tests/run.sh`
리뷰 기준: a92de1c
결과: 파이프라인 21/21 · TS 엔진(Vitest) 36/36 통과 (2026-10-31 확인). **승인 목록 1/41** (`cd app && npx playwright test`, 화면 전이라 40개 실패는 정상 — run.sh에는 41/41이 되면 넣는다)
사용자에게 남은 질문: 블라인드 답(주 비교 v6 vs v3, 기준선 v6 vs 노트만) → 눈으로 평가 점수 → 등급 기준값 (아래 "다음 세션에서 할 일")
다음 할 일: 아래 "다음 세션에서 할 일" 1번부터

## 새 세션 시작 (이 순서대로)
1. 브랜치: **`wip/scent-studio-stage4`** (main 아님. 승인 41/41 + 5단계 리뷰 뒤 main에 합침) — `git fetch && git switch wip/scent-studio-stage4 && git pull`
2. 읽기: 이 파일 → `docs/spec.md`(앱 전체) → `docs/plan.md`(4단계 계획·진행 표) → 필요하면 `docs/NOTES.md` 맨 아래(최근 결정과 이유)
3. 새 컴퓨터 준비(한 번):
   - `py -m pip install -r pipeline/requirements.txt` (numpy, pytest)
   - `cd app && npm install && npx playwright install chromium`
   - 원본 데이터(커밋 안 함): `py pipeline/fetch.py` → `py pipeline/build_catalog.py`
4. 확인: `bash tests/run.sh` → 파이프라인 21 + Vitest 36 통과

## 지금 상태 (2026-10-31)
- **단계 1·2 끝**: Expo 뼈대(`app/`) + 승인 테스트 41개(`app/tests/acceptance/`, 지금 1/41), TS 엔진(`app/src/engine/`, Python 골든과 일치). Java 엔진·Kotlin 앱은 지웠다.
- **엔진 v6** (사장 결정 순서대로, 이유·수치는 `docs/NOTES.md`):
  - 노트 프로필 = **함께 든 노트의 몫 빼기**(향수 어코드 ≈ 노트 프로필 평균, 음수 없는 최소제곱, numpy). 향수 20개 미만 노트는 계열 평균.
  - 점수 = **어코드 0.35 : 노트 0.55** : 선호 계열 0.1. **강 3.0**(어코드 쪽에만), 약 0.5, 중 1.0.
  - 원본 데이터셋 **복사 오류 제외**: 브랜드가 다른데 노트·평가 수·어코드·조향사가 같은 묶음(+평가 수만 1~5 다른 거의 복사)은 원본만 남기거나 묶음째 뺌 → 333개 제외, `pipeline/copy_suspects.csv`.
  - 같은 향수 묶음(group): 이름이 같아도 노트가 절반 넘게 다르면 다른 향수.
  - 등급 기준값은 **아직 0.87 / 0.79 그대로**(사장 결정 대기, 같은 규칙으로 재면 0.92 / 0.87).
- 기준표 `docs/engine_report.md`: 재현 20/20(어려운 버전 20개 20·20, 100개 99·98·97) · 맹점 4/5(시트러스 1위 2) · 다양성 97.
- 블라인드 문서 두 개 (둘 다 조합 12개, 답은 **조합 단위** `블라인드: 1A 2B … 12A`, 일부 조합만 답해도 집계):
  - **주 비교** `docs/engine_blind_vs_v3.md` (지금 엔진 v6 vs 예전 엔진 v3) → 답 파일 `docs/engine_blind_vs_v3_answers.txt`
  - **기준선** `docs/engine_blind.md` (지금 엔진 vs 노트만 보는 방식) → 답 파일 `docs/engine_blind_answers.txt` (+ 눈으로 평가 `점수: 1=4 11=3 …`)
  - ⚠️ 두 문서는 1~6번 조합(기준선 문서의 1·2·3·4·8·10번)이 겹친다. 나란히 보면 A/B가 드러나므로 한 문서씩 따로 답하게 안내한다.
  - 두 문서는 사장 답을 받기 전에 **다시 만들지 않는다**. `py pipeline/evaluate.py`는 블라인드 문서가 이미 있으면 덮어쓰지 않으니 그냥 돌려도 된다(다시 만들 때만 `--rebuild-blind`). 줄별 A/B 고정본: `engine_blind_v6_pairs.json`, `engine_blind_vs_v3_pairs.json`.
  - 예전 v3 문서와 Claude 임시 답안은 `engine_blind_v3.md`, `engine_blind_answers_v3.txt`로 보관(지우지 않음).

## 2026-11-01 세션 기록 (사장이 "이어서"라고 하면 여기부터)
- 사장이 깃허브에서 한 작업(이 브랜치 22커밋)을 받아 확인했다. 브랜치는 그대로 `wip/scent-studio-stage4`.
- ai-workflow 견본의 최신 규칙(d88021a까지)을 반영했다: 웹·앱 동시 제작(React+Capacitor), **스킬 사용 규칙** 표, 보안 규칙, 6단계 레드팀 공격. 이 저장소에만 있던 "안드로이드 앱 전용" 규칙은 뺐고, scent-studio는 Expo 유지라고 CLAUDE.md에 적었다.
- 클라우드 세션에서 확인한 스킬: 있음 — awesome-design, hallmark, emil-design-eng, ponytail, web-design-guidelines, break-ui, junyeop-clean-ppt, code-review. **없음** — superpowers, agent-reach, graphify, frontend-design, webapp-testing, security-guidance, security-audit, **Gemini CLI(5단계 교차 리뷰 필수)**.
  - 사장에게 보기 3개를 드렸고 아직 답 없음: ① 클라우드 환경 Setup script에서 `junyeop3407/claude-setup`의 `install.sh` 실행(추천) ② agent-reach·webapp-testing을 claude.ai 스킬로 올리기 ③ 디자인·화면 단계 전까지 보류. 블라인드 평가에는 필요 없다.
- 이어서 할 일: 아래 1번(주 비교 블라인드 답 받기)부터. 스킬 설치 답은 디자인·화면 단계 전까지 받는다.

## 다음 세션에서 할 일
1. **사장에게 주 비교 블라인드 답 받기**: `docs/engine_blind_vs_v3.md`를 보여 주고 조합 단위 답을 받는다(일부만 가능). 답을 `docs/engine_blind_vs_v3_answers.txt`에 적는다:
   ```
   출처: 사장
   블라인드: 1A 2B 3A …
   ```
   그다음 `py pipeline/evaluate.py` → 기준표 3절(주 비교)에 집계. 판정 기준: 12개 중 8개(60%) 이상 지금 엔진 쪽. 판정은 모든 조합에 답한 뒤에만.
2. **기준선 블라인드 답**: `docs/engine_blind.md` → `docs/engine_blind_answers.txt`에 같은 형식(`출처: 사장`, `블라인드: …`).
3. **눈으로 평가**: 블라인드 답을 받은 조합만 기준표 4절에 지금 엔진 Top 5가 나온다. 사장에게 보여 주고 1~5점을 받아 `engine_blind_answers.txt`의 `점수: 1=4 2=3 …`(일부만 가능)에 적고 `py pipeline/evaluate.py`. 기준: 평균 3.5 이상.
4. **등급 기준값**: 점수가 들어오면 기준표 6절 "등급 기준값 후보" 표(0.87/0.79, 0.89/0.82, 0.90/0.84, 0.92/0.87)가 채워진다. 사장이 고르면 `pipeline/engine_ref.py` GRADE_*·`app/src/engine/params.ts` GRADE_*·`ENGINE_VERSION` 7·설계서 8-6절을 함께 바꾸고 `py pipeline/build_catalog.py --fixture && py pipeline/build_catalog.py && py pipeline/make_golden.py` → `bash tests/run.sh`.
5. 기준을 다 넘으면 ★엔진 마무리 끝 → `docs/plan.md` 단계 3(DB 구축) → 4(서버) → ★디자인 → 5(화면) → 6.
   - 못 넘으면 원인을 찾아 사장과 다시 정한다(엔진 실험 스크립트는 `pipeline/experiments/`).

## 사장 결정 기록 (짧게, 자세한 이유는 NOTES.md)
- 2026-10-31 웹 + 안드로이드 + 아이폰, 설계 E안(Expo). 앱 이름 Perfumer-LAB(향수 공방소), 앱 ID com.perfumerlab.scentstudio. 4단계 계획 승인(DB 구축 단계 포함).
- 2026-10-31 엔진: 레몬 '강' 문제 → 특징 강조 + 강 2.0(v3) → 어코드 0.35 : 노트 0.55(v4) → 함께 든 노트 몫 빼기 + 복사 오류 제외(v5) → 강 3.0, 어코드 쪽에만(v6). 거의 복사도 제외. 블라인드는 조합 단위 답, 주 비교는 v6 vs v3.
- 블라인드·눈으로 평가에 Claude가 매긴 임시 답(claude-provisional)은 v3 문서용으로 보관만 하고, v6 판정에는 쓰지 않는다.

## 주요 파일
- 앱: `app/` (Expo Router 화면 `app/app/`, 엔진 `app/src/engine/`, 이름표 `app/src/ui/testIds.ts`, 승인 테스트 `app/tests/acceptance/`, 부품 테스트 `app/tests/unit/`)
- 데이터·검증: `pipeline/` (build_catalog, engine_ref, evaluate, make_golden, experiments/, tests/), 보고서 `pipeline/report.md`, 복사 오류 목록 `pipeline/copy_suspects.csv`
- 문서: `docs/requirements.md`(기획) · `docs/design.md`(설계, 엔진 8절) · `docs/spec.md`(명세) · `docs/tests.md`(승인 목록 41) · `docs/plan.md`(4단계 계획) · `docs/NOTES.md`(고친 이유·결정) · `docs/engine_*.md`(엔진 실험·기준표·블라인드)
- 가짜 데이터·골든: `tests/fixtures/`, `tests/golden/recommend_cases.json`
