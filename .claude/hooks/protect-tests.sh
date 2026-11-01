#!/usr/bin/env bash
# "승인된 테스트는 사장 허락 없이 고치거나 지우지 않는다"를 시스템으로 지킨다. (PreToolUse: Edit|Write|MultiEdit)
# 이미 커밋된 apps/<앱>/tests/ 파일(= 승인되어 통과까지 확인된 테스트)을 고치려 하면
# Claude가 혼자 결정하지 못하게 사장 확인 창(ask)을 띄운다.
# 테스트 준비 코드(conftest, run.sh, fixtures)와 리뷰 회귀 테스트(test_review_fixes)는 통과시킨다.
# jq 없이 동작한다.

input="$(cat)"
file="$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | sed 's/\\\\/\//g; s/\\/\//g')"
[ -z "$file" ] && exit 0

# 저장소 기준 상대 경로 (apps/<앱>/tests/...)
rel="$(printf '%s' "$file" | sed -n 's#^\(.*/\)\{0,1\}\(apps/[^/]*/tests/.*\)$#\2#p')"
[ -z "$rel" ] && exit 0

case "$rel" in
  */conftest.py|*/run.sh|*/fixtures/*|*/helpers/*|*test_review_fixes*) exit 0 ;;
esac

root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
# 아직 커밋 안 된 새 테스트(3·4단계에서 쓰는 중)는 막지 않는다
git -C "$root" cat-file -e "HEAD:$rel" 2>/dev/null || exit 0

cat <<JSON
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask","permissionDecisionReason":"승인된 테스트($rel)를 고치려 합니다. 사장 허락 없이는 고치지 않는 규칙입니다. 검사 내용·숫자는 그대로이고 찾는 위치만 바뀌는지 확인한 뒤 허용해 주세요."}}
JSON
exit 0
