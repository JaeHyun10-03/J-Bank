#!/usr/bin/env bash
# perf 부하 발생 EC2에서 실행하는 단계들. 로컬의 perf/run-ec2.py가 SSM으로 호출한다(root).
# 사용법: loadgen.sh <명령> [인자...]   결과 파일은 /opt/perf-out/ 아래에 남긴다.
set -euo pipefail

REPO=/opt/jbank
COMPOSE_DIR="$REPO/infra/compose/perf"
OUT=/opt/perf-out
PW_FILE=/opt/perf-secrets/perf-password
ACCOUNTS=$OUT/env/accounts.json

cmd="$1"; shift
case "$cmd" in
  setup)
    # 관측 스택 기동. 대상 사설 IP를 Prometheus 설정에 넣고, perf 고객 공용 비밀번호를 여기서만 만든다.
    target_ip="$1"
    mkdir -p "$OUT/env" /opt/perf-secrets && chmod 700 /opt/perf-secrets
    [ -f "$PW_FILE" ] || (umask 077; openssl rand -hex 16 > "$PW_FILE")
    cd "$COMPOSE_DIR"
    sed "s/__TARGET_IP__/$target_ip/g" loadgen/prometheus.yml.tpl > loadgen/prometheus.yml
    echo "$target_ip" > "$OUT/env/target-ip"
    [ -f .env.loadgen ] || (umask 077; echo "GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 16)" > .env.loadgen)
    docker compose -f docker-compose.loadgen.yml --env-file .env.loadgen up -d --quiet-pull
    k6 version | tee "$OUT/env/k6-version.txt"
    ;;

  prepare)
    target_ip="$1"
    BASE_URL="https://$target_ip" PERF_PASSWORD="$(cat "$PW_FILE")" \
      python3 "$REPO/perf/prepare-accounts.py" --out "$ACCOUNTS" | tee "$OUT/env/prepare.log"
    ;;

  verify-login)
    # 복원 검증 1회(준비 단계): 발신·로그인 전용 고객 로그인과 핫 계좌 조회.
    target_ip="$1"
    BASE_URL="https://$target_ip" PERF_PASSWORD="$(cat "$PW_FILE")" \
      python3 "$REPO/perf/ec2/verify_login.py" "$ACCOUNTS" | tee "$OUT/env/restore-verify.log"
    ;;

  hot-id)
    python3 -c "import json;print(json.load(open('$ACCOUNTS'))['hot']['accountId'])"
    ;;

  k6)
    # k6 실행. 사용법: k6 <run> <스크립트> [모니터 모드] [KEY=VALUE...]
    #   모니터 모드 s1|s2|s2f: 단계별 무너짐을 판정해 한 단계 더 간 뒤 k6를 멈춘다. none: 끝까지 실행.
    run="$1"; script="$2"; monitor="${3:-none}"; shift 3 || shift $#
    # 같은 이름으로 다시 돌린 회차의 이전 파일이 섞이지 않게 비우고 시작한다(백그라운드 k6가 로그를
    # 비우기 전에 옛 SCENARIO_START를 읽는 경쟁도 이것으로 막는다).
    rm -rf "${OUT:?}/$run"
    mkdir -p "$OUT/$run"
    : > "$OUT/$run/k6.log"
    env_args=()
    for kv in "$@"; do env_args+=(-e "$kv"); done
    # S2 램프 값을 받았으면 감시·분석이 같은 단계를 쓰도록 ramp.json으로 남긴다(k6_monitor.ramp).
    python3 - "$OUT/$run/ramp.json" "$@" <<'PY'
import json, sys
keys = {"S2_START": "start", "S2_STEP": "step", "S2_MAX": "max"}
ramp = {keys[k]: int(v) for k, v in (a.split("=", 1) for a in sys.argv[2:]) if k in keys}
if ramp:
    json.dump(ramp, open(sys.argv[1], "w"))
PY
    export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
    export K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true
    export K6_PROMETHEUS_RW_PUSH_INTERVAL=5s
    set +e
    k6 run --quiet --out experimental-prometheus-rw --tag run="$run" \
      -e BASE_URL="https://$(cat "$OUT/env/target-ip")" \
      -e PERF_PASSWORD="$(cat "$PW_FILE")" -e ACCOUNTS_FILE="$ACCOUNTS" "${env_args[@]}" \
      --summary-export "$OUT/$run/k6-summary.json" \
      "$REPO/perf/k6/$script" > "$OUT/$run/k6.log" 2>&1 &
    k6_pid=$!
    # 단계 구간은 setup(전원 로그인)이 끝난 시각부터 센다(lib/ec2.js의 SCENARIO_START 로그).
    until grep -q 'SCENARIO_START' "$OUT/$run/k6.log" 2>/dev/null || ! kill -0 "$k6_pid" 2>/dev/null; do sleep 1; done
    grep -o 'SCENARIO_START [0-9T:.-]*Z' "$OUT/$run/k6.log" | head -1 | awk '{print $2}' | sed 's/\.[0-9]*Z$/Z/' \
      > "$OUT/$run/k6-start.txt"
    if [ "$monitor" != "none" ]; then
      python3 "$REPO/perf/ec2/k6_monitor.py" watch "$monitor" "$run" "$k6_pid" "$(cat "$OUT/$run/k6-start.txt")" \
        > "$OUT/$run/monitor.log" 2>&1 &
    fi
    wait "$k6_pid"; code=$?
    set -e
    date -u +%Y-%m-%dT%H:%M:%SZ > "$OUT/$run/k6-end.txt"
    # 실패 이체 멱등키(lib/ec2.js의 FAILKEY 로그)를 "키 상태 요청이름" 한 줄씩 뽑는다.
    grep -o 'FAILKEY [0-9a-f-]* [0-9]* [a-z-]*' "$OUT/$run/k6.log" | awk '{print $2, $3, $4}' \
      > "$OUT/$run/failed-transfers.txt" || true
    echo "failed transfer keys: $(wc -l < "$OUT/$run/failed-transfers.txt")"
    # --summary-export에는 setup() 반환값(perf 고객 전원의 로그인 토큰)이 그대로 들어간다. 결과물에 남기지 않는다.
    python3 -c "import json,sys; p=sys.argv[1]; d=json.load(open(p)); d['setup_data']={}; json.dump(d, open(p,'w'), indent=2)" \
      "$OUT/$run/k6-summary.json" 2>/dev/null || true
    echo "k6 exit=$code" | tee "$OUT/$run/k6-exit.txt"
    # 모니터가 멈춘 경우(SIGINT) k6 종료 코드는 0이 아닐 수 있다. 판정은 분석 결과로 한다.
    ;;

  analyze)
    # 사용법: analyze <run> <모드 s1|s2|s2f|s3|s5|warmup> [label:시작epoch:끝epoch,...]
    run="$1"; mode="$2"; windows="${3:-}"
    python3 "$REPO/perf/ec2/k6_monitor.py" analyze "$mode" "$run" \
      "$(cat "$OUT/$run/k6-start.txt")" "$(cat "$OUT/$run/k6-end.txt")" "$windows" | tee "$OUT/$run/analysis.json"
    ;;

  export)
    # 대시보드 전 패널의 query_range 원자료(REQ-12).
    run="$1"
    python3 "$REPO/perf/ec2/k6_monitor.py" export "$run" \
      "$(cat "$OUT/$run/k6-start.txt")" "$(cat "$OUT/$run/k6-end.txt")" \
      "$COMPOSE_DIR/loadgen/provisioning/dashboards/json/jbank-perf.json"
    # 수집 대상별 결측 검사(REQ-05). 결과는 prometheus/gaps.txt.
    python3 "$REPO/perf/ec2/k6_monitor.py" gaps "$run" \
      "$(cat "$OUT/$run/k6-start.txt")" "$(cat "$OUT/$run/k6-end.txt")"
    ;;

  *)
    echo "알 수 없는 명령: $cmd" >&2; exit 2 ;;
esac
