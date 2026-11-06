# 다른 컴퓨터에서 이어 하기 (scent-studio)

> 이 문서에는 **값(비밀번호·키·토큰)을 절대 쓰지 않는다.** "어디서 가져오는지"만 적는다.
> 마지막 갱신: 2026-11-06 (단계 4 묶음 1: Render 배포 완료, 실측 대기)

## 이 컴퓨터에서 오늘 한 설정 중 다른 컴퓨터에서 다시 해야 하는 것

### A. 다시 할 필요 없는 것 (클라우드·GitHub에 있음)
- **Supabase**: 프로젝트, 표 6개(V1), RLS, `scent_server` 계정과 비밀번호, pg_cron 예약 정리(`purge-old-events`)
- **Render**: 서버(`https://perfumer-lab.onrender.com`), 환경 변수 7개, Auto-Deploy Off, Health Check `/api/v1/health/live`
- **GitHub**: Secrets 2개(`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`), 예약 작업 `supabase-keepalive`(매일)
- **Render GitHub 앱 연결**: `perfumer-lab` 저장소만 허용됨
- **비밀번호 2개**: Google 비밀번호 관리자 `supabase.com` 항목에 저장됨 — `postgres`(관리자), `scent_server`(서버)

### B. 컴퓨터마다 다시 해야 하는 것 (순서대로)
1. **Chrome을 같은 구글 계정으로 로그인**: 비밀번호 관리자(`chrome://password-manager`)를 보려고.
2. **도구 설치·확인** (Windows는 PowerShell, 설치 뒤 터미널을 새로 연다):

   | 도구 | 설치 | 확인 |
   |---|---|---|
   | git (Git Bash 포함) | `winget install --id Git.Git -e` | `git --version` |
   | Java 21 | `winget install --id EclipseAdoptium.Temurin.21.JDK -e` | `java -version` (21) |
   | Node.js | `winget install --id OpenJS.NodeJS.LTS -e` | `node -v` |
   | Python | `winget install --id Python.Python.3.12 -e` | `py --version` |
   | gh (GitHub CLI) | `winget install --id GitHub.cli -e` | `gh --version` |
   | Gemini CLI (교차 리뷰) | `npm install -g @google/gemini-cli` | `gemini --version` |
   | Claude Code | `npm install -g @anthropic-ai/claude-code` | `claude --version` |

   - **Mac은 아래 "Mac에서 다른 점" 절을 따른다.** 요약: 기본 `python3`는 3.9라 파이프라인이 안 돈다(3.12 문법). `brew install python@3.12` → `python3.12 -m pip install --break-system-packages -r pipeline/requirements.txt` (`tests/run.sh`가 `-I`로 돌려 `--user` 설치는 안 보인다). npm 캐시 권한 오류가 나면 `npm install --cache <임시 폴더>`.
   - Gemini API 키: https://aistudio.google.com/apikey 에서 만들거나 기존 키를 써서 PowerShell `[Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "<키>", "User")`. 루트 README "교차 리뷰" 참고.
   - 카탈로그를 다시 만들 때만: 환경 변수 `SCENT_KEY_SECRET` (루트 README "데이터 정리 목록의 비밀 키", 값은 비밀번호 관리자).
3. **GitHub 로그인**: `gh auth login --web` → `gh auth setup-git`.
   워크플로 파일(`.github/workflows/`)을 고칠 거면 추가로 `gh auth refresh -h github.com -s workflow`.
4. **저장소 받기**:
   - 처음이면 `git clone https://github.com/junyeop3407/perfumer-lab.git`
   - 있으면 `git pull`
   - 그다음 `git fetch && git switch wip/scent-studio-stage4 && git pull`
5. **`.env` 만들기**: `apps/scent-studio/backend/.env.example`을 같은 폴더에 `.env`로 복사하고 칸을 채운다(`.env`는 git이 무시함, 커밋 안 됨).

   | 칸 | 어디서 가져오나 |
   |---|---|
   | `DB_URL` | Supabase 대시보드 → Connect → Direct → **Session pooler** 호스트를 `jdbc:postgresql://<호스트>:5432/postgres?sslmode=require` 형식으로 (또는 Render → 서비스 → Environment에서 복사) |
   | `DB_MIGRATION_USER` | `postgres.<프로젝트참조값>` (프로젝트참조값은 Supabase 주소 `https://<참조값>.supabase.co`의 앞부분) |
   | `DB_MIGRATION_PASSWORD` | Google 비밀번호 관리자 "perfumer-lab DB (postgres)". **Render에는 없음, 여기에만 있음** |
   | `DB_SERVER_USER` | `scent_server.<프로젝트참조값>` |
   | `DB_SERVER_PASSWORD` | Google 비밀번호 관리자 "perfumer-lab DB (scent_server)" 또는 Render → Environment |
   | `SUPABASE_PUBLISHABLE_KEY` | Supabase → Project Settings → API Keys → Publishable key (`sb_publishable_`로 시작) |
   | `DEBUG_TOKEN` | Render → 서비스 → Environment (묶음 1 실측이 끝나면 지움) |
   | `RENDER_URL` | Render 대시보드의 서비스 주소 (`https://perfumer-lab.onrender.com`) |
   | `SUPABASE_SECRET_KEY` | **묶음 6에서 생김**(지금은 빈 칸). 새로 만든 `render-server` 키, 기본 키 아님 |

   - **자주 하는 실수 (2026-11-06 Mac에서 실제로 난 것)**:
     - `DB_URL` 끝부분을 두 번 붙여 넣음(`...?sslmode=require:5432/postgres?sslmode=require`) → `dbCheck`가 "잘못된 sslmode 값"으로 실패. `?sslmode=require`는 맨 끝에 한 번만.
     - `SUPABASE_PUBLISHABLE_KEY` 칸에 **Secret key(`sb_secret_`)** 를 넣음 → `api_check.sh`의 keepalive가 403 "permission denied for function keepalive". 이 칸은 반드시 `sb_publishable_`로 시작한다. Secret key는 이 칸에도 다른 칸에도 넣지 않는다.
6. **값 다루는 규칙**:
   - `.env` 값은 대화창과 다른 파일에 붙여 넣지 않는다.
   - 복사하다 메모장 임시 탭에 붙였으면 **저장하지 말고 닫는다**.
7. **커밋 안 하는 데이터 받기**:
   - `pipeline/data/out/`의 카탈로그·표는 비공개 저장소 `junyeop3407/perfumer-lab-private`에 있다. `gh repo clone junyeop3407/perfumer-lab-private`로 받아 필요한 파일을 `apps/scent-studio/pipeline/data/out/`에 복사한다.
   - 채운 시트는 경로가 다르다: `blind_v8/sheet_judged.md` → `blind_v8_sheet.md`, `eye_v8/sheet_scored.md` → `eye_v8_sheet.md`.
   - 원본부터 다시 만들려면 `SCENT_KEY_SECRET`을 설정한 뒤 `py pipeline/fetch.py` → `py pipeline/fetch_decant.py` → `py pipeline/build_catalog.py`.
   - 정답 키(`blind_v8/answer_key.json`)는 받지 않는다.
   - 앱 화면 작업 때: `cd app && npm install && npx playwright install chromium`.

### C. 다 하고 확인하는 순서 (`apps/scent-studio`에서)
1. `bash tests/run.sh` → 파이프라인 + Vitest + 서버·DB JUnit 전부 통과. 첫 실행은 Gradle·내장 Postgres를 받느라 1~2분 걸린다. 키·원본이 없으면 파이프라인 일부는 건너뛴다.
2. `cd backend && ./gradlew dbCheck` → 실제 Supabase 접속, 표·권한·RLS 확인.
3. `bash backend/scripts/api_check.sh` → 공개 키로 keepalive는 되고 app 스키마는 막히는지.
4. 브라우저나 `curl https://perfumer-lab.onrender.com/api/v1/health/live` → `{"status":"ok"}` 응답. 서버가 자고 있으면 첫 응답에 30~90초 걸린다.

### Mac에서 다른 점 (2026-11-06, macOS 14 Apple Silicon에서 확인)
- **설치는 winget 대신 Homebrew**: `brew install git gh node python@3.12`, Java 21은 `brew install --cask temurin@21`(Oracle JDK 21도 됨). Gemini CLI·Claude Code는 표와 같이 `npm install -g`.
- **Java**: `JAVA_HOME`을 따로 정하지 않아도 Gradle이 설치된 21을 찾는다. 확인은 `java -version`(21) + `cd backend && ./gradlew -version`의 `Launcher JVM`·`Daemon JVM`이 21인지.
- **Python**: Mac에는 `py`가 없다. 이 문서·HANDOFF의 `py ...`는 **`python3.12 ...`로 바꿔 읽는다.** 기본 `python3`(3.9)는 쓰지 않는다.
  - 설치: `python3.12 -m pip install --break-system-packages -r pipeline/requirements.txt` (`tests/run.sh`가 `-I`로 돌려 `--user` 설치는 안 보인다).
- **환경 변수**(`GEMINI_API_KEY`, `SCENT_KEY_SECRET`): PowerShell 명령 대신 `~/.zshrc`에 `export 이름=<값>` 한 줄을 넣고 터미널을 새로 연다. 값은 대화창에 붙여 넣지 않는다.
- **`.env` 복사**: `cd backend && cp .env.example .env`.
- **npm 캐시 권한 오류**가 나면 `npm install --cache <임시 폴더>`.
- 그 밖에 `tests/run.sh`, Gradle, 내장 Postgres(arm64)는 Windows와 똑같이 돌았다(파이프라인 119 + 건너뜀 2, Vitest 37, 서버·DB 105).

### D. 비밀번호를 잃어버렸을 때
- **postgres(관리자)**:
  1. Supabase → Project Settings → Database → 데이터베이스 비밀번호 재설정(Reset database password)으로 새로 정한다(영문·숫자 32자 이상).
  2. 비밀번호 관리자를 고친다.
  3. **모든 컴퓨터의 `.env`** `DB_MIGRATION_PASSWORD`를 고친다.
  - Render는 이 비밀번호를 쓰지 않는다.
- **scent_server(서버)**:
  1. 새 비밀번호(영문·숫자 32자 이상)를 만들어 `.env`의 `DB_SERVER_PASSWORD`에 넣는다.
  2. `cd backend && ./gradlew dbServerPassword`를 실행한다. `.env` 값을 읽어 이 컴퓨터에서 SCRAM-SHA-256 해시로 바꿔 넣고, 원문·해시는 출력하지 않는다. SQL Editor는 쓰지 않는다.
  3. `./gradlew dbServerCheck`로 확인한다.
  4. **Render 환경 변수 `DB_SERVER_PASSWORD`도 같은 값으로** 바꾸고 다시 배포한다.
  5. 비밀번호 관리자를 고친다.
