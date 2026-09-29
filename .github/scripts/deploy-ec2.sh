#!/usr/bin/env bash
# backend-cd의 배포 단계. 사용법: INSTANCE_ID=<id> SHA=<git sha> deploy-ec2.sh
# 운영 인스턴스는 평일 09~18시에만 켜진다(docs/adr/0011). 꺼져 있으면 배포를 건너뛰고 성공으로
# 끝낸다 — 다음 부팅 때 호스트의 부팅 작업이 :latest(이 이미지)를 반영한다. 켜져 있는데 SSM이
# 붙지 않거나, 상태 조회·배포가 실패하면 실패로 끝낸다.
set -euo pipefail

SSM_WAIT="${SSM_WAIT:-180}"      # 부팅 직후 SSM 에이전트 등록 대기(초)
POLL_LIMIT="${POLL_LIMIT:-120}"  # 결과 폴링 횟수 × POLL_INTERVAL = 10분(부팅 작업이 배포 잠금을 쥐고 있을 수 있음)
POLL_INTERVAL="${POLL_INTERVAL:-5}"

state=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].State.Name' --output text)
if [ "$state" != "running" ]; then
  echo "::warning::인스턴스가 ${state} 상태라 배포를 건너뜀 — 다음 부팅 때 :latest(${SHA})로 반영된다"
  exit 0
fi

waited=0
until [ "$(aws ssm describe-instance-information --filters "Key=InstanceIds,Values=$INSTANCE_ID" \
          --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || true)" = "Online" ]; do
  if [ "$waited" -ge "$SSM_WAIT" ]; then
    echo "::error::인스턴스는 running인데 ${SSM_WAIT}초 동안 SSM이 Online이 아님"
    exit 1
  fi
  sleep 10
  waited=$((waited + 10))
done

# 인스턴스에는 SSH 포트가 없다. SSM Run Command(root)로 보낸다. /opt/jbank는 ec2-user 소유라 git은
# ec2-user로 내려서 실행한다(root로는 'dubious ownership'). 저장소 갱신과 배포는 부팅 작업과 같은
# 잠금 안에서 하고(잠금 디렉터리는 기존 인스턴스에 없을 수 있어 먼저 만든다), 그 뒤 호스트 설정을
# 다시 설치한다. RunShellScript는 명령이 실패해도 다음 줄로 가므로 한 줄에 &&로 잇는다.
cmd="install -d -o ec2-user -g ec2-user /var/lib/jbank && runuser -u ec2-user -- flock /var/lib/jbank/deploy.lock bash -c 'cd /opt/jbank && git fetch -q origin main && git reset -q --hard origin/main && IMAGE_TAG=${SHA} bash infra/compose/deploy.sh' && bash /opt/jbank/infra/compose/host/install-host.sh"
params=$(python3 -c 'import json,sys; print(json.dumps({"commands": [sys.argv[1]]}))' "$cmd")

command_id=$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
  --comment "deploy ${SHA}" --parameters "$params" --query Command.CommandId --output text)

status=Pending
for _ in $(seq 1 "$POLL_LIMIT"); do
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "$status" in
    Success|Failed|Cancelled|TimedOut) break ;;
  esac
  sleep "$POLL_INTERVAL"
done

aws ssm get-command-invocation --command-id "$command_id" --instance-id "$INSTANCE_ID" \
  --query '[Status,StandardOutputContent,StandardErrorContent]' --output text || true
[ "$status" = "Success" ]
