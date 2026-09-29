#!/usr/bin/env bash
# .github/scripts/deploy-ec2.sh 모의 검사. aws를 가짜로 바꿔 인스턴스 상태별 동작을 본다.
# 실행: bash infra/compose/tests/cd-skip-test.sh
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)/.github/scripts/deploy-ec2.sh"
PASS=0
FAIL=0

setup() { # setup <인스턴스 상태|FAIL> <SSM PingStatus> <명령 결과 Status>
  T=$(mktemp -d)
  mkdir -p "$T/bin"
  export CALLS="$T/calls" STATE_NAME=$1 PING=$2 RESULT=$3
  : > "$CALLS"
  cat > "$T/bin/aws" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$CALLS"
case "$1 $2" in
  "ec2 describe-instances") [ "$STATE_NAME" = FAIL ] && { echo "AccessDenied" >&2; exit 254; }; echo "$STATE_NAME" ;;
  "ssm describe-instance-information") echo "$PING" ;;
  "ssm send-command") echo cmd-1 ;;
  "ssm get-command-invocation") echo "$RESULT" ;;
  *) exit 2 ;;
esac
EOF
  chmod +x "$T/bin/aws"
  PATH="$T/bin:$PATH" INSTANCE_ID=i-test SHA=abc123 SSM_WAIT=0 POLL_INTERVAL=0 POLL_LIMIT=3 \
    bash "$SCRIPT" > "$T/out" 2>&1
  CODE=$?
}

check() {
  local name=$1; shift
  if "$@"; then PASS=$((PASS + 1)); echo "ok   $name"; else FAIL=$((FAIL + 1)); echo "FAIL $name"; sed 's/^/     | /' "$T/out"; fi
}
not() { ! "$@"; }

setup stopped None Success
check "stopped → 성공 종료" [ "$CODE" = 0 ]
check "stopped → 경고 출력" grep -q "::warning::" "$T/out"
check "stopped → 배포 명령 안 보냄" not grep -q "send-command" "$CALLS"

setup stopping None Success
check "stopping → 건너뜀(성공)" [ "$CODE" = 0 ]

setup running Online Success
check "running+Online → 배포 성공" [ "$CODE" = 0 ]
check "running+Online → 배포 명령 보냄" grep -q "send-command" "$CALLS"
cmd=$(grep "send-command" "$CALLS")
check "잠금 디렉터리 생성이 flock보다 먼저" python3 -c 'import sys; c=sys.argv[1]; a=c.find("install -d -o ec2-user"); b=c.find("flock /var/lib/jbank/deploy.lock"); sys.exit(0 if 0 <= a < b else 1)' "$cmd"
check "배포가 잠금 안에서 sha로" grep -q "flock /var/lib/jbank/deploy.lock bash -c 'cd /opt/jbank && git fetch -q origin main && git reset -q --hard origin/main && IMAGE_TAG=abc123 bash infra/compose/deploy.sh'" <<< "$cmd"
check "호스트 설치가 배포 뒤" python3 -c 'import sys; c=sys.argv[1]; sys.exit(0 if c.find("deploy.sh") < c.find("install-host.sh") else 1)' "$cmd"

setup running ConnectionLost Success
check "running+SSM 미등록 → 실패" [ "$CODE" != 0 ]
check "running+SSM 미등록 → 배포 명령 안 보냄" not grep -q "send-command" "$CALLS"

setup FAIL Online Success
check "상태 조회 실패 → 실패(건너뛰지 않음)" [ "$CODE" != 0 ]
check "상태 조회 실패 → 경고로 바꾸지 않음" not grep -q "::warning::" "$T/out"

setup running Online Failed
check "배포 실패 → 실패" [ "$CODE" != 0 ]

setup running Online InProgress
check "폴링 한도까지 미완료 → 실패" [ "$CODE" != 0 ]

echo "cd-skip-test: $PASS 통과, $FAIL 실패"
[ "$FAIL" = 0 ]
