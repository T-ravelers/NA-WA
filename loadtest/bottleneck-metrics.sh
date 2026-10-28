#!/usr/bin/env bash
# 측정 구간 하나의 병목 지표 3개를 Prometheus에서 읽는다(#582).
#
# 사용: bottleneck-metrics.sh <시작 epoch초> <끝 epoch초>
# k6 실행 직전과 직후에 `date +%s`로 잰 값을 넘긴다. 마지막 스크레이프(5초 주기)가
# 구간에 들어오도록 끝난 뒤 6초쯤 기다렸다가 실행한다.
set -euo pipefail

readonly PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090}"

if [[ $# -ne 2 || ! $1 =~ ^[0-9]+$ || ! $2 =~ ^[0-9]+$ ]] || (($2 <= $1)); then
  echo "사용: $0 <시작 epoch초> <끝 epoch초>" >&2
  exit 1
fi

start="$1"
end="$2"
window="$((end - start))s"

query() {
  curl -fsS --get "${PROMETHEUS_URL}/api/v1/query" \
    --data-urlencode "query=$1" --data-urlencode "time=${end}" |
    python3 -c 'import json, sys
r = json.load(sys.stdin)["data"]["result"]
print(r[0]["value"][1] if r else "nan")'
}

# echo "$(query ...)"로 쓰면 조회가 실패해도 echo의 0이 남는다. 값을 먼저 받아
# 조회 실패에서 멈추고, 다섯 값이 모두 모였을 때만 출력한다.
pending_max=$(query "max(max_over_time(hikaricp_connections_pending[${window}]))")
busy_max=$(query "max(max_over_time(tomcat_threads_busy[${window}]))")
config_max=$(query "max(tomcat_threads_config_max)")
# increase()는 구간 끝을 외삽하므로 횟수가 소수로 나온다. 기록할 때 반올림한다.
gc_sum=$(query "sum(increase(jvm_gc_pause_seconds_sum[${window}]))")
gc_count=$(query "sum(increase(jvm_gc_pause_seconds_count[${window}]))")

printf '%s\n' \
  "window=${window}" \
  "hikaricp_connections_pending_max=${pending_max}" \
  "tomcat_threads_busy_max=${busy_max}" \
  "tomcat_threads_config_max=${config_max}" \
  "gc_pause_seconds_sum=${gc_sum}" \
  "gc_pause_count=${gc_count}"
