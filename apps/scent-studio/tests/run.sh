#!/usr/bin/env bash
# 테스트 실행: 앱 폴더에서  bash tests/run.sh
# 훅(.claude/hooks/check-tests.sh)이 커밋 전과 응답을 끝내기 전에 돌린다. 5분 안에 끝나야 한다.
# 폰 앱 확인(EAS 설치 파일)은 느려서 여기 넣지 않는다 (6단계에 따로).
# 2026-10-31 설계 E안(Expo): 4단계에서 app/ 의 Vitest(엔진·저장소)와 Playwright(승인 목록)를 여기 더한다.
cd "$(dirname "$0")/.." || exit 1

# 1. 파이프라인 (Python). 컴퓨터마다 명령이 달라서(Windows: py, Mac: python3) pytest가 있는 것을 찾는다
PY=""
for cmd in py python3 python; do
  if command -v "$cmd" >/dev/null 2>&1 && "$cmd" -c "import pytest" >/dev/null 2>&1; then PY="$cmd"; break; fi
done
if [ -z "$PY" ]; then
  echo "pytest가 설치된 파이썬을 찾지 못했습니다: py -m pip install pytest (Mac은 python3)" >&2
  exit 1
fi
"$PY" -I -m pytest -q pipeline/tests || exit 1

# 2. 엔진(Java, TypeScript로 옮길 때 기준으로 남겨 둠)
exec ./gradlew --quiet :engine:test "$@"
