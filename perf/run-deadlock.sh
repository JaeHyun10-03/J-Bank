#!/usr/bin/env bash
# 교착 재현 실험 실행. 절차와 판정은 docs/runbook/transfer-deadlock-experiment.md, 결과는 perf/results/deadlock/.
#
#   로컬 1회차:      (LOGIN_ID·PASSWORD를 저장소 밖 파일에서 export) perf/run-deadlock.sh local <조건> <VU> <회차>
#   운영 6회:        INSTANCE_ID=<인스턴스ID> LOGIN_ID=<로그인ID> perf/run-deadlock.sh prod-all [--from <조건>-vu<VU>]
#   운영 복귀 확인:  INSTANCE_ID=<인스턴스ID> LOGIN_ID=<로그인ID> perf/run-deadlock.sh prod-restore-check
#   실험 전 점검:    INSTANCE_ID=<인스턴스ID> perf/run-deadlock.sh prod-check   (읽기 전용, 로그인 없음)
#
# 조건: current | sorted-for-update | unsorted-for-update. 운영 비밀번호는 read -s로 한 번 받아 셸 변수에만 둔다
# (인자·파일·로그에 남기지 않는다). 계좌는 로그인한 고객의 ACTIVE 계좌 중 계좌 ID가 가장 작은 두 개이고,
# 계좌번호는 k6 환경변수로만 넘기며 결과 파일에는 계좌 ID만 쓴다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS="$ROOT/perf/results/deadlock"
REGION="${AWS_REGION:-ap-northeast-2}"
MEASURE=60s
WARMUP=20s
WARMUP_VUS=5
STEP_LIMIT=300     # SSM 명령·DB 대기 한 단계의 상한(초)
RELEASE_LIMIT=900  # 조건 ① 뒤 알림 해제 대기 상한(초)
PROD_ORDER=(current-vu50 current-vu100 sorted-for-update-vu50 sorted-for-update-vu100
  unsorted-for-update-vu50 unsorted-for-update-vu100)

usage() {
  sed -n '4,7p' "$0" >&2
  exit 1
}

MODE="${1:-}"
case "$MODE" in
  local)
    [ "$#" -eq 4 ] || usage
    ENV_NAME=local
    BASE_URL="${BASE_URL:-http://localhost:8080}"
    : "${PASSWORD:?PASSWORD 환경변수가 필요하다}"
    ;;
  prod-all | prod-restore-check)
    ENV_NAME=prod
    BASE_URL="${BASE_URL:-https://api.j-bank.site}"
    : "${INSTANCE_ID:?INSTANCE_ID 환경변수가 필요하다}"
    read -r -s -p "운영 로그인 비밀번호: " PASSWORD
    echo >&2
    ;;
  prod-check)
    ENV_NAME=prod
    BASE_URL="${BASE_URL:-https://api.j-bank.site}"
    : "${INSTANCE_ID:?INSTANCE_ID 환경변수가 필요하다}"
    ;;
  *) usage ;;
esac
[ "$MODE" = prod-check ] || : "${LOGIN_ID:?LOGIN_ID 환경변수가 필요하다}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$RESULTS/$ENV_NAME"
PROGRESS="$RESULTS/$ENV_NAME/progress.txt"

log() { echo "$(date '+%F %T') $*" | tee -a "$PROGRESS" >&2; }
now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }
readyz() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE_URL/readyz" || true; }

# Ctrl-C·종료 신호: 대기 중인 SSM 명령을 취소하고 진행 중인 회차를 기록한 뒤 끝낸다(재개는 --from).
# 측정을 시작하기 전이면 측정 미실행, 종료값까지 남았으면 완료(조건 ①만 해제 미확인)로 남긴다.
CURRENT_ROUND_DIR=""
on_interrupt() {
  if [ -f "$TMP/ssm.id" ]; then
    aws ssm cancel-command --region "$REGION" --command-id "$(cat "$TMP/ssm.id")" > /dev/null 2>&1 || true
  fi
  if [ -n "$CURRENT_ROUND_DIR" ] && [ ! -f "$CURRENT_ROUND_DIR/result.json" ]; then
    if [ -s "$CURRENT_ROUND_DIR/after.json" ]; then
      local release=-
      [[ "$CURRENT_ROUND_DIR" == *unsorted-for-update* ]] && release="해제 미확인(사용자 중단)"
      write_result "$CURRENT_ROUND_DIR" "완료" "" "$release"
    elif [ "${measuring:-no}" = yes ]; then
      write_result "$CURRENT_ROUND_DIR" "사용자 중단" "종료값 전 INT/TERM" "-"
    else
      write_result "$CURRENT_ROUND_DIR" "측정 미실행(사용자 중단)" "INT/TERM" "-"
    fi
  fi
  log "사용자 중단(INT/TERM). 복구 후 --from으로 다음 회차부터 재개한다"
  exit 130
}
trap on_interrupt INT TERM

# 대기 상한을 넘겼을 때의 판정(운영): 그동안 readyz가 한 번도 200이 아니면 무응답, 200이 있었으면 시간 초과.
timeout_reason() {
  if [ "$1" = yes ]; then echo "시간 초과"; else echo "무응답"; fi
}

# 운영 호스트에서 실행할 스크립트 본문과, 그것을 임시 파일로 풀어 ec2-user로 실행하는 SSM 명령.
remote_script() {
  printf 'set -euo pipefail\nexport HOME=/home/ec2-user\ncd /opt/jbank/infra/compose\nDC="docker compose -f docker-compose.prod.yml"\n%s\n' "$1"
}
remote_command() {
  echo "f=\$(mktemp /tmp/deadlock-cmd.XXXXXX) && echo $1 | base64 -d > \"\$f\" && chmod 644 \"\$f\" && runuser -u ec2-user -- bash \"\$f\" < /dev/null; rc=\$?; rm -f \"\$f\"; exit \$rc"
}

# 대상 호스트의 compose 폴더에서 셸 스크립트를 실행한다. $DC는 그 환경의 docker compose 명령이다.
# 실패하면 $TMP/stop에 사유를 남기고 0이 아닌 값을 돌려준다.
host() {
  if [ "$ENV_NAME" = local ]; then
    (cd "$ROOT/infra/compose" && COMPOSE_PROFILES=core,api,observability DC="docker compose" bash -c "$1") ||
      { echo "명령 실패" > "$TMP/stop"; return 1; }
    return 0
  fi
  local b64 id status saw200=no deadline=$((SECONDS + STEP_LIMIT))
  b64="$(remote_script "$1" | base64 | tr -d '\n')"
  # 스크립트는 표준 입력이 아니라 파일로 넘긴다. 표준 입력으로 넘기면 `docker compose exec -T`가
  # 아직 읽지 않은 다음 줄을 컨테이너로 가져가 버린다.
  if ! id="$(aws ssm send-command --region "$REGION" --instance-ids "$INSTANCE_ID" \
    --document-name AWS-RunShellScript --timeout-seconds 600 \
    --parameters "$(jq -cn --arg c "$(remote_command "$b64")" '{commands: [$c]}')" \
    --query Command.CommandId --output text 2> "$TMP/ssm.err")"; then
    log "SSM 전송 실패: $(head -c 300 "$TMP/ssm.err")"
    [ "$(readyz)" = 200 ] && saw200=yes
    timeout_reason "$saw200" > "$TMP/stop"
    return 1
  fi
  # 대기 중 Ctrl-C가 오면 on_interrupt가 이 명령을 취소한다(host는 대개 $(...) 안이라 파일로 넘긴다).
  echo "$id" > "$TMP/ssm.id"
  while :; do
    status="$(aws ssm get-command-invocation --region "$REGION" --command-id "$id" --instance-id "$INSTANCE_ID" \
      --query Status --output text 2> /dev/null || echo Pending)"
    case "$status" in
      Success)
        rm -f "$TMP/ssm.id"
        aws ssm get-command-invocation --region "$REGION" --command-id "$id" --instance-id "$INSTANCE_ID" \
          --query StandardOutputContent --output text
        return 0
        ;;
      Failed | Cancelled | TimedOut | Cancelling)
        rm -f "$TMP/ssm.id"
        log "SSM 명령 실패($status): $(aws ssm get-command-invocation --region "$REGION" --command-id "$id" \
          --instance-id "$INSTANCE_ID" --query StandardErrorContent --output text | head -c 500)"
        echo "명령 실패" > "$TMP/stop"
        return 1
        ;;
    esac
    [ "$(readyz)" = 200 ] && saw200=yes
    if [ "$SECONDS" -gt "$deadline" ]; then
      # 상한을 넘긴 명령이 스크립트가 끝난 뒤에 실행되지 않게 취소한다(예: flock을 기다리던 전략 적용).
      aws ssm cancel-command --region "$REGION" --command-id "$id" > /dev/null 2>&1 || true
      rm -f "$TMP/ssm.id"
      timeout_reason "$saw200" > "$TMP/stop"
      return 1
    fi
    sleep 3
  done
}

psql_json() {
  host "\$DC exec -T postgres psql -U jbank -d jbank -Atq -v ON_ERROR_STOP=1 <<'SQL'
$1
SQL"
}

# 로그인해 쿠키 헤더를 만들고 실험 계좌 두 개(ID 오름차순)를 고른다.
login_and_resolve_accounts() {
  local cookie customer accounts
  # 비밀번호는 명령 인자로 넘기지 않는다(ps에 보임). jq는 환경변수로 읽고, 본문은 파이프로 curl에 넘긴다.
  PASSWORD="$PASSWORD" jq -cn --arg l "$LOGIN_ID" '{loginId: $l, password: env.PASSWORD}' |
    curl -sf -D "$TMP/login.headers" -o "$TMP/login.json" -H 'Content-Type: application/json' \
      --data-binary @- "$BASE_URL/api/v1/auth/login" || { log "로그인 실패"; return 1; }
  cookie="$(grep -i '^set-cookie:' "$TMP/login.headers" | sed -E 's/^[Ss]et-[Cc]ookie: ([^;]*).*/\1/' |
    paste -sd ';' - | sed 's/;/; /g')"
  # 쿠키·CSRF 토큰도 인자 대신 헤더 파일로 넘긴다($TMP는 본인만 읽는 임시 폴더).
  printf 'Cookie: %s\nX-CSRF-TOKEN: %s\n' "$cookie" \
    "$(printf '%s' "$cookie" | tr ';' '\n' | sed -n 's/^ *XSRF-TOKEN=//p')" > "$TMP/auth.headers"
  customer="$(jq -r .data.customerId "$TMP/login.json")"
  accounts="$(curl -sf -H @"$TMP/auth.headers" "$BASE_URL/api/v1/customers/$customer/accounts?status=ACTIVE&size=50" |
    jq -c '[.data.content[] | {id: (.accountId | tonumber), number: .accountNumber}] | sort_by(.id) | .[0:2]')"
  [ "$(jq length <<< "$accounts")" -eq 2 ] || { log "ACTIVE 계좌가 2개 이상 필요하다"; return 1; }
  ID_A="$(jq -r '.[0].id' <<< "$accounts")"
  ID_B="$(jq -r '.[1].id' <<< "$accounts")"
  NUM_A="$(jq -r '.[0].number' <<< "$accounts")"
  NUM_B="$(jq -r '.[1].number' <<< "$accounts")"
}

# 설정 복귀 확인용 이체 1건(A→B 1,000원). HTTP 코드와 거래 상태를 출력한다.
one_transfer() {
  login_and_resolve_accounts || { echo "로그인 실패"; return 0; }
  curl -s -o "$TMP/transfer.json" -w '%{http_code}' -X POST "$BASE_URL/api/v1/transfers" \
    -H 'Content-Type: application/json' -H @"$TMP/auth.headers" \
    -H "Idempotency-Key: $(uuidgen)" \
    --data-binary "$(jq -cn --arg f "$NUM_A" --arg t "$NUM_B" \
      '{fromAccountNumber: $f, toAccountNumber: $t, amount: "1000.00", memo: "deadlock restore check"}')" \
    > "$TMP/transfer.status" || true
  echo "HTTP $(cat "$TMP/transfer.status"), $(jq -r '.data.status // .error.code' "$TMP/transfer.json" 2> /dev/null)"
}

snapshot() {
  psql_json "select json_build_object(
  'at', to_char(now() at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),
  'deadlocks', (select deadlocks from pg_stat_database where datname = current_database()),
  'transactions_table_rows', (select count(*) from transactions),
  'unapplied', (select count(*) from pending_credits where applied_at is null and account_id in ($ID_A, $ID_B)),
  'completed', (select count(*) from transactions where status = 'COMPLETED'
                and from_account_id in ($ID_A, $ID_B) and to_account_id in ($ID_A, $ID_B)),
  'ledger', (select count(*) from ledger_entries where account_id in ($ID_A, $ID_B)),
  'balance_sum', (select sum(current_balance_cache) from accounts where account_id in ($ID_A, $ID_B)),
  'negative', (select count(*) from accounts where account_id in ($ID_A, $ID_B) and current_balance_cache < 0));"
}

# 미반영 입금이 0이 될 때까지 기다린다. 상한을 넘기면 사유를 남기고 실패한다.
wait_unapplied_zero() {
  local deadline=$((SECONDS + STEP_LIMIT)) saw200=no snap
  while :; do
    snap="$(snapshot)" || return 1
    [ "$(jq .unapplied <<< "$snap")" -eq 0 ] && return 0
    [ "$(readyz)" = 200 ] && saw200=yes
    if [ "$SECONDS" -gt "$deadline" ]; then
      if [ "$ENV_NAME" = prod ]; then timeout_reason "$saw200" > "$TMP/stop"; else echo "시간 초과" > "$TMP/stop"; fi
      log "미반영 입금이 ${STEP_LIMIT}초 안에 0이 되지 않았다(남은 $(jq .unapplied <<< "$snap")건)"
      return 1
    fi
    sleep 2
  done
}

# 락 대기 타이머(db.lock.wait)를 Prometheus에서 메서드별로 읽는다(기록용).
lock_wait_metrics() {
  local q url res out='{}'
  for q in 'sum by (method) (db_lock_wait_seconds_count)' 'sum by (method) (db_lock_wait_seconds_sum)' \
    'max by (method) (db_lock_wait_seconds_max)'; do
    url="http://localhost:9090/api/v1/query?query=$(jq -rn --arg q "$q" '$q | @uri')"
    if [ "$ENV_NAME" = local ]; then
      res="$(curl -sf "$url")" || return 1
    else
      res="$(host "\$DC exec -T prometheus wget -qO- '$url'")" || return 1
    fi
    out="$(jq --arg q "$q" --argjson r "$res" '. + {($q): $r.data.result}' <<< "$out")"
  done
  echo "$out"
}

# api를 그 전략으로 재생성하고, 준비되면 기동 로그의 전략을 출력한다. 운영은 .env의 그 한 줄만 바꾼다(current는 줄 제거).
apply_strategy() {
  local strategy="$1" line=""
  [ "$strategy" = current ] || line="JBANK_TRANSFER_LOCK_STRATEGY=$strategy"
  if [ "$ENV_NAME" = local ]; then
    host "JBANK_TRANSFER_LOCK_STRATEGY=$strategy \$DC up -d --no-deps --force-recreate api >/dev/null 2>&1
for i in \$(seq 1 60); do
  [ \"\$(docker inspect -f '{{.State.Health.Status}}' \$(\$DC ps -q api))\" = healthy ] && break; sleep 3
done
[ \"\$(docker inspect -f '{{.State.Health.Status}}' \$(\$DC ps -q api))\" = healthy ] || { echo 'api가 healthy가 되지 않았다' >&2; exit 1; }
docker logs \$(\$DC ps -q api) 2>&1 | grep -o '이체 락 전략: [a-z-]*' | tail -1"
  else
    # 전략 줄을 붙이기 전에 .env 끝 줄바꿈을 보장한다(없으면 앞 줄에 붙어 복귀 때 지워지지 않는다).
    host "flock /var/lib/jbank/deploy.lock bash -s <<'EOS'
set -e
sed -i '/^JBANK_TRANSFER_LOCK_STRATEGY=/d' .env
if [ -n '$line' ]; then
  [ -z \"\$(tail -c1 .env)\" ] || echo >> .env
  echo '$line' >> .env
fi
docker compose -f docker-compose.prod.yml up -d --no-deps --force-recreate api >/dev/null 2>&1
EOS
for i in \$(seq 1 80); do
  [ \"\$(docker inspect -f '{{.State.Health.Status}}' \$(\$DC ps -q api))\" = healthy ] && break; sleep 3
done
[ \"\$(docker inspect -f '{{.State.Health.Status}}' \$(\$DC ps -q api))\" = healthy ] || { echo 'api가 healthy가 되지 않았다' >&2; exit 1; }
echo \".env 줄 수: \$(grep -c '^JBANK_TRANSFER_LOCK_STRATEGY=' .env || true) \$(grep '^JBANK_TRANSFER_LOCK_STRATEGY=' .env || true)\"
echo \"컨테이너 변수: \$(\$DC exec -T api printenv JBANK_TRANSFER_LOCK_STRATEGY || echo 없음)\"
docker logs \$(\$DC ps -q api) 2>&1 | grep -o '이체 락 전략: [a-z-]*' | tail -1"
  fi
}

# k6가 0이 아닌 값으로 끝나거나 요약이 없으면 실패한다(setup 로그인 실패 등). 이때 대상이 응답하지 않으면 무응답이다.
run_k6() {
  local vus="$1" duration="$2" out="$3" rc=0
  BASE_URL="$BASE_URL" ACCOUNT_A="$NUM_A" ACCOUNT_B="$NUM_B" LOGIN_ID="$LOGIN_ID" PASSWORD="$PASSWORD" \
    VUS="$vus" DURATION="$duration" \
    k6 run --quiet --summary-export "$out/k6-summary.json" "$ROOT/perf/k6/deadlock.js" > "$out/k6-log.txt" 2>&1 ||
    rc=$?
  # summary-export가 setup()의 반환값(쿠키·CSRF 토큰)을 setup_data에 넣으므로 비운다.
  if [ -s "$out/k6-summary.json" ]; then
    jq '.setup_data = {}' "$out/k6-summary.json" > "$out/k6-summary.json.tmp" &&
      mv "$out/k6-summary.json.tmp" "$out/k6-summary.json"
  fi
  if [ "$rc" -ne 0 ] || [ ! -s "$out/k6-summary.json" ]; then
    log "k6 실패(종료 코드 $rc)"
    if [ "$(readyz)" = 200 ]; then echo "명령 실패" > "$TMP/stop"; else echo "무응답" > "$TMP/stop"; fi
    return 1
  fi
}

# postgres 로그를 회차 시작 시각부터 저장한다(Loki가 일부를 버릴 수 있어 파일로도 남김).
# 운영은 SSM 표준 출력이 약 24,000자에서 잘리므로 서버에서 압축·base64로 만든 뒤 나눠 받는다.
backup_postgres_log() {
  local since="$1" out="$2" size chunk=20000 start
  if [ "$ENV_NAME" = local ]; then
    (cd "$ROOT/infra/compose" && COMPOSE_PROFILES=core,api,observability \
      docker compose logs --no-log-prefix --no-color --since "$since" postgres > "$out/postgres-log.txt" 2>&1)
    return 0
  fi
  size="$(host "\$DC logs --no-log-prefix --no-color --since '$since' postgres 2>&1 | gzip -c | base64 -w0 > /tmp/deadlock-pg.b64
wc -c < /tmp/deadlock-pg.b64")" || return 1
  : > "$TMP/pg.b64"
  for ((start = 1; start <= size; start += chunk)); do
    host "cut -c $start-$((start + chunk - 1)) /tmp/deadlock-pg.b64" | tr -d '\n' >> "$TMP/pg.b64" || return 1
  done
  base64 -d < "$TMP/pg.b64" | gunzip > "$out/postgres-log.txt"
}

# 앞 회차가 중간에 멈춰 남은 표본 수집이 있으면 먼저 끈다.
mem_sampler_start() {
  host "pkill -f /tmp/deadlock-mem.sh || true
cat > /tmp/deadlock-mem.sh <<'EOS'
while :; do echo \"\$(date +%s) \$(awk '/MemAvailable/{print \$2}' /proc/meminfo)\"; sleep 1; done
EOS
setsid nohup bash /tmp/deadlock-mem.sh > /tmp/deadlock-mem.log 2>&1 < /dev/null &
echo \$! > /tmp/deadlock-mem.pid" > /dev/null
}

# 측정 중 최저 가용 메모리(MiB).
mem_sampler_stop() {
  host "kill \$(cat /tmp/deadlock-mem.pid) 2>/dev/null || true
awk 'NR == 1 || \$2 < m { m = \$2 } END { printf \"%d\", m / 1024 }' /tmp/deadlock-mem.log"
}

# 조건 ① 뒤 DeadlockDetected 해제를 기다린다. 로컬은 mailpit의 RESOLVED 메일, 운영은 Alertmanager·Prometheus에서
# 알림이 사라진 뒤 6분을 더 기다린다(해제 메일 발송 시간. 메일 확인은 사용자가 Gmail에서 한다).
# 출력: "해제 확인" 또는 "해제 미확인(사유)". 운영 SSM이 실패하면 1을 돌려준다.
wait_release() {
  local after="$1" out="$2" deadline=$((SECONDS + RELEASE_LIMIT)) alerts am prom seen=no
  log "알림 해제 대기(상한 ${RELEASE_LIMIT}초)"
  while [ "$SECONDS" -le "$deadline" ]; do
    if [ "$ENV_NAME" = local ]; then
      curl -sf 'http://localhost:8025/api/v1/messages?limit=100' |
        jq '[.messages[] | {subject: .Subject, created: .Created}]' > "$out/mail.json"
      if jq -e --arg after "$after" \
        'any(.[]; (.subject | test("RESOLVED")) and (.subject | test("DeadlockDetected")) and .created > $after)' \
        "$out/mail.json" > /dev/null; then
        echo "해제 확인"
        return 0
      fi
    else
      alerts="$(host "\$DC exec -T alertmanager wget -qO- 'http://localhost:9093/api/v2/alerts?filter=alertname%3D%22DeadlockDetected%22'
echo
\$DC exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=ALERTS%7Balertname%3D%22DeadlockDetected%22%7D'")" || {
        echo "해제 미확인($(cat "$TMP/stop"))"
        return 1
      }
      am="$(head -1 <<< "$alerts" | jq length)"
      prom="$(tail -1 <<< "$alerts" | jq '.data.result | length')"
      echo "$(now_utc) alertmanager=$am prometheus=$prom" >> "$out/alerts.txt"
      if [ "$am" -gt 0 ] || [ "$prom" -gt 0 ]; then seen=yes; fi
      if [ "$am" -eq 0 ] && [ "$prom" -eq 0 ]; then
        sleep 360
        # 대기 동안 발생을 한 번도 못 봤으면(이미 해제됐거나 발생하지 않음) 그렇게 남긴다.
        if [ "$seen" = yes ]; then echo "해제 확인"; else echo "해제 확인(대기 중 발생 미관측)"; fi
        return 0
      fi
    fi
    sleep 15
  done
  echo "해제 미확인(상한 도달)"
}

# 로컬 ① 회차: Loki에서 교착 로그(deadlock detected)와 대기 관계(DETAIL) 줄 수, 표본 줄을 남긴다.
loki_deadlock_lines() {
  local start="$1" end="$2" p count samples out='{}'
  for p in 'deadlock detected' 'DETAIL:  Process'; do
    count="$(curl -sfG 'http://localhost:3100/loki/api/v1/query' \
      --data-urlencode "query=sum(count_over_time({service=\"postgres\"} |= \"$p\" [$((end - start))s]))" \
      --data-urlencode "time=$end" | jq '.data.result[0].value[1] // "0" | tonumber')"
    samples="$(curl -sfG 'http://localhost:3100/loki/api/v1/query_range' \
      --data-urlencode "query={service=\"postgres\"} |= \"$p\"" \
      --data-urlencode "start=${start}000000000" --data-urlencode "end=${end}000000000" --data-urlencode 'limit=6' |
      jq '[.data.result[].values[][1]] | .[0:6]')"
    out="$(jq --arg p "$p" --argjson c "$count" --argjson s "$samples" '. + {($p): {count: $c, samples: $s}}' <<< "$out")"
  done
  echo "$out"
}

write_result() {
  jq -n --arg status "$2" --arg reason "$3" --arg release "$4" --arg at "$(now_utc)" \
    '{status: $status, reason: $reason, release: $release, finished_at: $at}' > "$1/result.json"
}

# 회차 하나. 중단되면 결과 이름과 사유를 기록하고 1을 돌려준다. 측정 전에 멈춘 회차는 "측정 미실행"이다.
run_round() {
  local strategy="$1" vus="$2" round="$3" dir started measured=no measuring=no logline release="-" mem measure_end
  dir="$RESULTS/$ENV_NAME/$strategy-vu$vus-r$round"
  # 끝난 회차는 다시 돌리지 않는다(Q-05). 결과를 덮어쓰지 않게 막는다.
  [ ! -f "$dir/result.json" ] || { log "이미 결과가 있는 회차: $dir"; return 1; }
  mkdir -p "$dir"
  CURRENT_ROUND_DIR="$dir"
  rm -f "$TMP/stop"
  started="$(now_utc)"
  log "회차 시작 $strategy-vu$vus-r$round"
  jq -n --arg env "$ENV_NAME" --arg strategy "$strategy" --argjson vus "$vus" --argjson round "$round" \
    --arg measure "$MEASURE" --arg warmup "$WARMUP" --argjson warmup_vus "$WARMUP_VUS" --arg started "$started" \
    --arg base_url "$BASE_URL" --arg k6 "$(k6 version | head -1)" --arg git "$(git -C "$ROOT" rev-parse --short HEAD)" \
    --argjson a "$ID_A" --argjson b "$ID_B" \
    '{env: $env, strategy: $strategy, vus: $vus, round: $round, executor: "constant-vus", measure: $measure,
      request_timeout: "30s", warmup: $warmup, warmup_vus: $warmup_vus, amount: "1000.00",
      direction: "(__VU + __ITER) % 2", account_ids: [$a, $b], base_url: $base_url, k6: $k6, git: $git,
      started_at: $started}' > "$dir/conditions.json"

  fail_round() {
    local status
    status="$(cat "$TMP/stop" 2> /dev/null || echo "명령 실패")"
    [ "$measured" = yes ] || status="측정 미실행($status)"
    write_result "$dir" "$status" "$1" "$release"
    log "회차 중단 $strategy-vu$vus-r$round: $status — $1"
    return 1
  }

  logline="$(apply_strategy "$strategy")" || { fail_round "전략 적용"; return 1; }
  echo "$logline" > "$dir/strategy.txt"
  grep -q "이체 락 전략: $strategy" <<< "$logline" || { fail_round "기동 로그 전략 불일치"; return 1; }
  wait_unapplied_zero || { fail_round "워밍업 전 미반영 입금"; return 1; }
  psql_json "VACUUM (ANALYZE) accounts;" > /dev/null || { fail_round "VACUUM"; return 1; }

  mkdir -p "$dir/warmup"
  run_k6 "$WARMUP_VUS" "$WARMUP" "$dir/warmup" || { fail_round "워밍업 k6"; return 1; }
  wait_unapplied_zero || { fail_round "기준값 전 미반영 입금"; return 1; }
  sleep 10
  snapshot > "$dir/before.json" || { fail_round "기준값"; return 1; }
  lock_wait_metrics > "$dir/lock-wait-before.json" || { fail_round "기준 락 대기"; return 1; }

  if [ "$ENV_NAME" = prod ]; then mem_sampler_start || { fail_round "메모리 표본 시작"; return 1; }; fi
  log "측정 $MEASURE VU $vus"
  measuring=yes
  run_k6 "$vus" "$MEASURE" "$dir" || { fail_round "측정 k6"; return 1; }
  measured=yes
  measure_end="$(now_utc)"
  sleep 10
  if [ "$ENV_NAME" = prod ]; then
    mem="$(mem_sampler_stop)" || { fail_round "메모리 표본 종료"; return 1; }
    echo "$mem" > "$dir/mem-min-mib.txt"
  fi
  wait_unapplied_zero || { fail_round "종료값 전 미반영 입금"; return 1; }
  # 임시 파일에 쓴 뒤 옮긴다. 기록 도중 중단돼도 빈 after.json이 남지 않게(on_interrupt가 완료로 오인).
  snapshot > "$TMP/after.json" || { fail_round "종료값"; return 1; }
  mv "$TMP/after.json" "$dir/after.json"
  lock_wait_metrics > "$dir/lock-wait-after.json" || { fail_round "종료 락 대기"; return 1; }
  backup_postgres_log "$started" "$dir" || { fail_round "postgres 로그 백업"; return 1; }

  if [ "$strategy" = unsorted-for-update ]; then
    if [ "$ENV_NAME" = local ]; then
      loki_deadlock_lines "$(date -j -u -f %Y-%m-%dT%H:%M:%SZ "$started" +%s)" "$(date +%s)" \
        > "$dir/loki.json" || log "Loki 조회 실패"
    fi
    if [ "${SKIP_RELEASE_WAIT:-no}" != yes ]; then
      release="$(wait_release "$measure_end" "$dir")" || {
        write_result "$dir" "완료" "" "$release"
        log "해제 대기 중 중단: $release"
        return 1
      }
    fi
  fi
  write_result "$dir" "완료" "" "$release"
  log "회차 끝 $strategy-vu$vus-r$round (해제: $release)"
}

# 실험 전략 줄을 지우고 api를 재생성한 뒤 기동 로그 전략과 이체 1건을 확인한다(운영 설정 복귀).
prod_restore() {
  local logline
  CURRENT_ROUND_DIR=""
  rm -f "$TMP/stop"
  host "pkill -f /tmp/deadlock-mem.sh || true" > /dev/null || true
  logline="$(apply_strategy current)" || { log "설정 복귀 실패: $(cat "$TMP/stop")"; return 1; }
  {
    echo "## 설정 복귀 $(now_utc)"
    echo
    echo '```text'
    echo "$logline"
    echo "이체 1건: $(one_transfer)"
    echo "중지 스케줄: $(aws scheduler get-schedule --region "$REGION" --name jbank-dev-app-stop --query State --output text 2>&1)"
    echo '```'
    echo
  } >> "$RESULTS/prod/restore.md"
  log "설정 복귀 기록: $RESULTS/prod/restore.md"
}

case "$MODE" in
  local)
    login_and_resolve_accounts
    run_round "$2" "$3" "$4"
    ;;
  prod-all)
    FROM=""
    [ "${2:-}" = --from ] && FROM="${3:?--from <조건>-vu<VU>}"
    if [ -n "$FROM" ] && ! printf '%s\n' "${PROD_ORDER[@]}" | grep -qx "$FROM"; then
      log "--from 값이 회차 목록에 없다: $FROM (목록: ${PROD_ORDER[*]})"
      exit 1
    fi
    login_and_resolve_accounts
    started=no
    [ -z "$FROM" ] && started=yes
    last=$((${#PROD_ORDER[@]} - 1))
    for i in "${!PROD_ORDER[@]}"; do
      key="${PROD_ORDER[$i]}"
      if [ "$key" = "$FROM" ]; then
        started=yes
        # ① 해제 대기 중에 멈췄다면 재개할 때 해제 확인부터 한다.
        prev="${PROD_ORDER[$((i > 0 ? i - 1 : 0))]}"
        if [ "$i" -gt 0 ] && [ "${prev%-vu*}" = unsorted-for-update ] &&
          jq -e '.release | startswith("해제 미확인")' "$RESULTS/prod/$prev-r1/result.json" > /dev/null 2>&1; then
          release="$(wait_release "$(now_utc)" "$RESULTS/prod/$prev-r1")" || { log "해제 확인 실패: $release"; exit 1; }
          jq --arg r "$release" '.release = $r' "$RESULTS/prod/$prev-r1/result.json" > "$TMP/r.json"
          mv "$TMP/r.json" "$RESULTS/prod/$prev-r1/result.json"
        fi
      fi
      [ "$started" = yes ] || continue
      if [ -f "$RESULTS/prod/$key-r1/result.json" ]; then
        log "이미 결과가 있는 회차는 건너뛴다: $key"
        continue
      fi
      # 마지막 회차 뒤에는 해제를 기다리지 않고 바로 설정 복귀로 간다.
      SKIP_RELEASE_WAIT=no
      [ "$i" -eq "$last" ] && SKIP_RELEASE_WAIT=yes
      if ! run_round "${key%-vu*}" "${key##*-vu}" 1; then
        log "중단. 복구 후 다음 회차부터: perf/run-deadlock.sh prod-all --from <다음 회차>"
        exit 1
      fi
    done
    prod_restore
    ;;
  prod-check)
    # 실험일 전 읽기 전용 점검: SSM 실행, 원격 스크립트가 exec -T 뒤 줄까지 실행되는지, 컨테이너 안 wget, 스케줄 조회 권한.
    host "echo \"SSM: ok\"
echo \"컨테이너 변수: \$(\$DC exec -T api printenv JBANK_TRANSFER_LOCK_STRATEGY || echo 없음)\"
echo \"exec 다음 줄: ok\"
echo \"Alertmanager: \$(\$DC exec -T alertmanager wget -qO- http://localhost:9093/-/ready || echo 실패)\"
echo \"Prometheus: \$(\$DC exec -T prometheus wget -qO- http://localhost:9090/-/ready || echo 실패)\"
echo \"마지막 줄: ok\"" || { echo "SSM 실패: $(cat "$TMP/stop")"; exit 1; }
    echo "중지 스케줄: $(aws scheduler get-schedule --region "$REGION" --name jbank-dev-app-stop --query State --output text 2>&1)"
    ;;
  prod-restore-check)
    {
      echo "## 복귀 확인 $(now_utc)"
      echo
      echo '```text'
      host "echo \".env 줄 수: \$(grep -c '^JBANK_TRANSFER_LOCK_STRATEGY=' .env || true)\"
echo \"컨테이너 변수: \$(\$DC exec -T api printenv JBANK_TRANSFER_LOCK_STRATEGY || echo 없음)\"
docker logs \$(\$DC ps -q api) 2>&1 | grep -o '이체 락 전략: [a-z-]*' | tail -1" || echo "조회 실패: $(cat "$TMP/stop")"
      echo "이체 1건: $(one_transfer)"
      echo "중지 스케줄: $(aws scheduler get-schedule --region "$REGION" --name jbank-dev-app-stop --query State --output text 2>&1)"
      echo '```'
      echo
    } >> "$RESULTS/prod/restore.md"
    log "복귀 확인 기록: $RESULTS/prod/restore.md"
    ;;
esac
