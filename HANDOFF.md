# HANDOFF — 다음 세션 시작 가이드

마지막 갱신: 2026-11-10 세션 끝 (scent-studio 묶음 8 계정 삭제 완료·실제 삭제 확인, V3 실제 적용, Render 6694790(묶음 6+7) 배포·확인, 카카오 로그인 ⏸ 보류 — 다음: 묶음 9. 묶음 8 이후 코드는 Render 미배포) / 2026-11-09 세션 끝 (scent-studio 묶음 7 코드 완료·V3 실제 DB 적용 전 멈춤) / 2026-11-08 세션 끝 (scent-studio 묶음 6 완료·묶음 7 전 멈춤. 다음: 사장 할 일 확인 → launch_checklist 프롬프트 A → 카탈로그 배포 → 묶음 7) / 2026-11-08 (scent-studio 묶음 6 코드 완료, 실제 카탈로그 배포 OK 대기·묶음 7 전 멈춤) / 2026-11-08 (scent-studio 묶음 5 완료, 묶음 6 전 멈춤 — 사장이 서버 전용 Secret key 만들기) / 2026-11-07 (scent-studio 묶음 1~4 완료, 세션 끝. 다음: 묶음 5, 묶음 6 전에 멈춤) / 2026-11-06 (scent-studio: Windows·Mac 둘 다 세팅 완료, 실측 대기) / 2026-11-06 (scent-studio 단계 4 묶음 1: Render 배포 완료, 실측 대기, 새 컴퓨터 설정 문서) / 2026-11-05 (10단계 등급 확정, 엔진 v8 동결, 단계 3 시작 전 사장 지시 대기) / 2026-11-05 (9단계 눈 평가 합격 4.38, 10단계 시작 전 사장 지시 대기) / 2026-11-05 (9단계 눈 평가 시트 준비 완료, 사장 점수 대기) / 2026-11-05 (8단계 블라인드 완료, 유의차 없음, v8 유지, 9단계 시작 전 사장 지시 대기) / 2026-11-05 (엔진 v8 적용, 8단계 블라인드 시작 전, 사장 지시 대기) / 2026-11-05 (합치기 겹침 검사 반영) / 2026-11-04 (v8 ★6 결정 완료, 7단계 시작 전, 사장 답 대기) / 2026-11-04 (오염 행 제외 확정, 재측정 완료) / 2026-11-04 (v7 채택, v8 후보 측정) / 2026-11-02 (엔진 v7 완료: 어코드·노트 강도, 비상업 운영) / 2026-11-01 (ai-workflow 견본 최신판 반영: CLAUDE.md를 rules·skills·hooks·agents로 나눔, /handoff 스킬, guard-git·protect-tests 훅, test-runner·qa-reviewer·red-team 에이전트)

## 1. 한 줄 요약
이석균 선배님 특강(AI 기초)의 방식대로 앱을 만드는 **작업장 견본**이다. 사용자는 **사장**, Claude의 팀들(기획·디자인·프론트엔드·백엔드·QA)은 **직원**이고, 모든 팀의 결과물은 사장 검토를 받는다. 규칙, `/build-app` 스킬, 교차 리뷰, 훅이 준비되어 있다. **진행 중인 앱: `apps/scent-studio`(향 조합 → 비슷한 향수 추천, 웹·안드로이드·아이폰 공통 Expo 앱) — 4단계 구현 중, v8은 P1 채택·M1 제외로 결정, 합치기 겹침 검사 반영, 엔진 v8 적용 완료, 8단계 블라인드 유의차 없음(v8 유지), 9단계 눈 평가 합격, 10단계 등급 확정, 엔진 v8 동결, 단계 3(DB) 완료, 단계 4(서버) 묶음 1~6 완료(V2 실제 DB 적용, 로그인·요청 제한, 취향·동의, 조합·찜·시향 기록, 사용 기록·카탈로그 코드, f51a886·84df7b4), 묶음 7 오류 신고(V3 실제 DB 적용·Render 6694790 배포, 2026-11-10), 묶음 8 계정 삭제 완료(실제 삭제 확인, Render 미배포), 카카오 로그인 ⏸ 보류(`apps/scent-studio/docs/kakao_setup.md`). 2026-11-09 실제 카탈로그 배포·진짜 Storage 확인, Render에 묶음 6 코드 배포(892a79f). 2026-11-08 사장 결정: 1차 기능 7개 추가(카카오 로그인·로그아웃·약관·개인정보·만 14세 동의 화면·데이터 출처·라이선스 화면·"추천이 이상해요" 신고·첫 실행 안내·앱 정보), Play 비공개 테스트(12명 이상·14일) 뒤 공개, 화면 세로 고정 — 문서 반영 완료(2026-11-09 프롬프트 A, 승인 목록 56개). 앱 안 카탈로그 파일은 옛 버전(2026.10.31, 엔진 6)이라 단계 5에서 교체. 이어 할 때는 브랜치 `wip/scent-studio-stage4`로 바꾸고(`git switch wip/scent-studio-stage4`) `apps/scent-studio/HANDOFF.md`의 "★ 지금 멈춘 곳" → "다음 세션에서 할 일"부터** (2026-11-05).

## 2. 새 세션 시작 방법
- 인텔리제이(또는 터미널)에서 이 폴더를 열고 `claude` 실행
- 첫 마디: **"HANDOFF.md 읽고 이어서 해 줘"**, 새 앱은 **`/build-app 만들고 싶은 것`**
- 다른 컴퓨터: `git pull` 먼저
- **다른 컴퓨터에서 scent-studio를 시작할 때는 `apps/scent-studio/docs/new_computer_setup.md` 먼저**

## 3. 처음 쓰는 컴퓨터 준비 (한 번만)
1. git 설치 (Windows는 Git Bash가 함께 깔림. 훅이 bash로 돈다)
2. GitHub CLI: `winget install GitHub.cli` → `gh auth login --web` → `gh auth setup-git`
3. 저장소 받기: `git clone <이 저장소 주소>`
4. 이 폴더를 Claude Code로 처음 열면 superpowers 플러그인 설치를 묻는다 → "예"
5. git 이름·이메일이 없으면: `git config user.name "이름"`, `git config user.email "메일"`
6. 팀 기본 기술 도구 (첫 앱을 만들 때 Claude가 필요한 것만 설치를 안내한다):
   - Java (백엔드팀): `winget install --id EclipseAdoptium.Temurin.21.JDK -e` (Mac `brew install --cask temurin@21`)
   - Node.js (프론트엔드팀·디자인팀·QA팀의 React, Vite, Tailwind, Playwright, Vitest): `winget install --id OpenJS.NodeJS.LTS -e` (Mac `brew install node`)
   - Playwright 브라우저 (QA팀, 앱 폴더에서 한 번): `npx playwright install chromium`
   - 안드로이드 앱을 빌드할 때: Android Studio `winget install --id Google.AndroidStudio -e` (Mac `brew install --cask android-studio`) → 처음 실행해서 SDK 설치 마법사를 끝까지 진행. 에뮬레이터는 Device Manager에서 하나 만든다
   - 파이썬으로 테스트하는 앱일 때만: `py -m pip install pytest playwright` → `py -m playwright install chromium` (Mac은 `python3`)

## 4. 로드맵
| 단계 | 내용 | 상태 |
|---|---|---|
| 0~3 | 폴더 구조, 규칙(CLAUDE.md), `/build-app` 스킬, 훅 | ✅ 준비됨 |
| 4 | 첫 앱 만들기 (`apps/scent-studio/`) | 🔄 4단계(구현) 중 — 엔진 v8 적용 완료, 블라인드 유의차 없음(v8 유지), 눈으로 평가 합격, 등급 확정, 엔진 동결 → 그다음 DB·서버·디자인·화면 |
| 교차 리뷰 | 5단계에 Gemini CLI 리뷰 (**필수**, 변경분 1라운드, README 참고) | ✅ 준비됨 |

## 5. 파일 구조
```
├── CLAUDE.md                              항상 지킬 규칙
├── HANDOFF.md                             이 파일 (저장소 전체 현황)
├── README.md                              이 견본을 새 프로젝트에 쓰는 방법
├── .claude/skills/build-app/SKILL.md      /build-app 절차 (앱 하나 한 바퀴, 기능 추가도 같은 순서)
├── .claude/skills/build-app/templates/    견본 파일 (파이썬으로 테스트하는 앱용 run.sh)
├── .claude/skills/build-app/cross-review.sh  교차 리뷰 (필수, Gemini CLI로 변경분 리뷰)
├── setup/new-project/SKILL.md             "새 프로젝트 만들어 줘" 개인 스킬 (README 참고해 설치)
├── .claude/hooks/check-tests.sh           테스트 실패 시 커밋·완료 보고 막기
├── .claude/settings.json                  훅 등록, superpowers 플러그인 켜기
└── apps/                                  앱 하나당 폴더 하나 (아직 비어 있음)
```

## 6. 정해진 것 (다시 묻지 않기)
- 저장소 하나에 앱 여러 개 (`apps/<앱이름>/`)
- 스킬·훅은 저장소 안 `.claude/`에 둔다 (GitHub로 다른 컴퓨터에 따라가게)
- 사용자는 개발 초보. 질문은 쉽게, 하나씩, 보기와 추천을 붙여서
- 사용자는 사장, 팀은 직원. 팀별 기본 기술(백엔드 Java+Spring, 프론트엔드 React, 디자인 DESIGN.md+Tailwind, QA Playwright+Vitest/JUnit, 기획 저장소 안 기획서)과 사장 검토 지점은 `CLAUDE.md` "팀별 기본 기술" 표

## 7. 주의할 점 (앞선 시험 앱에서 배운 것, 스킬에 반영됨)
- 공개 주소에 올리기(GitHub Pages 등)는 안전장치 때문에 Claude가 직접 못 한다. 사용자가 `! bash apps/<앱>/deploy.sh` 같은 짧은 한 줄을 실행한다. `!` 앞에 띄어쓰기가 있으면 실행되지 않는다.
- 훅은 `HANDOFF.md`의 `테스트 실행:` 줄을 읽어 돌린다. 허용 명령: `npx playwright test`, `npx vitest run`, `npm test`, `./gradlew test`, `mvn test` 등(전체 목록은 스킬). 여러 도구를 함께 돌려야 하면 `tests/run.sh`에 담아 `bash tests/run.sh`.
- 파이썬으로 테스트하는 앱이면: Windows의 `python`은 가짜 실행 파일일 수 있으니 `bash tests/run.sh`(견본 `templates/run.sh`, py·python3 자동 선택).
- 구현 중(테스트 일부 실패)에는 Stop 훅이 응답을 한 번 멈춰 세운다. 정상이다.
