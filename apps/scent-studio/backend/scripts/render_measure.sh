#!/usr/bin/env bash
# 묶음 1 Render 실측 (plan_stage4.md). backend 폴더에서:
#   bash scripts/render_measure.sh snap          측정값 한 번 (시작 시간·메모리)
#   bash scripts/render_measure.sh load [분]      health를 2초마다 부르며 30초마다 메모리 기록 (기본 10분)
#   bash scripts/render_measure.sh wake          잠든 서버를 깨우는 첫 요청 시간 + 그때 시작 시간
#   bash scripts/render_measure.sh ip            Render가 붙이는 X-Forwarded-For 모양 (가짜 값을 앞에 넣어 봄)
# .env의 RENDER_URL(https://...onrender.com)과 DEBUG_TOKEN을 쓴다. 토큰은 출력하지 않는다.
cd "$(dirname "$0")/.." || exit 1
get() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r' | sed -E "s/^[\"'](.*)[\"']$/\1/"; }
url="$(get RENDER_URL)"; url="${url%/}"; tok="$(get DEBUG_TOKEN)"
[ -n "$url" ] && [ -n "$tok" ] || { echo ".env에 RENDER_URL과 DEBUG_TOKEN이 필요합니다" >&2; exit 1; }
case "$url" in https://*) ;; *) echo "RENDER_URL은 https:// 로 시작해야 합니다(토큰이 평문으로 나가지 않게)" >&2; exit 1 ;; esac

# 토큰은 명령줄 인자로 넘기지 않고(프로세스 목록에 보임) 표준 입력 설정으로 넘긴다(리뷰 묶음 1 QA-9)
dbg() { printf 'header = "X-Debug-Token: %s"\n' "$tok" | curl -s --max-time 120 --config - "$@"; }
mem() { dbg "$url/api/v1/_debug/mem"; }
pick() { printf '%s' "$1" | grep -o "\"$2\":[^,}]*" | head -1 | cut -d: -f2-; }
line() { m="$1"; echo "ready=$(pick "$m" jvmStartToReadyMs)ms heap=$(pick "$m" heapUsedMB)/$(pick "$m" heapMaxMB)MB meta=$(pick "$m" metaspaceUsedMB)MB code=$(pick "$m" codeCacheUsedMB)MB rss=$(pick "$m" rssMB)MB rssPeak=$(pick "$m" rssPeakMB)MB cg=$(pick "$m" cgroupCurrentMB)/$(pick "$m" cgroupLimitMB)MB cgPeak=$(pick "$m" cgroupPeakMB)MB threads=$(pick "$m" threads) cpus=$(pick "$m" availableProcessors) vm=$(pick "$m" vmInfo)"; }
mask() { sed -E 's/"1\.2\.3\.4"/"FAKE-1.2.3.4"/g; s/"([0-9]{1,3}\.){3}[0-9]{1,3}"/"<IPv4>"/g; s/"[0-9A-Fa-f]{0,4}(:[0-9A-Fa-f]{0,4}){2,7}(%[^"]*)?"/"<IPv6>"/g'; }

case "${1:-snap}" in
  snap) line "$(mem)" ;;
  load)
    mins="${2:-10}"; end=$(( $(date +%s) + mins * 60 )); n=0; fail=0; maxPeak=0; maxCg=0; last=0
    while [ "$(date +%s)" -lt "$end" ]; do
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$url/api/v1/health"); n=$((n+1)); [ "$code" = "200" ] || fail=$((fail+1))
      if [ $(( $(date +%s) - last )) -ge 30 ]; then
        m="$(mem)"; last=$(date +%s); echo "$(date +%H:%M:%S) $(line "$m")"
        p=$(pick "$m" rssPeakMB); c=$(pick "$m" cgroupPeakMB)
        case "$p" in ''|null|*[!0-9]*) ;; *) [ "$p" -gt "$maxPeak" ] && maxPeak=$p ;; esac
        case "$c" in ''|null|*[!0-9]*) ;; *) [ "$c" -gt "$maxCg" ] && maxCg=$c ;; esac
      fi
      sleep 2
    done
    echo "== ${mins}분 부하 끝: health $n번(실패 $fail), 프로세스 최대 RSS ${maxPeak}MB, 컨테이너 최대 ${maxCg}MB (기준 420MB 이하)" ;;
  wake)
    t=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' --max-time 180 "$url/api/v1/health/live")
    echo "첫 요청: HTTP ${t% *}, ${t#* }초"; line "$(mem)" ;;
  ip)
    dbg -H "X-Forwarded-For: 1.2.3.4" -H "True-Client-IP: 9.9.9.9" "$url/api/v1/_debug/ip" | mask
    echo; echo "(xffFromRight에서 FAKE-1.2.3.4가 몇 번째인지로 TRUSTED_PROXY_HOPS를 정한다. 실제 IP는 가림)" ;;
  *) echo "snap | load [분] | wake | ip" >&2; exit 1 ;;
esac
