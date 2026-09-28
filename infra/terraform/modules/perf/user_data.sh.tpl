#!/bin/bash
# 최초 부팅 1회. docker + compose 설치, 저장소 clone. 운영 user_data와 달리 배치 cron을 등록하지
# 않는다 — 측정 중 배치가 스스로 돌면 측정이 오염된다. 배치는 perf/run-ec2.sh가 직접 실행한다.
set -euxo pipefail

dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

COMPOSE_VERSION=v2.29.7
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/$${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

%{ if install_k6 ~}
dnf install -y https://dl.k6.io/rpm/repo.rpm
dnf install -y k6
%{ endif ~}

git clone --branch "${git_ref}" "https://github.com/${github_repository}.git" /opt/jbank
chown -R ec2-user:ec2-user /opt/jbank
mkdir -p /opt/perf-out && chown ec2-user:ec2-user /opt/perf-out
touch /var/lib/cloud/instance/jbank-perf-ready
