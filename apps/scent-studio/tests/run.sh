#!/usr/bin/env bash
# 테스트 실행: 앱 폴더에서  bash tests/run.sh
# 훅(.claude/hooks/check-tests.sh)이 커밋 전과 응답을 끝내기 전에 돌린다. 5분 안에 끝나야 한다.
# 폰 앱 확인(EAS 설치 파일)은 느려서 여기 넣지 않는다 (6단계에 따로).
# 승인 목록(Playwright, app/tests/acceptance)은 41/41이 될 때까지 여기 넣지 않고 따로 돌린다:
#   cd app && npx playwright test   → 결과 N/41을 HANDOFF.md·docs/NOTES.md에 적는다 (docs/plan.md)
cd "$(dirname "$0")/.." || exit 1

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
"$PY" -I -m pytest -q pipeline/tests || exit 1

# 2. 앱: 추천 엔진(골든·성질) 등 부품 단위 테스트 (Vitest)
cd app || exit 1
if [ ! -d node_modules ]; then
  echo "app/node_modules가 없습니다: cd app && npm install" >&2
  exit 1
fi
npx vitest run "$@" || exit 1
cd .. || exit 1

# 3. 서버: DB 마이그레이션·검사·권한 (JUnit + 내장 Postgres, Docker 필요 없음, Java 21)
cd backend || exit 1
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])'; then
  echo "Java 21 이상이 필요합니다: winget install --id EclipseAdoptium.Temurin.21.JDK -e (Mac: brew install --cask temurin@21)" >&2
  exit 1
fi
./gradlew test --console=plain -q
