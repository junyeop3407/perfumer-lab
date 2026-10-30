---
name: new-project
description: Use when the user wants to start a new project or app workspace ("새 프로젝트 만들어 줘", "새 앱 작업장", "/new-project"), creating it from the ai-workflow template repository.
---

# new-project — ai-workflow 견본으로 새 프로젝트 만들기

견본 저장소: https://github.com/junyeop3407/ai-workflow (공개, 견본 저장소로 표시됨)
안에 든 것: 규칙(CLAUDE.md), `/build-app` 스킬, 교차 리뷰, 훅, HANDOFF.

## 순서
1. **이름 정하기**: 사용자가 이름을 안 줬으면 하나만 묻는다. 영문 소문자와 하이픈(예: `study-timer`). 무엇을 만들지 들었으면 이름을 추천해 보기로 준다.
2. **만들 위치**: 따로 말이 없으면 Windows는 `C:\<이름>`, Mac은 `~/<이름>`. 같은 이름 폴더나 GitHub 저장소가 이미 있으면 다른 이름을 묻는다.
3. **만들기** (공개. 앱을 다 만든 뒤 `/build-app` 6단계에서 공개로 둘지 비공개로 바꿀지 다시 묻는다. 처음부터 공개이므로 API 키·비밀번호 같은 비밀값은 코드에 넣지 않고 `.env`에 둔다):
   ```
   gh repo create <이름> --public --template junyeop3407/ai-workflow --clone
   ```
   - 만들 위치의 상위 폴더에서 실행한다. Windows에서 `gh`가 PATH에 없으면 `"C:\Program Files\GitHub CLI\gh.exe"`.
   - 저장소를 만드는 일은 바깥에 내보내는 일이라 안전장치가 막을 수 있다. 막히면 사용자에게 짧은 한 줄을 준다:
     `! cd /c && "/c/Program Files/GitHub CLI/gh.exe" repo create <이름> --public --template junyeop3407/ai-workflow --clone`
     (`!`는 맨 앞, 띄어쓰기 없이)
   - `gh`가 없거나 로그인이 안 돼 있으면: `winget install GitHub.cli` → `! gh auth login --web` → `gh auth setup-git`.
4. **확인**: 새 폴더에 `CLAUDE.md`, `.claude/skills/build-app/SKILL.md`가 있는지 본다. 견본에서 막 만든 저장소는 내용이 늦게 채워질 수 있으니, 비어 있으면 몇 초 뒤 `git pull`.
5. git 이름·이메일이 없으면 새 폴더에 `git config user.name`, `git config user.email`을 설정한다(지금 컴퓨터의 다른 저장소 값을 그대로).
6. **알려 주기** (짧게):
   - 만든 저장소 주소와 폴더 위치
   - 다음: 그 폴더를 Claude Code로 연다(인텔리제이에서 폴더 열기 → Claude Code, 또는 터미널에서 그 폴더로 가서 `claude`). 처음 열면 superpowers 플러그인 설치를 묻는다 → "예".
   - 첫 마디: `/build-app 만들고 싶은 것`

## 참고
- 견본은 만든 그 순간의 내용을 복사한다. 나중에 견본(ai-workflow)의 스킬·훅이 바뀌어도 이미 만든 프로젝트에는 자동으로 들어가지 않는다.
- 이 스킬 자체는 `ai-workflow` 저장소의 `setup/new-project/SKILL.md`에도 있다. 다른 컴퓨터에서는 그 파일을 `~/.claude/skills/new-project/SKILL.md`로 복사하면 쓸 수 있다.
