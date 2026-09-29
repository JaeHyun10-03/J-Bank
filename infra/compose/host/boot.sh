#!/usr/bin/env bash
# 인스턴스가 켜질 때 한 번 돈다(jbank-boot.service, ec2-user). 운영 시간에만 켜지므로(docs/adr/0011)
# 꺼진 동안 밀린 일을 여기서 한다.
#   1. api 준비 대기(최대 10분, 넘으면 배치를 건너뛰고 실패)
#   2. 배포 잠금 안에서 최신 이미지 반영(sync-latest.sh) → 교체됐으면 다시 준비 대기
#   3. 중단된 배치 실행 기록 정리(부팅 직후라 실제로 도는 잡은 없다)
#   4. 배치: 만기이자(오늘) → CTR(따라잡기) → 원장 대사 → FDS(따라잡기). 잡끼리는 독립,
#      따라잡기는 실패한 날에서 그 잡만 멈춘다(다음 부팅에서 그 날부터 다시).
set -uo pipefail

REPO="${JBANK_REPO:-/opt/jbank}"
COMPOSE_DIR="$REPO/infra/compose"
LIB="${JBANK_LIB:-/usr/local/lib/jbank}"
LOCK="${JBANK_LOCK:-/var/lib/jbank/deploy.lock}"
READY_TIMEOUT="${JBANK_READY_TIMEOUT:-600}"
COMPOSE=(docker compose -f "$COMPOSE_DIR/docker-compose.prod.yml")

log() { echo "$(date -u +%FT%TZ) boot: $*"; }

wait_api() {
  local waited=0
  until [ "$("${COMPOSE[@]}" ps --format '{{.Health}}' api 2>/dev/null)" = "healthy" ]; do
    if [ "$waited" -ge "$READY_TIMEOUT" ]; then
      return 1
    fi
    sleep 5
    waited=$((waited + 5))
  done
}

sql() { "${COMPOSE[@]}" exec -T postgres psql -U jbank -d jbank -tAq -c "$1"; }

last_completed() {
  sql "SELECT max(p.parameter_value) FROM batch_job_execution_params p
       JOIN batch_job_execution e ON e.job_execution_id = p.job_execution_id
       JOIN batch_job_instance i ON i.job_instance_id = e.job_instance_id
       WHERE i.job_name = '$1' AND e.status = 'COMPLETED' AND p.parameter_name = 'runDate'"
}

run_job() {
  log "실행 $*"
  if bash "$COMPOSE_DIR/run-batch.sh" "$@" >/dev/null 2>&1; then
    log "완료 $*"
  else
    log "실패 $*"
    return 1
  fi
}

catch_up() {
  local job=$1 last day
  last=$(last_completed "$job")
  log "$job 마지막 완료 기준일: ${last:-없음}"
  for day in $(python3 "$LIB/batch_dates.py" "${last:--}"); do
    run_job "$job" --run-date "$day" || { log "$job: $day 실패로 이후 날짜는 다음 부팅에"; return 1; }
  done
}

log "시작"
if ! wait_api; then
  log "api가 ${READY_TIMEOUT}초 안에 준비되지 않음 — 배치 건너뜀"
  exit 1
fi

flock "$LOCK" bash "$LIB/sync-latest.sh"
if ! wait_api; then
  log "이미지 반영 후 api가 준비되지 않음 — 배치 건너뜀"
  exit 1
fi

sql "UPDATE batch_step_execution SET status = 'FAILED', exit_code = 'FAILED',
       exit_message = 'abandoned at boot', end_time = now()
     WHERE status IN ('STARTING', 'STARTED', 'STOPPING');
     UPDATE batch_job_execution SET status = 'FAILED', exit_code = 'FAILED',
       exit_message = 'abandoned at boot', end_time = now()
     WHERE status IN ('STARTING', 'STARTED', 'STOPPING');" \
  && log "중단 기록 정리 완료" || log "중단 기록 정리 실패"

failed=0
today=$(TZ=Asia/Seoul date +%F)
if [ "$(last_completed interestMaturityJob)" = "$today" ]; then
  log "interestMaturityJob: 오늘($today) 이미 완료 — 건너뜀"
else
  run_job interestMaturityJob --run-date "$today" || failed=1
fi
catch_up ctrDetectionJob || failed=1
run_job ledgerReconciliationJob || failed=1
catch_up fdsDetectionJob || failed=1

log "끝(실패 있음: $failed)"
exit "$failed"
