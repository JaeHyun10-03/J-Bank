#!/bin/bash
# 최초 부팅 1회. docker + compose 플러그인 설치, 저장소 clone, 부팅 작업 설치. 이후 배포는
# backend-cd.yml이 SSM으로 보낸다.
set -euxo pipefail

dnf install -y docker git
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

# 부팅 작업(최신 이미지 반영·밀린 배치)을 systemd로 설치한다. 운영 시간에만 켜지므로 새벽 cron은
# 두지 않는다(docs/adr/0011). .env를 채우기 전 첫 재부팅에서는 api가 뜨지 않아 부팅 작업이 실패로 남는다.
bash /opt/jbank/infra/compose/host/install-host.sh

# 비밀값(.env)은 여기서 만들지 않는다 — SSM 세션으로 접속해 infra/compose/.env.example을
# 참고해 직접 채운 뒤 compose를 올린다. 그 전까지 api는 기동하지 않는다.
