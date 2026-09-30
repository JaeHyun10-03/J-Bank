#!/usr/bin/env bash
# perf 대상 EC2에서 실행하는 단계들. 로컬의 perf/run-ec2.py가 SSM으로 호출한다(root).
# 사용법: target.sh <명령> [인자...]   결과 파일은 /opt/perf-out/ 아래에 남긴다.
set -euo pipefail

REPO=/opt/jbank
cd "$REPO/infra/compose/perf"
DC=(docker compose -f docker-compose.target.yml)
OUT=/opt/perf-out
PGDATA_DIR=/opt/perf-pgdata
PGDATA_BASE=/opt/perf-pgdata.base

psql_q() { "${DC[@]}" exec -T postgres psql -U jbank -d jbank -v ON_ERROR_STOP=1 "$@"; }
psql_val() { psql_q -tA -c "$1"; }
# 입금 비동기 반영(ADR 0012) 이후 이미지에만 있는 테이블. 이전 이미지로 기준선을 다시 잴 때도 스크립트가 돌게 한다.
has_pending() { [ "$(psql_val "SELECT to_regclass('pending_credits') IS NOT NULL")" = t ]; }
disk() { echo "disk $(date -Is) $1: $(df -B1 --output=avail / | tail -1) bytes free"; }

wait_ready() {
  for _ in $(seq 1 60); do
    # readiness는 호스트에 매핑하지 않은 관리 포트(9095)에 있어 컨테이너 안에서 조회한다.
    if "${DC[@]}" exec -T api wget -qO- http://localhost:9095/actuator/health/readiness >/dev/null 2>&1; then
      echo "readiness UP $(date -Is)"; return 0
    fi
    sleep 5
  done
  echo "readiness 대기 시간 초과" >&2; return 1
}

cmd="$1"; shift
case "$cmd" in
  setup)
    # setup build: GHCR 이미지 대신 체크아웃한 저장소로 api 이미지를 여기서 빌드한다(main에 없는 브랜치 코드
    # 측정용 — 브랜치 이미지를 GHCR에 올리면 운영이 받는 :latest를 덮을 수 있다). 태그는 perf-<커밋>.
    if [ "${1:-}" = build ]; then
      tag="perf-$(git -c safe.directory='*' -C "$REPO" rev-parse --short HEAD)"
      docker build -q -t "ghcr.io/jaehyun10-03/jbank-api:$tag" "$REPO/apps/jbank-api"
      echo "IMAGE_TAG=$tag" > /tmp/perf-image-tag
    fi
    # 비밀값은 이 인스턴스 안에서만 만든다(운영 값과 무관, 저장소·결과물로 나가지 않는다).
    if [ ! -f .env ]; then
      umask 077
      {
        echo "PERF_HOST=$(hostname -I | awk '{print $1}')"
        if [ -f /tmp/perf-image-tag ]; then cat /tmp/perf-image-tag; else grep '^IMAGE_TAG=' target.env.example; fi
        echo "DB_PASSWORD=$(openssl rand -hex 24)"
        echo "PII_ENCRYPTION_KEY=$(openssl rand -base64 32)"
        echo "RESIDENT_REG_NO_HASH_KEY=$(openssl rand -base64 32)"
        echo "JWT_SECRET=$(openssl rand -base64 48)"
        echo "METRICS_PASSWORD=$(openssl rand -hex 16)"
      } > .env
    fi
    mkdir -p "$PGDATA_DIR" "$OUT/env"
    "${DC[@]}" --env-file .env up -d --quiet-pull
    # Caddyfile은 바인드 마운트라 내용이 바뀌어도 컨테이너가 다시 만들어지지 않는다. 동기화 후 항상 다시 읽힌다.
    "${DC[@]}" restart caddy
    wait_ready
    ;;

  environment)
    # 실행 중 이미지 digest·구성 지문(OPS-01).
    {
      echo "## 대상 환경 $(date -Is)"
      echo "- 저장소 커밋: $(git -c safe.directory='*' -C "$REPO" rev-parse HEAD)"
      echo "- compose 파일 sha256: $(sha256sum docker-compose.target.yml | cut -d' ' -f1)"
      echo "- 인스턴스 유형: $(curl -s -H "X-aws-ec2-metadata-token: $(curl -s -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" http://169.254.169.254/latest/meta-data/instance-type)"
      for c in $("${DC[@]}" ps -q); do
        docker inspect -f '- {{.Name}}: {{.Config.Image}} @ {{.Image}}' "$c"
      done
      for img in $("${DC[@]}" config --images); do
        echo "- digest $img: $(docker image inspect -f '{{join .RepoDigests ","}}' "$img")"
      done
    } | tee "$OUT/env/environment-target.md"
    ;;

  seed)
    # 준비 도중 실패해 prepare를 다시 돌릴 때를 위해, 이미 적재됐으면 건너뛴다(seed-10m.sql은 재실행을 거부한다).
    if [ "$(psql_val 'SELECT count(*) FROM transactions')" -ge 10000000 ]; then
      echo "이미 적재됨 — 시드 건너뜀 $(date -Is)" | tee -a "$OUT/env/seed.log"; exit 0
    fi
    disk "시드 전" | tee "$OUT/env/disk.log"
    { time psql_q < "$REPO/perf/sql/seed-10m.sql"; } > "$OUT/env/seed.log" 2>&1
    psql_q -c "SELECT (SELECT count(*) FROM customers) AS customers,
                      (SELECT count(*) FROM accounts) AS accounts,
                      (SELECT count(*) FROM transactions) AS transactions;" | tee -a "$OUT/env/seed.log"
    disk "시드 후" | tee -a "$OUT/env/disk.log"
    ;;

  baseline)
    # 준비(가입·입금) 이후의 기준 대사. 대사 잡과 같은 조건의 SQL로 계산해 표로 남긴다
    # (잡은 불일치를 WARN 로그로만 남겨 목록을 얻을 수 없다). 이 표는 복사본에 함께 들어간다.
    psql_q <<'SQL' | tee "$OUT/env/baseline.log"
DROP TABLE IF EXISTS perf_baseline_account, perf_baseline_global;
CREATE TABLE perf_baseline_account AS
SELECT a.account_id, a.current_balance_cache AS cache, coalesce(l.s, 0) AS ledger
FROM accounts a
LEFT JOIN (SELECT account_id,
                  sum(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) AS s
           FROM ledger_entries GROUP BY account_id) l USING (account_id)
WHERE coalesce(l.s, 0) <> a.current_balance_cache;
CREATE TABLE perf_baseline_global AS
SELECT coalesce(sum(amount) FILTER (WHERE entry_type = 'DEBIT'), 0) AS debit,
       coalesce(sum(amount) FILTER (WHERE entry_type = 'CREDIT'), 0) AS credit
FROM ledger_entries;
SELECT count(*) AS baseline_mismatch_accounts FROM perf_baseline_account;
SELECT * FROM perf_baseline_global;
SELECT pg_size_pretty(pg_database_size('jbank')) AS db_size, pg_database_size('jbank') AS db_bytes;
SQL
    ;;

  snapshot)
    # 준비 순서의 마지막 단계: postgres를 멈추고 데이터 디렉터리 복사본을 만든다(Q-05).
    disk "복사본 전" | tee -a "$OUT/env/disk.log"
    du -sb "$PGDATA_DIR" | tee -a "$OUT/env/disk.log"
    "${DC[@]}" stop
    rm -rf "$PGDATA_BASE"
    cp -a "$PGDATA_DIR" "$PGDATA_BASE"
    disk "복사본 후" | tee -a "$OUT/env/disk.log"
    "${DC[@]}" --env-file .env up -d
    wait_ready
    ;;

  restore)
    # 회차 시작 조건: 복사본 복원 → OS 페이지 캐시 비움 → 스택 재기동(redis 포함 새 컨테이너) → readiness.
    "${DC[@]}" down -v
    rm -rf "$PGDATA_DIR"
    cp -a "$PGDATA_BASE" "$PGDATA_DIR"
    sync; echo 3 > /proc/sys/vm/drop_caches
    "${DC[@]}" --env-file .env up -d
    wait_ready
    disk "복원 후"
    ;;

  stop-api)
    # 측정이 끝나면 정합성 스냅샷 전에 api를 멈춘다. (1) 대기열 이체가 더 커밋되지 않아 스냅샷과 실패 키 대조가
    # 같은 상태를 본다. (2) 무너진 뒤 api가 약 1GB까지 불어난 상태에서 대사 잡 JVM이 더 뜨면 2GB 인스턴스가
    # 스래싱으로 멈춘다(s1-r1 사고). 정합성·대사는 DB만 본다.
    "${DC[@]}" stop api metrics-proxy caddy
    free -m
    ;;

  clean)
    # 회차 시작 전에 이전 실행의 결과 디렉터리를 비운다.
    for run in "$@"; do rm -rf "${OUT:?}/$run"; done
    ;;

  boundary)
    # 측정 경계: 예열이 끝난 뒤 ID 최댓값과 핫 계좌 잔액(REQ-10).
    run="$1"; hot_id="$2"
    mkdir -p "$OUT/$run"
    # 지표 프록시를 재시작해 새 쿠키로 측정을 시작한다(측정 구간 동안 만료 전 쿠키 유지).
    # /metrics가 200을 돌려주지 않으면 측정을 시작하지 않는다.
    "${DC[@]}" restart metrics-proxy >/dev/null
    ok=0
    for _ in $(seq 1 30); do
      if [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:9091/metrics)" = 200 ]; then ok=1; break; fi
      sleep 1
    done
    [ "$ok" = 1 ] || { echo "metrics-proxy 수집 확인 실패 — 이 회차는 시작하지 않는다" >&2; exit 3; }
    echo "metrics-proxy 재로그인·수집 확인 $(date -Is)"
    # 예열 이체의 입금이 경계 뒤에 반영되면 경계 이후 원장 식이 어긋난다. 미반영이 0이 될 때까지 기다린다(최대 60초).
    pending=0
    if has_pending; then
      for _ in $(seq 1 60); do
        pending=$(psql_val "SELECT count(*) FROM pending_credits WHERE applied_at IS NULL")
        [ "$pending" = 0 ] && break
        sleep 1
      done
    fi
    {
      echo "B_PENDING=$pending"
      echo "B_TX=$(psql_val 'SELECT coalesce(max(transaction_id), 0) FROM transactions')"
      echo "B_LE=$(psql_val 'SELECT coalesce(max(entry_id), 0) FROM ledger_entries')"
      echo "HOT_ID=$hot_id"
      echo "HOT_BAL=$(psql_val "SELECT current_balance_cache FROM accounts WHERE account_id = $hot_id")"
      echo "B_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } | tee "$OUT/$run/boundary.env"
    ;;

  sampler-start)
    run="$1"; mkdir -p "$OUT/$run"
    nohup bash -c "while true; do
      $(printf '%q ' "${DC[@]}") exec -T postgres psql -U jbank -d jbank -tA -F, -c \"SELECT now(), coalesce(wait_event_type,'-'), coalesce(wait_event,'-'), coalesce(state,'-'), count(*) FROM pg_stat_activity WHERE datname='jbank' GROUP BY 2,3,4 ORDER BY 5 DESC\"
      sleep 10
    done" >> "$OUT/$run/pg_stat_activity.csv" 2>&1 &
    echo $! > "$OUT/$run/sampler.pid"
    ;;

  sampler-stop)
    run="$1"
    kill "$(cat "$OUT/$run/sampler.pid")" 2>/dev/null || true
    rm -f "$OUT/$run/sampler.pid"
    ;;

  batch)
    # 배치 잡은 incrementer가 없어 같은 인자로 재실행되지 않는다. 회차 고유 perfRun 인자를 붙인다.
    # runDate는 측정일(KST). run-batch.sh(운영용)는 쓰지 않는다.
    run="$1"; job="$2"; perf_run="$3"; with_date="${4:-}"
    args=("--spring.profiles.active=prod,batch" "--spring.batch.job.name=$job" "perfRun=$perf_run")
    [ "$with_date" = "--run-date" ] && args+=("runDate=$(TZ=Asia/Seoul date +%F)")
    mkdir -p "$OUT/$run"
    start=$(date +%s.%N)
    set +e
    # 메모리 스래싱으로 대상이 멈추면 배치가 끝나지 않는다(s1-r1 사고). 30분 넘으면 끊고 실패로 기록한다.
    timeout 1800 "${DC[@]}" --env-file .env run --rm --no-deps api java -jar /app/app.jar "${args[@]}" \
      > "$OUT/$run/batch-$perf_run.full.log" 2>&1
    code=$?
    end=$(date +%s.%N)
    set -e
    # 대사 잡은 시드 계좌 약 10만 개의 기준 불일치를 한 줄씩 WARN으로 남긴다. 건수만 남기고 나머지 줄은 보존한다.
    full="$OUT/$run/batch-$perf_run.full.log"
    { echo "계좌 잔액 불일치 WARN 줄 수: $(grep -c '계좌 잔액 불일치' "$full" || true)"
      grep -v '계좌 잔액 불일치' "$full"; } > "$OUT/$run/batch-$perf_run.log"
    rm -f "$full"
    # 배치가 처리한 건수(REQ-09). 배치 로그의 "…완료" 메시지를 그대로 옮긴다.
    msg=$(grep -o '"message":"[^"]*완료[^"]*"' "$OUT/$run/batch-$perf_run.log" | tail -1 | sed 's/^"message":"//; s/"$//')
    echo "$perf_run: ${msg:-(완료 메시지 없음)}" | tee -a "$OUT/$run/batch-counts.txt"
    echo "batch job=$job perfRun=$perf_run exit=$code seconds=$(awk "BEGIN{print $end - $start}") start=$start end=$end" \
      | tee -a "$OUT/$run/batch.txt"
    exit $code
    ;;

  integrity)
    # 경계 이후 행만 대상으로 정합성 비교(REQ-10). k6 성공 건수와의 비교는 run-ec2.py가 한다.
    run="$1"
    # shellcheck disable=SC1090
    source "$OUT/$run/boundary.env"
    psql_q -v b_tx="$B_TX" -v b_le="$B_LE" -v hot_id="$HOT_ID" -v hot_bal="$HOT_BAL" <<'SQL' | tee "$OUT/$run/integrity-target.txt"
\pset format unaligned
\pset fieldsep '='
\pset tuples_only on
SELECT 'new_transfer_completed', count(*) FROM transactions
 WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status = 'COMPLETED';
SELECT 'new_transfer_other_status', count(*) FROM transactions
 WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status <> 'COMPLETED';
SELECT 'new_non_transfer', count(*) FROM transactions
 WHERE transaction_id > :b_tx AND transaction_type <> 'TRANSFER';
SELECT 'new_ledger_rows', count(*) FROM ledger_entries WHERE entry_id > :b_le;
SELECT 'new_ledger_debit', coalesce(sum(amount), 0) FROM ledger_entries WHERE entry_id > :b_le AND entry_type = 'DEBIT';
SELECT 'new_ledger_credit', coalesce(sum(amount), 0) FROM ledger_entries WHERE entry_id > :b_le AND entry_type = 'CREDIT';
SELECT 'new_transfer_amount_sum', coalesce(sum(amount), 0) FROM transactions
 WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status = 'COMPLETED';
SELECT 'hot_completed_amount_sum', coalesce(sum(amount), 0) FROM transactions
 WHERE transaction_id > :b_tx AND transaction_type = 'TRANSFER' AND status = 'COMPLETED' AND to_account_id = :hot_id;
SELECT 'hot_balance_delta', current_balance_cache - :hot_bal FROM accounts WHERE account_id = :hot_id;
WITH cur AS (
  SELECT a.account_id, a.current_balance_cache AS cache, coalesce(l.s, 0) AS ledger
  FROM accounts a
  LEFT JOIN (SELECT account_id, sum(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) AS s
             FROM ledger_entries GROUP BY account_id) l USING (account_id)
)
SELECT 'new_mismatch_accounts', count(*) FROM cur
 WHERE cur.ledger <> cur.cache
   AND NOT EXISTS (SELECT 1 FROM perf_baseline_account b WHERE b.account_id = cur.account_id);
WITH cur AS (
  SELECT a.account_id, a.current_balance_cache AS cache, coalesce(l.s, 0) AS ledger
  FROM accounts a
  LEFT JOIN (SELECT account_id, sum(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END) AS s
             FROM ledger_entries GROUP BY account_id) l USING (account_id)
)
SELECT 'baseline_account_delta_mismatch', count(*) FROM cur JOIN perf_baseline_account b USING (account_id)
 WHERE (cur.cache - b.cache) <> (cur.ledger - b.ledger);
SELECT 'global_debit_delta', (SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE entry_type = 'DEBIT') - debit FROM perf_baseline_global;
SELECT 'global_credit_delta', (SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE entry_type = 'CREDIT') - credit FROM perf_baseline_global;
SELECT 'duplicate_idempotency_keys', count(*) FROM (
  SELECT idempotency_key FROM transactions WHERE transaction_id > :b_tx GROUP BY 1 HAVING count(*) > 1) d;
SQL
    if has_pending; then
      psql_q -v b_tx="$B_TX" -v hot_id="$HOT_ID" < "$REPO/perf/ec2/sql/credit-integrity.sql" \
        | tee -a "$OUT/$run/integrity-target.txt"
    fi
    ;;

  credit-dump)
    # 반영 지연 원자료: 경계 이후 입금 대기의 생성·반영 시각(epoch). run-ec2.py가 단계별 p95를 계산한다.
    run="$1"
    # shellcheck disable=SC1090
    source "$OUT/$run/boundary.env"
    if has_pending; then
      psql_q -tA -F, -c "SELECT transaction_id, account_id, extract(epoch FROM created_at),
        coalesce(extract(epoch FROM applied_at)::text, '') FROM pending_credits
        WHERE transaction_id > $B_TX ORDER BY transaction_id" > "$OUT/$run/pending-credits.csv"
      echo "pending-credits rows=$(wc -l < "$OUT/$run/pending-credits.csv")"
    fi
    ;;

  failed-keys)
    # 실패 이체 멱등키 대조(REQ-10 (4)). run-ec2.py가 부하 발생기에서 받아 $OUT/$run/failed-transfers.txt로 올린다.
    run="$1"; f="$OUT/$run/failed-transfers.txt"
    [ -f "$f" ] || { echo "failed_keys_file=missing" | tee "$OUT/$run/failed-keys-target.txt"; exit 1; }
    {
      echo "CREATE TEMP TABLE perf_failed(idempotency_key text, status int, name text);"
      echo "COPY perf_failed FROM STDIN WITH (DELIMITER ' ');"
      cat "$f"
      echo '\.'
      cat <<'SQL'
\pset format unaligned
\pset fieldsep '='
\pset tuples_only on
SELECT 'failed_keys_total', count(*) FROM perf_failed;
SELECT 'failed_keys_committed', count(*) FROM perf_failed f
  JOIN transactions t ON t.idempotency_key = f.idempotency_key AND t.status = 'COMPLETED';
SELECT 'failed_status_' || f.status || '_total', count(*) FROM perf_failed f GROUP BY f.status ORDER BY f.status;
SELECT 'failed_status_' || f.status || '_committed', count(t.transaction_id) FROM perf_failed f
  LEFT JOIN transactions t ON t.idempotency_key = f.idempotency_key AND t.status = 'COMPLETED'
  GROUP BY f.status ORDER BY f.status;
SQL
    } | psql_q | tee "$OUT/$run/failed-keys-target.txt"
    ;;

  logs)
    # 측정 구간 로그 발췌(REQ-12). since는 ISO 시각.
    run="$1"; since="$2"
    "${DC[@]}" logs --no-log-prefix --since "$since" postgres > "$OUT/$run/postgres-full.log" 2>&1 || true
    "${DC[@]}" logs --no-log-prefix --since "$since" api > "$OUT/$run/api-full.log" 2>&1 || true
    {
      # 전체 줄 수: api를 먼저 멈춘 뒤에도 정지된 컨테이너 로그를 실제로 읽었는지 확인하는 값.
      # 앱 로그 형식(ECS JSON의 log.level)만 센다. compose 에러 메시지 한 줄로 0보다 커지지 않게 한다.
      echo "api_log_lines=$(grep -c '"log.level"' "$OUT/$run/api-full.log" || true)"
      echo "postgres_log_lines=$(wc -l < "$OUT/$run/postgres-full.log")"
      echo "slow_statements=$(grep -c 'duration:' "$OUT/$run/postgres-full.log" || true)"
      echo "lock_wait_lines=$(grep -c 'still waiting for' "$OUT/$run/postgres-full.log" || true)"
      echo "api_error_lines=$(grep -c '"log.level":"ERROR"' "$OUT/$run/api-full.log" || true)"
      echo "api_warn_lines=$(grep -c '"log.level":"WARN"' "$OUT/$run/api-full.log" || true)"
    } | tee "$OUT/$run/log-counts.txt"
    grep -E 'duration:|still waiting for|deadlock|FATAL|ERROR' "$OUT/$run/postgres-full.log" | head -300 > "$OUT/$run/postgres-excerpt.log" || true
    grep -E '"log.level":"(ERROR|WARN)"' "$OUT/$run/api-full.log" | head -200 > "$OUT/$run/api-errors-excerpt.log" || true
    rm -f "$OUT/$run/postgres-full.log" "$OUT/$run/api-full.log"
    disk "회차 종료" | tee -a "$OUT/$run/disk.log"
    ;;

  explain)
    # 부하 없음 배치 구간의 느린 쿼리를 EXPLAIN (ANALYZE, BUFFERS)로 다시 실행한다(REQ-09).
    run="$1"; since="$2"; until="$3"
    "${DC[@]}" logs --no-log-prefix --since "$since" --until "$until" postgres > "$OUT/$run/postgres-batch.log" 2>&1 || true
    python3 "$REPO/perf/ec2/extract_slow_sql.py" "$OUT/$run/postgres-batch.log" > "$OUT/$run/batch-slow.sql"
    : > "$OUT/$run/batch-explain.txt"
    while IFS= read -r stmt; do
      [ -z "$stmt" ] && continue
      { echo "-- $stmt"; psql_q -c "EXPLAIN (ANALYZE, BUFFERS) $stmt"; echo; } >> "$OUT/$run/batch-explain.txt" 2>&1 || true
    done < "$OUT/$run/batch-slow.sql"
    rm -f "$OUT/$run/postgres-batch.log"
    wc -l "$OUT/$run/batch-slow.sql"
    ;;

  *)
    echo "알 수 없는 명령: $cmd" >&2; exit 2 ;;
esac
