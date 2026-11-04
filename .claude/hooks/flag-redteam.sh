#!/usr/bin/env bash
# "결제·로그인·회원정보를 고쳤으면 레드팀을 다시 돌린다"를 시스템으로 지킨다. (PostToolUse: Edit|Write|MultiEdit)
# apps/<앱>/ 안에서 보안에 민감한 파일(로그인·토큰·결제·쿠폰·회원·관리자 등)을 고치면
# apps/<앱>/.redteam-needed 표시를 만들고, 처음 만들 때 한 번만 Claude에게 알린다.
# 표시는 레드팀을 다시 돌려 뚫린 것을 다 고친 뒤 지운다(/build-app "레드팀 공격").
# 테스트·문서 파일은 무시한다. jq 없이 동작한다.

input="$(cat)"
file="$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | sed 's/\\\\/\//g; s/\\/\//g')"
[ -z "$file" ] && exit 0

# apps/<앱>/ 아래 파일만
app="$(printf '%s' "$file" | sed -n 's#^\(.*/\)\{0,1\}apps/\([^/]*\)/.*$#\2#p')"
[ -z "$app" ] && exit 0
rel="$(printf '%s' "$file" | sed -n 's#^\(.*/\)\{0,1\}\(apps/[^/]*/.*\)$#\2#p')"

# 테스트·문서·기록·화면 꾸밈 파일은 무시
case "$rel" in
  */tests/*|*/test/*|*/docs/*|*.md|*/.redteam-needed) exit 0 ;;
  *.css|*.scss|*.sass|*.less|*.svg|*.png|*.jpg|*.jpeg|*.gif|*.webp|*.ico|*.woff|*.woff2|*.ttf|*.lock) exit 0 ;;
esac

# 보안에 민감한 파일인지: 경로 조각(폴더·파일 이름)이 아래 단어로 "시작"할 때만 (대소문자 무시)
# 예) AuthController.java, jwt/, PaymentService.java, admin/ → 해당 / border.css, endpoint.ts, UserCard.tsx → 아님
low="$(printf '%s' "$rel" | tr 'A-Z' 'a-z')"
printf '%s' "$low" | grep -Eq '(^|[/._-])(auth|login|logout|signin|signup|session|token|jwt|password|passwd|oauth|security|payment|pay[/._-]|checkout|order|price|coupon|point|wallet|refund|billing|member|account|admin|role|permission|upload|webhook|user(s)?(service|controller|repository|repo|api|route|handler|[/._-]))' || exit 0

root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
flag="$root/apps/$app/.redteam-needed"

# 이미 표시가 있으면 조용히 끝낸다 (같은 알림을 여러 번 보내지 않음)
[ -f "$flag" ] && exit 0

printf '%s\n' "$rel" > "$flag" 2>/dev/null || exit 0

cat <<JSON
{"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":"보안에 민감한 파일($rel)을 고쳤습니다. apps/$app/.redteam-needed 표시를 만들었습니다. 이 앱은 QA 보고·배포·커밋 마무리 전에 red-team 에이전트를 다시 돌려야 합니다(/build-app '레드팀 공격'). 다 고친 뒤 표시를 지우세요."}}
JSON
exit 0
