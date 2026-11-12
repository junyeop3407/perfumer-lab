#!/usr/bin/env bash
# 테스트 실행: 앱 폴더에서  bash tests/run.sh
# 훅(.claude/hooks/check-tests.sh)이 커밋 전과 응답을 끝내기 전에 돌린다. 5분 안에 끝나야 한다.
# 폰 앱 확인(EAS 설치 파일)은 느려서 여기 넣지 않는다 (6단계에 따로).
# 화면 테스트(Playwright, 웹 빌드 한 번): 승인 목록(app/tests/acceptance) + 회귀(app/tests/regression).
#   승인 목록은 아직 화면이 없는 것만 app/tests/acceptance/pending.txt에 적어 실패를 허용한다(2026-11-12 사장 지시).
#   목록 밖 승인 테스트 실패, 회귀 테스트 실패, 목록에 있는데 통과 → 모두 run.sh 실패.
cd "$(dirname "$0")/.." || exit 1
# 끝에 다섯 줄(파이프라인·Vitest·화면 승인·화면 회귀·서버)로 요약한다. 단계마다 실패하면 그 출력을 보이고 바로 실패로 끝난다.
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT
fail() { cat "$LOG" >&2; echo "실패: $1" >&2; exit 1; }

# 1. 파이프라인 (Python 3.12 이상, f-string 문법 때문). 컴퓨터마다 명령이 달라서(Windows: py, Mac: python3.12) pytest가 있는 것을 찾는다
#    Mac 기본 python3(3.9)는 버전 검사에서 걸러진다. -I로 돌리므로 pip --user 설치는 보이지 않는다
PY=""
for cmd in py python3 python3.13 python3.12 python; do
  if command -v "$cmd" >/dev/null 2>&1 && "$cmd" -I -c "import sys, pytest; sys.exit(sys.version_info < (3, 12))" >/dev/null 2>&1; then PY="$cmd"; break; fi
done
if [ -z "$PY" ]; then
  echo "pytest가 설치된 Python 3.12 이상을 찾지 못했습니다: Windows py -m pip install -r pipeline/requirements.txt / Mac brew install python@3.12 → python3.12 -m pip install --break-system-packages -r pipeline/requirements.txt" >&2
  exit 1
fi
"$PY" -I -m pytest -q pipeline/tests >"$LOG" 2>&1 || fail "파이프라인"
SUM_PIPE="파이프라인: $(grep -E '[0-9]+ (passed|failed)' "$LOG" | tail -1)"
export SCENT_PY="$PY" # Vitest의 카탈로그 준비 테스트(prepare-catalog.mjs)가 같은 Python으로 샘플을 만든다

# 2. 앱: 추천 엔진(골든·성질) 등 부품 단위 테스트 (Vitest)
cd app || exit 1
if [ ! -d node_modules ]; then
  echo "app/node_modules가 없습니다: cd app && npm install" >&2
  exit 1
fi
npx vitest run "$@" >"$LOG" 2>&1 || fail "Vitest"
SUM_VITEST="Vitest: $(grep -E '^\s+Tests ' "$LOG" | tail -1 | sed -E 's/^\s+Tests\s+//')"

# 2-2. 앱 화면: 승인 + 회귀 (Playwright, 크롬). CI=1: 남아 있는 옛 웹 서버를 다시 쓰지 않고 새로 빌드한다
rm -f test-results/check.json # 빌드가 깨져 결과를 못 쓰면 옛 결과로 통과하지 않게
CI=1 npx playwright test -c playwright.check.config.ts >/dev/null 2>&1
node tests/check-playwright.mjs test-results/check.json >"$LOG" 2>&1 || fail "화면 테스트"
SUM_SCREEN="$(tail -1 "$LOG")" # "승인 n/m 통과(…) · 회귀 n/n 통과"
cd .. || exit 1

# 3. 서버: DB 마이그레이션·검사·권한 (JUnit + 내장 Postgres, Docker 필요 없음, Java 21)
cd backend || exit 1
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
  echo "Java 21 이상이 필요합니다: winget install --id EclipseAdoptium.Temurin.21.JDK -e (Mac: brew install --cask temurin@21)" >&2
  exit 1
fi
# --rerun: 서버 코드가 안 바뀌어도 Gradle이 "UP-TO-DATE"로 건너뛰지 않고 매번 돈다(2026-11-12: -q라 건너뛴 게 안 보였음)
rm -rf build/test-results/test # 옛 결과로 세지 않게
./gradlew test --rerun --console=plain -q >"$LOG" 2>&1 || fail "서버(JUnit)"
SUM_SERVER="$(node ../tests/junit-summary.mjs build/test-results/test 2>"$LOG")" || fail "서버(JUnit) 결과"

echo "$SUM_PIPE"
echo "$SUM_VITEST"
echo "화면 ${SUM_SCREEN%% · *}"
echo "화면 ${SUM_SCREEN##* · }"
echo "$SUM_SERVER"
