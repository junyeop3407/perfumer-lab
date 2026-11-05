# scent-studio
현재 단계: 4 (구현 — docs/plan_v8.md: 8단계 블라인드 채점 완료, 유의차 없음, 9단계 시작 전 사장 지시 대기)
한 줄 설명: 탑·미들·베이스 노트를 골라 조합하면, 어코드 비중이 가장 비슷한 실제 향수를 추천하는 앱(Perfumer-LAB). 웹·안드로이드·아이폰 공통 (Expo/React Native 앱, Spring 서버, 데이터 작업은 Python)
실행 방법: 아직 화면 없음 (뼈대만: `cd app && npx expo start --web`)
테스트 실행: `bash tests/run.sh`
리뷰 기준: a92de1c
결과: 파이프라인 102/102 · TS 엔진(Vitest) 37/37 통과 (2026-11-05 확인). **승인 목록 1/41** (`cd app && npx playwright test`, 2026-11-05 직접 확인. 화면 전이라 40개 실패는 정상 — run.sh에는 41/41이 되면 넣는다)
사용자에게 남은 질문: 확신 낮은 판정 5개 중 5번째 번호(지시에 13, 16, 19, 26만 있음), 9단계 시작 지시
다음 할 일: 사장 지시 뒤 눈 평가(9단계) → 등급(10단계)

## ★ 지금 멈춘 곳 (2026-11-05)
- **8단계 블라인드 채점 완료: 유의차 없음(v8 13 : v7 13, 유효 26, 양측 부호 검정 p=1.000).** 규칙대로 **v8 유지**, "블라인드 비유의"를 위험에 기록했다(`docs/plan_v8.md` 위험). v8이 더 낫다는 근거가 나온 것은 아니다. **9단계는 시작하지 않았다. 사장 지시를 기다린다.**
  - 판정 32개: A 12, B 14, 비슷함 6. 자동 비슷함 8개는 따로다.
  - 분류별 v8 : v7 — 프레시 3:4, 플로럴 5:2, 우디 오리엔탈 4:2, 구르망 1:5. 분류별 표본이 작아 참고용이다.
  - **판정자 변경:** 사장 결정으로 사람 → 채팅 AI(`docs/engine_blind_v8_rules.md` "판정자 변경 (채점 전)", 정답 키를 열기 전 커밋 `c9453c4`). 한계는 위험 목록에 있다.
  - **확신 낮은 판정:** 지시받은 번호는 13, 16, 19, 26 네 개다. 이 넷을 빼면 11:11, p=1.000. 5번째가 어느 번호든 유의차 없음은 그대로다(10:11, 11:10, 11:11).
- **블라인드 파일:** 규칙 `docs/engine_blind_v8_rules.md`, 조합 `docs/engine_blind_v8_combos.json`, 채점 도구 `pipeline/experiments/blind_v8_score.py`. 판정 채운 시트 `pipeline/data/out/blind_v8_sheet.md`는 커밋하지 않고 비공개 저장소 `blind_v8/sheet_judged.md`에 백업했다.
- **정답 키는 비공개 저장소 `blind_v8/answer_key.json`에만 있다.** 이 컴퓨터에 파일로 받지 않고 대화에 출력하지 않는다. 채점은 표준 입력으로만 읽었다. `blind_v8_make.py sheet`는 다시 돌리지 않는다.
- **7단계 완료: 엔진 v8 적용.** P1은 `pipeline/estimate.py`, 강도 없는 향수의 추정값에는 `estimatedAccords`·`estimatedNotes` 표시. 앱 계산은 그대로다. v8 카탈로그 파일로 잰 값이 메모리 측정과 같음(W 42.8, 사람 유사 -2.5, `docs/engine_v8_check.md`).
- **v7 비교본:** `pipeline/data/out/catalog_v7_compare.json.gz`(정리 데이터 + 옛 연결, `save_v7_compare.py`). 8단계 블라인드의 v7 쪽으로 쓴다. 커밋 안 함, 비공개 백업에 있음.
- 정리 확정값: **후보 12,469, 제외 키 1,081, 표시 40.** 기준 목록은 `pipeline/manual/{filtered,review}_keys.csv` (`pipeline/experiments/freeze_exclusions.py`). `merge_keys.csv`는 비어 있고(예외 없음), `separate_keys.csv`는 겹침 규칙으로 대체됐지만 남겨 둔다.
- **측정 기준: 쿼리 893 / 남겨 둔 절반 474가 새 기준이다.** ★6 표(882/466)는 쿼리 집합이 달라 참고용이고 직접 비교하지 않는다. 지금 표는 `docs/engine_v8_final.md`, `docs/engine_v8_check.md`. 쿼리 목록은 `pipeline/data/out/queries_v8.json`.
- ★6 결정(2026-11-04): P1 채택, M1 제외, 맹점 공식 4/5 통과. P1-half는 블라인드 미달 시 예비안.

### 미뤄 둔 항목
- **P1 전체 재학습 (10단계 이후 필요하면 검토):** 지금은 개발 절반으로만 학습한다(사장 결정). 남겨 둔 절반을 평가용으로 깨끗하게 두기 위해서다.

### 미뤄 둔 항목 (화면 단계)
- **카탈로그 크기 줄이기:** gzip 압축 후 2,287KB, 풀면 10.7MB (v7 비교본 압축 1,574KB, 이전 기록 1,660KB). 추정치를 넣어 늘었다. 앱 용량과 함께 본다.
- **같은 해 분리 행:** 대표와 출시 연도가 같은 분리 행은 농도 차이일 수 있어 추천에 두 번 나올 수 있다. 같은 브랜드 + 기본 이름을 묶어 보여 준다.
- **승인 목록 H1·H2 글자 잘림:** 하단 탭 글자가 잘리는 기존 문제다(629b522에서도 같은 위치·같은 글자로 실패). 화면 단계에서 고친다.
- **데이터 표기 문제 (사장 지적, 2026-11-05):** ① 향수 이름에 브랜드·연도·농도가 붙어 중복 표기된다(이름+브랜드+연도+농도 형태). ② 번역 안 된 노트 이름이 남아 있다(영어 그대로 나오는 노트들). ③ 같은 노트가 여러 층에 반복되는 행이 있다.
- 저장소는 공개 유지(사장 결정). 정리 목록은 비밀 키를 섞은 HMAC이고, 예전 커밋은 그대로 둔다. **비밀 키 `SCENT_KEY_SECRET`은 작업한 컴퓨터의 사용자 환경 변수에 있다.** 비밀번호 관리자에 옮겼는지는 아직 확인받지 못했다. 다른 컴퓨터에서는 같은 키를 설정해야 실제 카탈로그를 만들 수 있다.

### 이어서 시작할 때
- 사장의 9단계 지시를 받은 뒤 진행한다. 지시 없이 시작하지 않는다.
- `freeze_exclusions.py`는 정리 전 카탈로그 `pipeline/data/out/catalog_precleanup.json.gz`를 읽는다. `catalog.json.gz`는 이제 v8이다.
- 보류 목록의 합치기 분리 후 제외 4개는 `pipeline/manual/hold_keys.csv`에서 읽으므로 `freeze_exclusions.py`를 다시 돌려도 남는다. (손으로 다시 넣으라던 예전 메모는 이 수정으로 대체됐다.)
- 옛 실험 스크립트는 v8 카탈로그를 읽으면 경고하고 멈춘다(`catalog_rules.load_old_catalog`). v7 기준 비교는 `catalog_v7_compare.json.gz`를 쓴다.
- 다시 돌리는 법: `py pipeline/experiments/engine_v8_final.py`(메모리 측정), `py pipeline/experiments/engine_v8_check.py`(v8 파일 확인). 이름이 든 표는 `pipeline/data/out/`(커밋 안 함): `final_traces.txt`, `exclusion_changes.csv`, `merged_rep_excluded.csv`, `hold_list_web_check_10.csv`, `hold_list_ambiguous_pass.csv`, `number_not_in_slug.csv`. 다른 컴퓨터에서는 비공개 저장소 `junyeop3407/perfumer-lab-private`에서 받아 이 폴더에 복사한다. 자동으로 맞춰지지 않으니 표가 바뀌면 다시 올린다(마지막 백업 2026-11-05).
- `decant_audit.py`는 기록용이다. 다시 돌리지 않는다.
- **v8 뒤 할 일:** 이름 오류 복원(유명 향수가 카탈로그에서 빠진 상태일 수 있음), 보류 목록 14개(웹 재출시작 확인 후보 10개 + 합치기 분리 후 새로 제외된 4개) 확인, 복구 후 오염 규칙을 통과한 2개 처리.

## 새 세션 시작 (이 순서대로)
1. 브랜치: **`wip/scent-studio-stage4`** (main 아님. 승인 41/41 + 5단계 리뷰 뒤 main에 합침) — `git fetch && git switch wip/scent-studio-stage4 && git pull`
2. 읽기: 이 파일 → `docs/spec.md`(앱 전체) → `docs/plan.md`(4단계 계획·진행 표) → 필요하면 `docs/NOTES.md` 맨 아래(최근 결정과 이유)
3. 새 컴퓨터 준비(한 번):
   - `py -m pip install -r pipeline/requirements.txt` (numpy, pytest)
   - `cd app && npm install && npx playwright install chromium`
   - 비밀 키: 환경 변수 `SCENT_KEY_SECRET` (저장소 README "데이터 정리 목록의 비밀 키"). 없으면 실제 카탈로그를 만들 수 없다. 테스트 1개는 키와 원본이 있을 때만 돈다
   - 원본 데이터(커밋 안 함): `py pipeline/fetch.py` → `py pipeline/fetch_decant.py` → `py pipeline/build_catalog.py`
4. 확인: `bash tests/run.sh` → 파이프라인 84 + Vitest 37 (키와 원본이 없으면 일부 건너뜀) 통과

## 지금 상태 (2026-11-02)
- **엔진 v7** (사장 승인, 계산은 `docs/engine_how_it_works.md`): 어코드·노트 강도(Decant, 후보의 51.7%), 노트 일치도 × (0.3 + 0.7·√강도 몫 합), 등급은 선호와 무관. 사람 유사 시험 Top 50 38.4% → 44.3% (`docs/engine_similar_votes.md`). 재현 20/20(100개 100·97·99) · 맹점 4/5 · 다양성 92.
- **블라인드 문서** (아래 v6 설명보다 이것이 우선. 조합 12개, 답은 조합 단위): 주 비교 `docs/engine_blind_vs_v6.md` → 답 `docs/engine_blind_vs_v6_answers.txt` / 기준선 `docs/engine_blind.md` → 답 `docs/engine_blind_answers.txt`. 기준선의 5·6·7·9·11·12번이 주 비교와 겹치니 **주 비교 답을 받은 뒤에** 기준선을 보여 준다. 재생성은 `py pipeline/evaluate.py --compare-v6 --rebuild-blind` (답 받기 전에는 하지 않는다).
- **비상업 운영, 내부 테스트까지** (D4). Decant는 CC BY-NC-SA 4.0. Fragrantica는 허가 요청 메일 회신 대기.
- **사장에게 알려 둔 한계**: 강도 있는 향수로 쏠림(Top 10의 81%, v6 58%), 등급이 대부분 참고(82%, 기준값 임시 0.87/0.81). 눈으로 평가 때 함께 본다.

### 아래는 v6 때의 기록
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

## 2026-11-02 세션 기록
- 사장 요청으로 엔진 계산 설명서 `docs/engine_how_it_works.md`를 썼다(예시 숫자: `pipeline/experiments/explain_example.py`). 엔진 동작은 그대로. 블라인드 답은 아직 없다.
- 설명서를 쓰다 찾은 확인 거리: ① 선호 계열을 켜면 점수가 낮아져 등급이 내려갈 수 있음 → v7에서 해결 ② 어코드 "목록이 진한 순서" 가정의 사이트 대조 기록을 찾지 못함 → 맨 위 "남은 확인 사항" 참고.
- 사장이 쓴 Claude 문서 "조향 추천 엔진 설계서 v2"(https://claude.ai/artifact/3FPLdg3ZG1ETHVMoYScVKM)의 방안을 시험판으로 돌렸다. 결과 `docs/engine_v2_trial.md`, 스크립트 `pipeline/experiments/engine_v2_trial.py`. **엔진·카탈로그·승인 테스트·골든·블라인드 문서는 그대로.**
  - 어코드·노트 강도 + "지금 일치도 × 강도 몫" 시험판이 사람 유사 시험 Top 50 38.4% → 44.3% (남겨 둔 절반, 구간 +2.8~+9.1). 기존 기준 통과.
  - 사장이 v2 진행을 결정해 계획 모드 승인 후 엔진 v7로 옮겼다. 위 "지금 상태" 참고. 받은 데이터: `pipeline/data/raw/decant/` (커밋 안 함, `fetch_decant.py`).

## 다음 세션에서 할 일
0. (끝남, 2026-11-04: 이름 든 전체는 `pipeline/data/out/`, docs에는 요약) **보류 목록 50개와 검증 표 20개를 어디에 둘까?** 저장소가 공개라 향수 이름·평가 수를 공개 목록에 싣지 않는 규칙(67a9e6d)과 "docs에 남기기" 요청이 부딪힌다. 추천: 이름이 든 전체는 커밋 안 하는 `pipeline/data/out/`에 두고, docs에는 이름 없는 요약만. 검증 표 20개는 대화로 보여 준다.
1. (끝남) **오염 단어 2단계 분류표**를 만들어 사장에게 먼저 보여 준다(지금 78개는 `pipeline/experiments/engine_v8_trial.py`의 `FOUL`).
   - 확실한 가짜(노트로 있을 수 없는 단어: sewer, nausea, carcass, rotten egg, zarquon 등): 1개라도 있으면 행 제외.
   - 애매한 단어(실제 니치 노트로 쓰이는 것: tomato leaf, dust, chlorine, melting plastic 등): 단독으로는 제외하지 않고, 그 행의 어코드와 노트가 어긋나는지(예: Oud Wood인데 우드 없음) 함께 보고 판단. **판단 규칙은 표와 함께 제안한다.**
   - 주의: zarquon은 이미 `junk_notes.csv`로 빠지는 단어이고, tomato leaf는 지금 78개에 없다. 분류표에서 이 둘의 처리를 밝힌다.
2. (끝남) **보류 목록**: 제외된 행 중 평가 수 상위 50개를 남긴다(나중에 다른 출처로 복원할 후보). 위치는 0번 답대로.
3. (끝남) **검증**: 제외 대상에서 무작위 20개를 뽑아 이름·브랜드·노트 표로 사장에게 보여 준다(사장이 따로 확인). 원본에 **의도적으로 넣은 가짜 행일 가능성**을 `docs/NOTES.md`에 기록한다.
4. (끝남) **정리된 데이터로 재측정**: M1, P1, M1+P1, **P1-half(보정 강도 절반)**. 강도 보유 목표치도 정리된 데이터 기준으로 다시 계산한다(지금 추정 약 55.5%).
   - P1에서 맹점이 3/5로 떨어지는 케이스가 무엇인지 찾는다.
   - M1 적용 후에도 Kaleidoscope가 민트·라벤더·시더우드 1위인 이유를 점수 구성(어코드/노트 기여)으로 분해한다.
5. (끝남, 2026-11-04) 재측정 보고와 v8 계획 승인. 이어서는 `docs/plan_v8.md`.
6. 승인 뒤: v8 구현 → 측정 → 새 조합 20개 이상 블라인드(A/B/비슷함, 비슷함은 집계 제외, 승률 + 이항검정 p값, 프레시·플로럴·우디 오리엔탈·구르망 고르게) → 눈으로 평가 → 등급 확정.
7. 그 뒤: Tversky 계수, TGSC 비중, 노트 상호작용 → `docs/plan.md` 단계 3(DB 구축) → 4(서버) → ★디자인 → 5(화면) → 6. 스킬 설치 답은 디자인 단계 전까지 받는다.

- **사장 몫(재촉하지 않음):** `docs/engine_accord_order_check.md` 20건 사이트 대조는 **보류**, 나중에 사장이 직접 한다. Parfumo는 자동 접속을 403으로 막으므로 우회하지 않는다. Fragrantica 회신도 대기 중이고, 그 전까지 배포는 초대제 내부 테스트까지.
- **아직 안 본 것:** Eau de Nuit의 '스위트' 태그와 노트가 어긋나는지(사장 근거 글에서 나온 건).
- 기준선 블라인드 `engine_blind.md`는 받지 않았다. v8 뒤 새 블라인드로 대신할지 그때 정한다.

## 사장 결정 기록 (짧게, 자세한 이유는 NOTES.md)
- 2026-11-05 사장 결정: 합치기에 노트 겹침 검사 추가(대표와 0.5 미만이면 합치지 않음). 예외 없음(한 행을 합친 채 두려던 결정은 잘못 보고된 겹침 값 때문이라 철회). 쿼리 893/474를 새 기준으로, ★6 표는 참고용으로. `separate_keys.csv`는 지우지 않는다.
- 2026-11-04 사장 결정 (2차): 오염 1,015개 제외 확정. 복원은 v8 뒤로. 의심 행은 표시만. 검증의 기본은 Decant 대조, 단어 목록은 보조. 애매한 단어 행은 어코드·노트 어긋남 대신 Decant 대조로 판정.
- 2026-11-04 사장 결정:
  - v7을 기준 엔진으로 채택. 다음 블라인드는 새 조합 20개 이상, A/B/비슷함, 승률 + 이항검정 p값, 계열 고르게.
  - 오염 의심 행은 **행 제외가 기본**, 단어는 2단계(확실한 가짜 / 애매한 단어)로 나눠 애매한 단어는 어코드·노트 어긋남과 함께 판단. 평가 수 상위 50개는 보류 목록, 무작위 20개는 사장 검증.
  - 등급은 **(b′) 무작위 분포 기준**으로 간다. 엔진이 바뀌면 다시 계산하고, 확정은 v8 눈으로 평가 뒤.
  - 브랜드 상한은 새로 넣지 않는다(통계상 쏠림과 무관). 수익 기능을 붙이면 비상업 운영 결정을 다시 검토한다.
  - Parfumo 20건 대조는 우회하지 않고 보류, 사장이 직접 확인.
- 2026-10-31 웹 + 안드로이드 + 아이폰, 설계 E안(Expo). 앱 이름 Perfumer-LAB(향수 공방소), 앱 ID com.perfumerlab.scentstudio. 4단계 계획 승인(DB 구축 단계 포함).
- 2026-11-02 엔진 v7로 진행. 어코드 연결표 OK. 비상업 운영. 등급은 선호와 무관.
- 2026-10-31 엔진: 레몬 '강' 문제 → 특징 강조 + 강 2.0(v3) → 어코드 0.35 : 노트 0.55(v4) → 함께 든 노트 몫 빼기 + 복사 오류 제외(v5) → 강 3.0, 어코드 쪽에만(v6). 거의 복사도 제외. 블라인드는 조합 단위 답, 주 비교는 v6 vs v3.
- 블라인드·눈으로 평가에 Claude가 매긴 임시 답(claude-provisional)은 v3 문서용으로 보관만 하고, v6 판정에는 쓰지 않는다.

## 주요 파일
- 앱: `app/` (Expo Router 화면 `app/app/`, 엔진 `app/src/engine/`, 이름표 `app/src/ui/testIds.ts`, 승인 테스트 `app/tests/acceptance/`, 부품 테스트 `app/tests/unit/`)
- 데이터·검증: `pipeline/` (build_catalog, engine_ref, evaluate, make_golden, experiments/, tests/), 보고서 `pipeline/report.md`, 복사 오류 목록 `pipeline/copy_suspects.csv`
- 문서: `docs/requirements.md`(기획) · `docs/design.md`(설계, 엔진 8절) · `docs/spec.md`(명세) · `docs/tests.md`(승인 목록 41) · `docs/plan.md`(4단계 계획) · `docs/NOTES.md`(고친 이유·결정) · `docs/engine_how_it_works.md`(엔진 계산 설명) · `docs/engine_*.md`(엔진 실험·기준표·블라인드)
- 가짜 데이터·골든: `tests/fixtures/`, `tests/golden/recommend_cases.json`
- 포트폴리오용 문제 해결 기록: 저장소 루트 `portfolio/problem-solving.md`
- 복사 오류 목록: 공개 `pipeline/copy_suspects.csv`(향수 이름·평가 수 없음) / 전체는 `pipeline/data/out/copy_suspects_full.csv`(커밋 안 함, `build_catalog.py`가 만듦)
