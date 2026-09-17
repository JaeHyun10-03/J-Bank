#!/usr/bin/env bash
# EC2에서 배치 잡 하나를 실행한다. k8s CronJob이 하던 일 — API와 같은 이미지를 실행 인자만
# 바꿔 일회성 컨테이너로 띄운다. 스케줄은 /etc/cron.d/jbank(ec2 모듈 user_data)에 있다.
# 사용법: run-batch.sh <jobName> [--run-date]
#   --run-date: runDate=오늘(KST)을 넘겨야 하는 잡(만기이자·CTR·FDS)에 붙인다.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

JOB="$1"
ARGS=("--spring.profiles.active=prod,batch" "--spring.batch.job.name=${JOB}")
if [ "${2:-}" = "--run-date" ]; then
  ARGS+=("runDate=$(TZ=Asia/Seoul date +%F)")
fi

# --no-deps: postgres·redis는 이미 떠 있다. run은 api 서비스의 이미지·env_file·네트워크를
# 그대로 쓰고 command만 바꾼다. 명령행 인자가 환경변수 SPRING_PROFILES_ACTIVE보다 우선한다.
docker compose -f docker-compose.prod.yml run --rm --no-deps api \
  java -jar /app/app.jar "${ARGS[@]}"
