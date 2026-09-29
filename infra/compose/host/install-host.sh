#!/usr/bin/env bash
# 호스트 설정 설치(root). backend-cd가 배포 뒤 매번 부르고, 새 인스턴스는 user_data가 부른다.
# 부팅 스크립트를 저장소 밖(/usr/local/lib/jbank)에 두어 git reset이 실행 중인 파일을 바꾸지 않게
# 하고, 임시 파일에 쓴 뒤 mv로 바꿔 실행 중인 bash는 옛 파일을 끝까지 읽는다.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB=/usr/local/lib/jbank

install -d -o ec2-user -g ec2-user /var/lib/jbank /var/log/jbank
install -d "$LIB"

put() { # put <원본> <대상> <모드>
  local tmp
  tmp=$(mktemp "$(dirname "$2")/.install.XXXXXX")
  cp "$1" "$tmp"
  chmod "$3" "$tmp"
  mv -f "$tmp" "$2"
}

put "$SRC/boot.sh" "$LIB/boot.sh" 755
put "$SRC/sync-latest.sh" "$LIB/sync-latest.sh" 755
put "$SRC/batch_dates.py" "$LIB/batch_dates.py" 755
put "$SRC/jbank-boot.service" /etc/systemd/system/jbank-boot.service 644

systemctl daemon-reload
systemctl enable jbank-boot.service >/dev/null 2>&1
# 새벽 cron은 인스턴스가 꺼져 있어 돌지 않는다. 배치는 boot.sh가 켜질 때 한다.
rm -f /etc/cron.d/jbank
echo "install-host: done"
