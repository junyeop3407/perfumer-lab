# HANDOFF — 다음 세션 시작 가이드

마지막 갱신: 2026-10-30

## 1. 한 줄 요약
이석균 선배님 특강(AI 기초)의 방식대로 앱을 만드는 **작업장 견본**이다. 사용자는 **사장**, Claude의 팀들(기획·디자인·프론트엔드·백엔드·QA)은 **직원**이고, 모든 팀의 결과물은 사장 검토를 받는다. 규칙, `/build-app` 스킬, 교차 리뷰, 훅이 준비되어 있다. **진행 중인 앱: `apps/scent-studio`(향 조합 → 비슷한 향수 추천, 안드로이드 전용) — 4단계 중. 이어 할 때는 브랜치 `wip/scent-studio-stage4`로 바꾸고(`git switch wip/scent-studio-stage4`) `apps/scent-studio/HANDOFF.md`의 "다음 세션에서 할 일"부터** (2026-10-30).

## 2. 새 세션 시작 방법
- 인텔리제이(또는 터미널)에서 이 폴더를 열고 `claude` 실행
- 첫 마디: **"HANDOFF.md 읽고 이어서 해 줘"**, 새 앱은 **`/build-app 만들고 싶은 것`**
- 다른 컴퓨터: `git pull` 먼저

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
   - 안드로이드 앱 전용일 때만: Android Studio `winget install --id Google.AndroidStudio -e` (Mac `brew install --cask android-studio`) → 처음 실행해서 SDK 설치 마법사를 끝까지 진행. 에뮬레이터는 Device Manager에서 하나 만든다
   - 파이썬으로 테스트하는 앱일 때만: `py -m pip install pytest playwright` → `py -m playwright install chromium` (Mac은 `python3`)

## 4. 로드맵
| 단계 | 내용 | 상태 |
|---|---|---|
| 0~3 | 폴더 구조, 규칙(CLAUDE.md), `/build-app` 스킬, 훅 | ✅ 준비됨 |
| 4 | 첫 앱 만들기 (`apps/scent-studio/`) | 🔄 4단계(구현) 중 — 엔진 마무리 |
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
