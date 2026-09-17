#!/bin/bash
# 최초 부팅 1회. docker + compose 플러그인 설치, 저장소 clone. 이후 배포는
# backend-cd.yml이 SSM으로 `git pull && docker compose up -d`를 보낸다.
set -euxo pipefail

dnf install -y docker git cronie
systemctl enable --now docker
usermod -aG docker ec2-user

# AL2023 저장소에 compose 플러그인이 없어 GitHub 릴리스에서 받는다.
COMPOSE_VERSION=v2.29.7
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/$${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

git clone "https://github.com/${github_repository}.git" /opt/jbank
chown -R ec2-user:ec2-user /opt/jbank

# 배치 잡 스케줄. k8s CronJob(v1.0.0 helm values의 batchJobs)과 같은 잡·같은 시각을
# KST 기준으로 옮겼다. 실행 자체는 infra/compose/run-batch.sh가 한다.
mkdir -p /var/log/jbank && chown ec2-user:ec2-user /var/log/jbank
cat > /etc/cron.d/jbank <<'CRON'
SHELL=/bin/bash
CRON_TZ=Asia/Seoul
0 1 * * * ec2-user /opt/jbank/infra/compose/run-batch.sh interestMaturityJob --run-date >> /var/log/jbank/batch.log 2>&1
0 2 * * * ec2-user /opt/jbank/infra/compose/run-batch.sh ctrDetectionJob --run-date >> /var/log/jbank/batch.log 2>&1
0 3 * * * ec2-user /opt/jbank/infra/compose/run-batch.sh ledgerReconciliationJob >> /var/log/jbank/batch.log 2>&1
0 4 * * * ec2-user /opt/jbank/infra/compose/run-batch.sh fdsDetectionJob --run-date >> /var/log/jbank/batch.log 2>&1
CRON
chmod 644 /etc/cron.d/jbank
systemctl enable --now crond

# 비밀값(.env)은 여기서 만들지 않는다 — SSM 세션으로 접속해 infra/compose/.env.example을
# 참고해 직접 채운 뒤 compose를 올린다. 그 전까지 api는 기동하지 않는다.
