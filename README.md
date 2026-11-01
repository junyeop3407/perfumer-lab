# ai-workflow

사용자는 **사장**, Claude의 팀들(기획·디자인·프론트엔드·백엔드·QA)은 **직원**입니다.
사장은 정하고 검토만 하고(모든 팀의 결과물은 사장 검토를 받음), 나머지(계획, 구현, 테스트, 리뷰, 문서, 커밋)는
Claude Code가 혼자 돌려서 앱을 만드는 작업장 견본입니다.
근거: 이석균 선배님 특강 (정리 문서는 비공개 저장소에 따로 보관)

> 이 README는 Claude Code가 읽고 개발을 시작하는 설명서입니다.

## 들어 있는 것
규칙을 "부탁"이 아니라 **구조**로 지키게 나눠 두었습니다. CLAUDE.md는 공식 권장(200줄 이하)보다 짧게, 매번 알아야 하는 사실만 담습니다.

| 폴더 | 무엇 | 왜 여기에 |
|---|---|---|
| `CLAUDE.md` | 프로젝트 소개, 자주 쓰는 명령, 역할, 팀별 기술 | 매 세션 항상 읽힘 → 짧을수록 잘 지킴 |
| `.claude/rules/` | 보안·화면(웹+안드로이드·iOS)·테스트 규칙 | 파일 위에 경로를 적어 그 경로를 열 때만 불러짐 |
| `.claude/skills/` | `/build-app`(앱 한 바퀴 + 단계별 스킬 표), `/handoff`(HANDOFF → 커밋 → 푸시) | 평소엔 설명 한 줄만, 쓸 때만 본문을 읽음 |
| `.claude/hooks/` | 실패 상태 커밋·강제 푸시·비밀값 커밋 차단, 승인된 테스트 수정 시 사장 확인 | Claude가 뭘 판단하든 동작 자체를 막음 |
| `.claude/agents/` | `test-runner`, `qa-reviewer`, `red-team` | 자기 컨텍스트에서 일하고 요약만 돌려줘 메인 대화가 가벼움 |

- `/build-app` 순서: 질문 → 설계안 3개 → 테스트 목록 승인 → 구현 → 별도 리뷰 + **교차 리뷰**(Gemini, 필수) → 레드팀 → 확인·올리기
- `HANDOFF.md`: 어느 컴퓨터에서든 여기서 이어서 시작
- `setup/new-project/`: 어디서든 "새 프로젝트 만들어 줘"로 이 견본을 꺼내 쓰는 개인 스킬

## 새 컴퓨터에서 한 번에 세팅하기
새 컴퓨터의 Claude Code에 이렇게만 말하면 됩니다.
```
junyeop3407/ai-workflow README 보고 이 컴퓨터 세팅해 줘
```

### Claude가 따라 할 순서 (새 컴퓨터 세팅)
> 이 저장소는 공개 저장소다. README는 `gh api repos/junyeop3407/ai-workflow/readme -H "Accept: application/vnd.github.raw"`로 읽는다.
> 이미 된 단계는 확인만 하고 넘어간다. 사용자는 개발 초보: 사용자가 직접 해야 하는 것은 `!`로 시작하는 짧은 한 줄로 주고, 맨 앞에 띄어쓰기가 없어야 실행된다고 알려 준다.

1. **git**: `git --version`. 없으면 Windows `winget install --id Git.Git -e`, Mac `xcode-select --install`. (Windows는 Git Bash가 함께 깔리고 훅이 이걸로 돈다)
2. **GitHub CLI**: `gh --version`. 없으면 Windows `winget install --id GitHub.cli -e`, Mac `brew install gh`.
   **Windows에서는 설치 직후 `gh`가 PATH에 없는 일이 흔하다.** 이후 모든 bash 명령에서 gh를 이렇게 정해 쓴다:
   `GH=gh; command -v gh >/dev/null || GH="/c/Program Files/GitHub CLI/gh.exe"` (그다음 `"$GH" ...`)
3. **GitHub 로그인**: `"$GH" auth status`. 안 돼 있으면 사용자에게 부탁: `! gh auth login --web` (gh가 PATH에 없으면 `! "/c/Program Files/GitHub CLI/gh.exe" auth login --web`). 끝나면 `"$GH" auth setup-git`.
4. **git 이름·이메일**: `git config --global user.name` / `user.email`이 비어 있으면 사용자에게 묻고 `--global`로 설정.
5. **`/new-project` 스킬 설치** (Git Bash 또는 Mac 터미널, 2번의 `GH`를 정한 뒤):
   `mkdir -p ~/.claude/skills/new-project && "$GH" api repos/junyeop3407/ai-workflow/contents/setup/new-project/SKILL.md -H "Accept: application/vnd.github.raw" > ~/.claude/skills/new-project/SKILL.md`
   → 파일 첫 줄이 `---`이고 `name: new-project`가 있는지 확인.
6. **선택 (사용자에게 하나씩 묻기, 추천 붙여서)**:
   - 팀 기본 기술 도구(Java JDK, Node.js, Playwright 브라우저): 첫 앱을 만들 때 필요한 것만 깔아도 됨(추천: 지금은 건너뜀). 설치 명령은 HANDOFF.md "처음 쓰는 컴퓨터 준비" 6번.
   - 교차 리뷰 도구(아래 "교차 리뷰" 절): Node.js + Gemini CLI. **교차 리뷰는 필수**라 첫 앱의 5단계 전에는 깔아야 함(추천: 지금 설치). Google AI Studio 무료 API 키 필요.
7. **이어서 하던 프로젝트가 있으면**: 사용자에게 저장소 이름을 묻고 `"$GH" repo clone junyeop3407/<이름>` → 그 폴더에서 `HANDOFF.md 읽고 이어서 해 줘`.
8. **알려 주기** (짧게): 된 것 / 건너뛴 것, 그리고 사용법 — 아무 폴더에서 `/new-project 만들고 싶은 것`. `/new-project`가 목록에 안 보이면 Claude Code를 한 번 껐다 켠다.

## 새 프로젝트 시작하기 (명령을 외울 필요 없음)
아무 폴더에서나 Claude Code를 열고 이렇게 말하면 됩니다.
```
/new-project 만들고 싶은 것
```
Claude가 이름을 정하고(추천해 줌), 이 견본으로 공개 저장소를 만들어 내려받습니다. 앱을 다 만들고 나면 공개로 둘지 비공개로 바꿀지 물어봅니다.
그다음 만들어진 폴더를 Claude Code로 열고 `/build-app 만들고 싶은 것`.

> 직접 하고 싶으면: `gh repo create <새이름> --public --template junyeop3407/ai-workflow --clone`

### 다른 컴퓨터에서 `/new-project`를 쓰려면 (한 번만)
Git Bash(또는 Mac 터미널)에서, GitHub 로그인(`gh auth login --web`) 뒤:
```
GH=gh; command -v gh >/dev/null || GH="/c/Program Files/GitHub CLI/gh.exe"; mkdir -p ~/.claude/skills/new-project && "$GH" api repos/junyeop3407/ai-workflow/contents/setup/new-project/SKILL.md -H "Accept: application/vnd.github.raw" > ~/.claude/skills/new-project/SKILL.md
```

## 교차 리뷰 (필수)
5단계에서 Claude 리뷰에 더해 **Gemini CLI**에게 이번 작업의 변경분을 리뷰받습니다. 건너뛰지 않습니다(없으면 Claude가 설치를 부탁함).
- 설치: Node.js `winget install --id OpenJS.NodeJS.LTS -e` → Gemini CLI `npm install -g @google/gemini-cli` → https://aistudio.google.com/apikey 에서 무료 API 키를 만들고 PowerShell에서 `[Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "<키>", "User")` 실행 (Google 계정 로그인 방식은 개인 무료 사용이 막혀 안 됨. 무료 키는 flash 모델만 쓸 수 있어 스크립트가 flash를 기본으로 씀)
- 실행: `bash .claude/skills/build-app/cross-review.sh apps/<앱> <리뷰 기준 커밋>` → `apps/<앱>/docs/review_result.md`
- 변경분 + 변경된 파일 전체 + 그 파일을 import하는 파일을 묶어 보내고, 버그·보안·예외 처리 누락·성능만 심각도와 함께 지적받습니다.
- Claude가 각 지적이 맞는지 코드로 확인해 반영합니다. 반영 안 한 지적은 이유를 남기고, 심각도 "높음"인데 애매하면 사장에게 먼저 묻습니다. 리뷰는 1라운드만.
- 6단계 QA 보고에 "반영한 지적 / 반영 안 한 지적(이유) / 확인 필요한 것"으로 정리됩니다.

## 사장이 하는 일 (모든 팀의 결과물을 검토)
| 언제 | 팀 | 할 일 |
|---|---|---|
| 1단계 | 기획팀 | 질문에 답하기(보기 중 고르기) → 기획서 검토 |
| 2단계 | 전체 | 설계안 3개(+디자인 방향) 중 고르기 |
| 3단계 | QA팀 | "뭐가 되면 끝인가" 테스트 목록 승인 |
| 4단계 시작 | 디자인팀 | 디자인 규칙과 시안 화면 캡처 검토 |
| 4단계 시작 | 백엔드팀 | 저장할 데이터와 기능 목록 검토 (서버가 있는 앱만) |
| 6단계 | 프론트엔드팀·QA팀 | QA 보고(테스트 결과, 리뷰에서 고친 것) 보고 완성된 앱을 써 본 뒤 OK (공개가 필요하면 `!` 한 줄 실행) → 저장소를 공개로 둘지 비공개로 바꿀지 고르기 |

검토는 "OK" 또는 고칠 점을 말하면 됩니다. 고칠 점을 말하면 고쳐서 다시 검토를 요청합니다.
