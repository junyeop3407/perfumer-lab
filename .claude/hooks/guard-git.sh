#!/usr/bin/env bash
# CLAUDE.md "절대 하지 않을 것"을 부탁이 아니라 시스템으로 막는다. (PreToolUse: Bash|PowerShell)
#   1. 강제 푸시, 커밋 기록 고쳐 쓰기, 저장소 삭제 → 항상 막음 (꼭 필요하면 사장이 직접 `!`로 실행)
#   2. git commit 직전: 커밋될 파일에 .env·키 파일·node_modules·빌드 결과물, 비밀값 패턴,
#      특정 컴퓨터 전용 경로(C:\Users\..., /Users/<이름>/, /home/<이름>/)가 있으면 막음
# jq 없이 동작한다 (Windows Git Bash에는 jq가 없는 경우가 많다).
# 종료 코드 2 = 막기 (stderr 내용이 Claude에게 전달된다), 0 = 통과.

input="$(cat)"
# 명령 문자열만 꺼낸다 (JSON 이스케이프된 따옴표·역슬래시를 대략 풀어 줌)
cmd="$(printf '%s' "$input" | tr '\n' ' ' | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"//p' \
  | sed 's/"[[:space:]]*,[[:space:]]*"[a-z_]*"[[:space:]]*:.*//; s/"[[:space:]]*}.*//' | sed 's/\\"/"/g; s/\\\\/\\/g')"
[ -z "$cmd" ] && exit 0

block() {
  echo "훅이 막았습니다: $1
이 규칙은 CLAUDE.md '절대 하지 않을 것'입니다. 우회하지 말고, 정말 필요하면 사장에게 이유를 설명하고 사장이 직접 '!' 한 줄로 실행하게 하세요." >&2
  exit 2
}

# ---- 1. 되돌릴 수 없는 git·GitHub 동작 ----
pushseg="$(printf '%s' "$cmd" | grep -Eo 'git([[:space:]]+-[^[:space:]]+)*[[:space:]]+push[^;&|]*')"
if [ -n "$pushseg" ]; then
  printf '%s' "$pushseg" | grep -Eq '(^|[[:space:]])(--force|--force-with-lease|--force-if-includes|-f|-[a-zA-Z]*f[a-zA-Z]*)([[:space:]=]|$)' \
    && block "강제 푸시(--force)는 다른 컴퓨터의 작업을 지울 수 있습니다."
  printf '%s' "$pushseg" | grep -Eq '[[:space:]]\+[^[:space:]]+' \
    && block "'+브랜치' 형태의 푸시는 강제 푸시와 같습니다."
  printf '%s' "$pushseg" | grep -Eq '(--delete|--mirror|[[:space:]]:[^[:space:]]+)' \
    && block "원격 브랜치 삭제·미러 푸시는 막혀 있습니다."
fi
printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(filter-branch|filter-repo)\b' \
  && block "커밋 기록 고쳐 쓰기(filter-branch/filter-repo)는 막혀 있습니다."
printf '%s' "$cmd" | grep -Eq 'gh[^;&|]*[[:space:]]repo[[:space:]]+(delete|archive)\b' \
  && block "저장소 삭제·보관은 사장만 할 수 있습니다."
printf '%s' "$cmd" | grep -Eq 'gh[^;&|]*[[:space:]]api[^;&|]*(-X|--method)[[:space:]]*DELETE[^;&|]*repos/' \
  && block "GitHub API로 저장소를 지우는 요청은 막혀 있습니다."

# ---- 2. 커밋 직전 검사 ----
printf '%s' "$cmd" | grep -Eq 'git([[:space:]]+-[^[:space:]]+)*[[:space:]]+commit\b' || exit 0

root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$root" || exit 0

# 커밋될 파일 목록: 스테이지된 것 + (같은 명령에 git add / commit -a 가 있으면) 아직 안 올린 변경까지
files="$(git diff --cached --name-only --diff-filter=ACMR)"
diffs="$(git diff --cached --diff-filter=ACMR -U0)"
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+add\b|commit[^;&|]*[[:space:]]-[a-zA-Z]*a'; then
  files="$files
$(git diff --name-only --diff-filter=ACMR)
$(git ls-files --others --exclude-standard)"
  diffs="$diffs
$(git diff --diff-filter=ACMR -U0)"
  while IFS= read -r f; do
    [ -f "$f" ] && diffs="$diffs
+++ b/$f
$(sed 's/^/+/' "$f" 2>/dev/null)"
  done <<< "$(git ls-files --others --exclude-standard)"
fi
files="$(printf '%s\n' "$files" | sed '/^$/d' | sort -u)"

# 2-1. 올리면 안 되는 파일
bad_files="$(printf '%s\n' "$files" | grep -E '(^|/)\.env($|\.)|\.(pem|key|p12|jks|keystore)$|(^|/)(node_modules|dist|build|\.venv|venv|__pycache__)/' | grep -Ev '(^|/)\.env\.example$')"
[ -n "$bad_files" ] && block "커밋하면 안 되는 파일이 있습니다(.env·키 파일·다시 만들 수 있는 폴더):
$bad_files
→ git restore --staged <파일> 로 빼고, 필요하면 .gitignore에 추가하세요. 값은 .env.example에 이름만."

# 2-2. 비밀값 패턴과 컴퓨터 전용 경로 (추가된 줄만, 훅 파일 자신은 제외)
added="$(printf '%s\n' "$diffs" | awk '
  /^\+\+\+ / { f=$2; sub(/^b\//,"",f); next }
  /^\+/ && f !~ /^\.claude\/hooks\// { print f ": " substr($0,2) }')"

secrets="$(printf '%s\n' "$added" | grep -E \
  -e 'AKIA[0-9A-Z]{16}' \
  -e 'AIza[0-9A-Za-z_-]{35}' \
  -e '(sk|rk)-(live|proj|ant)?-?[A-Za-z0-9_-]{20,}' \
  -e 'gh[pousr]_[A-Za-z0-9]{36}' \
  -e 'xox[abpr]-[A-Za-z0-9-]{10,}' \
  -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
  -e '(password|passwd|secret|api_?key|token)[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"'$<{]{8,}["'"'"']' \
  | head -n 5)"
[ -n "$secrets" ] && block "비밀값으로 보이는 내용이 커밋에 들어 있습니다:
$secrets
→ 값은 .env로 옮기고 코드에서는 환경변수로 읽으세요. (오탐이면 사장에게 확인받으세요)"

paths="$(printf '%s\n' "$added" | grep -Ev '^[^:]*\.md: ' | grep -E \
  -e '[A-Za-z]:\\\\?Users\\\\?' \
  -e '/Users/[A-Za-z0-9._-]+/' \
  -e '/home/[A-Za-z0-9._-]+/' \
  | head -n 5)"
[ -n "$paths" ] && block "특정 컴퓨터에만 있는 경로가 코드·설정에 들어 있습니다:
$paths
→ 저장소 기준 상대 경로로 바꾸세요."

exit 0
