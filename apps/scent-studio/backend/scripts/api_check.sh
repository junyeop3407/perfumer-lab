#!/usr/bin/env bash
# 공개 키(publishable)로 Supabase Data API를 직접 불러 막힘·열림을 확인한다. 읽기만 한다.
# 사용: backend 폴더에서  bash scripts/api_check.sh
# .env의 DB_MIGRATION_USER(postgres.<참조값>)에서 프로젝트 주소를 만들고, SUPABASE_PUBLISHABLE_KEY를 쓴다. 키는 출력하지 않는다.
cd "$(dirname "$0")/.." || exit 1
get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r'; }
ref="$(get DB_MIGRATION_USER)"; ref="${ref#postgres.}"
key="$(get SUPABASE_PUBLISHABLE_KEY)"
if [ -z "$ref" ] || [ -z "$key" ] || [ "${key#<}" != "$key" ]; then
  echo ".env에 DB_MIGRATION_USER와 SUPABASE_PUBLISHABLE_KEY가 필요합니다" >&2; exit 1
fi
base="https://$ref.supabase.co/rest/v1"
call() { # 이름, 기대, curl 인자...
  name="$1"; expect="$2"; shift 2
  out="$(curl -s -w '\n%{http_code}' -H "apikey: $key" -H "Authorization: Bearer $key" "$@")"
  code="$(printf '%s' "$out" | tail -1)"; body="$(printf '%s' "$out" | sed '$d' | head -c 300)"
  echo "[$name] HTTP $code — 기대: $expect"
  echo "    $body"
}
call "keepalive (공개 키로 실행)"        "200, 값 1"                      -X POST -H "Content-Type: application/json" -d '{}' "$base/rpc/keepalive"
call "app.blends 읽기 (app 스키마 지정)"  "406 PGRST106: app은 노출 안 됨"  -H "Accept-Profile: app" "$base/blends?select=*"
call "app.consents 읽기"                 "406 PGRST106"                   -H "Accept-Profile: app" "$base/consents?select=*"
call "app에 쓰기 시도"                    "406 PGRST106"                   -X POST -H "Content-Profile: app" -H "Content-Type: application/json" -d '{}' "$base/favorites"
call "public에서 blends 찾기"             "404 (public에는 그런 표 없음)"   "$base/blends?select=*"
call "app.catalog_releases 읽기 (V2)"     "406 PGRST106"                   -H "Accept-Profile: app" "$base/catalog_releases?select=*"
call "app.session_alive 직접 실행 (V2)"   "404/406 (노출 안 됨)"            -X POST -H "Content-Profile: app" -H "Content-Type: application/json" -d '{"p_session_id":null,"p_user_id":null}' "$base/rpc/session_alive"
call "app 함수 직접 실행 시도"            "404/406 (노출 안 됨)"            -X POST -H "Content-Profile: app" -H "Content-Type: application/json" -d '{}' "$base/rpc/purge_old_events"

# 이메일 가입 (묶음 9 전에 꺼야 함: 구글 로그인만). 먼저 설정을 읽기만 하고, 꺼져 있을 때만 실제 가입 시도로 막힘을 확인한다.
# (켜져 있을 때 시도하면 진짜 계정이 생기고 메일이 나가므로 시도하지 않는다)
auth="https://$ref.supabase.co/auth/v1"
settings="$(curl -s -H "apikey: $key" "$auth/settings")"
compact="$(printf '%s' "$settings" | tr -d ' \n\r')"
if ! printf '%s' "$compact" | grep -q '"external":{'; then email_on="unknown"
elif printf '%s' "$compact" | grep -q '"disable_signup":true' || printf '%s' "$compact" | grep -q '"email":false'; then email_on="off"
else email_on="on"; fi
echo "[이메일 가입 설정] $email_on — 기대: off (구글 로그인만)"
if [ "$email_on" = "off" ]; then
  call "공개 키로 이메일 가입 시도" "4xx (가입 막힘)" -X POST -H "Content-Type: application/json"     -d "{\"email\":\"probe-$(date +%s)@example.com\",\"password\":\"Probe$(date +%s)Xx9\"}" "$auth/signup"
else
  echo "    아직 켜져 있음 → 가입 시도는 건너뜀 (plan.md 출시 직전 확인 항목 6)"
fi

# 카탈로그 (묶음 6): 비공개 버킷이라 공개 주소·공개 키로는 받을 수 없어야 한다. 읽기만.
# 확인할 버전은 첫 인자(기본 2026.11.05): bash scripts/api_check.sh 2026.12.01
storage="https://$ref.supabase.co/storage/v1"
ver="${1:-2026.11.05}"
call "카탈로그 공개 주소로 받기"   "400/404 (공개 아님)"     "$storage/object/public/catalog/catalog-$ver.json.gz"
call "공개 키로 카탈로그 받기"     "400/403/404 (막힘)"      -H "apikey: $key" -H "Authorization: Bearer $key" "$storage/object/catalog/catalog-$ver.json.gz"
call "공개 키로 버킷 목록 보기"    "빈 목록 [] 또는 4xx"     -H "apikey: $key" -H "Authorization: Bearer $key" "$storage/bucket"
