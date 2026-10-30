# 테스트 실행: 앱 폴더에서  bash tests/run.sh
# 컴퓨터마다 파이썬 명령이 달라서(Windows: py, Mac: python3) pytest가 깔린 것을 찾아 쓴다.
cd "$(dirname "$0")/.." || exit 1
for py in py python3 python; do
  if command -v "$py" >/dev/null 2>&1 && "$py" -c "import pytest, playwright" >/dev/null 2>&1; then
    exec "$py" -m pytest -q "$@"
  fi
done
echo "pytest와 playwright가 설치된 파이썬을 찾지 못했습니다. docs/design.md의 '테스트 도구' 설치 방법을 따라 주세요." >&2
exit 1
