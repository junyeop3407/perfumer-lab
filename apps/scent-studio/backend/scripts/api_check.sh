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
call "app 함수 직접 실행 시도"            "404/406 (노출 안 됨)"            -X POST -H "Content-Profile: app" -H "Content-Type: application/json" -d '{}' "$base/rpc/purge_old_events"
