#!/usr/bin/env bash
# boot.sh·sync-latest.sh 모의 검사. docker·git·배포·배치를 가짜로 바꿔 사례별로 돌린다.
# 호스트(AL2023)와 같은 도구(flock, GNU date, python3)가 필요해 컨테이너에서 실행한다:
#   docker run --rm -v "$PWD":/w -w /w amazonlinux:2023 bash -c \
#     'dnf install -y -q util-linux python3 git >/dev/null && bash infra/compose/tests/boot-test.sh'
set -uo pipefail

HOST="$(cd "$(dirname "${BASH_SOURCE[0]}")/../host" && pwd)"
PASS=0
FAIL=0

setup() {
  T=$(mktemp -d)
  export STATE="$T/state" JBANK_REPO="$T/repo" JBANK_LIB="$HOST" JBANK_LOCK="$T/deploy.lock" JBANK_READY_TIMEOUT=30
  mkdir -p "$STATE" "$JBANK_REPO/infra/compose" "$T/bin"
  C="$JBANK_REPO/infra/compose"
  touch "$C/docker-compose.prod.yml"
  echo "IMAGE_TAG=old" > "$C/.env"
  echo healthy > "$STATE/health"
  echo new > "$STATE/label"
  : > "$STATE/runs"
  : > "$STATE/deploys"
  : > "$STATE/sql"

  # 가짜 배포: .env의 IMAGE_TAG를 바꾸고 기록한다(DEPLOY_SLEEP으로 느리게).
  cat > "$C/deploy.sh" <<'EOF'
sleep "${DEPLOY_SLEEP:-0}"
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${IMAGE_TAG}/" "$(dirname "$0")/.env"
echo "$IMAGE_TAG" >> "$STATE/deploys"
EOF
  # 가짜 배치: 호출을 기록하고, fail 파일의 패턴과 맞으면 실패, 성공하면 마지막 완료일을 갱신한다.
  cat > "$C/run-batch.sh" <<'EOF'
echo "$*" >> "$STATE/runs"
if [ -f "$STATE/fail" ] && grep -qxF "$*" "$STATE/fail"; then exit 1; fi
if [ "${2:-}" = "--run-date" ]; then
  last=$(cat "$STATE/last_$1" 2>/dev/null)
  if [ -z "$last" ] || [[ "$3" > "$last" ]]; then echo "$3" > "$STATE/last_$1"; fi
fi
EOF
  cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
args="$*"
case "$args" in
  *" ps --format "*) cat "$STATE/health" ;;
  *"exec -T postgres psql"*)
    q="${@: -1}"
    echo "$q" >> "$STATE/sql"
    if [[ "$q" == *"max(p.parameter_value)"* ]]; then
      job=$(echo "$q" | sed -n "s/.*job_name = '\([A-Za-z]*\)'.*/\1/p")
      cat "$STATE/last_$job" 2>/dev/null
    fi ;;
  "pull -q "*) [ -f "$STATE/pull_fail" ] && exit 1; echo pulled ;;
  "image inspect "*) cat "$STATE/label" ;;
  *) echo "unexpected docker $args" >&2; exit 2 ;;
esac
EOF
  cat > "$T/bin/git" <<'EOF'
#!/usr/bin/env bash
[ -f "$STATE/git_fail" ] && exit 1
exit 0
EOF
  chmod +x "$T/bin/docker" "$T/bin/git"
  export PATH="$T/bin:$PATH"
}

run_boot() { timeout 60 bash "$HOST/boot.sh" > "$T/out" 2>&1; echo $? > "$T/code"; }
tag() { sed -n 's/^IMAGE_TAG=//p' "$C/.env"; }
yesterday() { TZ=Asia/Seoul date -d "yesterday" +%F; }
today() { TZ=Asia/Seoul date +%F; }
ago() { TZ=Asia/Seoul date -d "$1 days ago" +%F; }

not() { ! "$@"; }

check() { # check <설명> <조건 명령...>
  local name=$1; shift
  if "$@"; then PASS=$((PASS + 1)); echo "ok   $name"; else FAIL=$((FAIL + 1)); echo "FAIL $name"; sed 's/^/     | /' "$T/out"; fi
}

# 1. 정상: revision이 다르면 교체, 배치 순서, 따라잡기, 오늘 이자
setup
echo "$(ago 4)" > "$STATE/last_ctrDetectionJob"
echo "$(ago 2)" > "$STATE/last_fdsDetectionJob"
run_boot
check "정상 종료" [ "$(cat "$T/code")" = 0 ]
check "최신 revision으로 교체" [ "$(tag)" = new ]
expected="interestMaturityJob --run-date $(today)
ctrDetectionJob --run-date $(ago 3)
ctrDetectionJob --run-date $(ago 2)
ctrDetectionJob --run-date $(yesterday)
ledgerReconciliationJob
fdsDetectionJob --run-date $(yesterday)"
check "배치 순서·따라잡기 날짜" [ "$(cat "$STATE/runs")" = "$expected" ]
check "중단 기록 정리 SQL(잡·스텝)" grep -q "UPDATE batch_step_execution" "$STATE/sql"
check "정리 SQL이 잡 실행도 바꿈" grep -q "UPDATE batch_job_execution" "$STATE/sql"

# 2. 같은 날 두 번째 부팅: 교체 없음, 이자 건너뜀, 따라잡기 없음, 대사만
: > "$STATE/runs"; : > "$STATE/deploys"
run_boot
check "두 번째 부팅 정상 종료" [ "$(cat "$T/code")" = 0 ]
check "두 번째 부팅 교체 없음" [ ! -s "$STATE/deploys" ]
check "두 번째 부팅은 대사만" [ "$(cat "$STATE/runs")" = "ledgerReconciliationJob" ]
check "이자 건너뜀 기록" grep -q "이미 완료" "$T/out"

# 3. 라벨 없음(변경 전 이미지) → 교체 안 함
setup; echo "" > "$STATE/label"; run_boot
check "라벨 없음 → 교체 안 함" [ "$(tag)" = old ]
check "라벨 없음이어도 배치 실행" grep -q "^ledgerReconciliationJob$" "$STATE/runs"

# 4. 같은 revision → 교체 안 함
setup; echo old > "$STATE/label"; run_boot
check "같은 revision → 교체 안 함" [ ! -s "$STATE/deploys" ]

# 5. pull 실패 → 유지하고 배치 계속
setup; touch "$STATE/pull_fail"; run_boot
check "pull 실패 → 유지" [ "$(tag)" = old ]
check "pull 실패여도 배치 실행·정상 종료" [ "$(cat "$T/code")" = 0 ]

# 6. git 실패 → 계속(교체는 진행)
setup; touch "$STATE/git_fail"; run_boot
check "git 실패 기록" grep -q "저장소 갱신 실패" "$T/out"
check "git 실패여도 교체·배치" [ "$(tag)" = new ]

# 7. api 준비 안 됨 → 배치 건너뛰고 실패
setup; echo starting > "$STATE/health"; JBANK_READY_TIMEOUT=0 run_boot
check "준비 초과 → 실패 종료" [ "$(cat "$T/code")" = 1 ]
check "준비 초과 → 배치·교체 없음" [ ! -s "$STATE/runs" -a ! -s "$STATE/deploys" ]

# 8. 따라잡기 중간 실패 → 그 잡만 멈추고 다음 잡 계속, 실패 종료
setup
echo "$(ago 4)" > "$STATE/last_ctrDetectionJob"
echo "ctrDetectionJob --run-date $(ago 2)" > "$STATE/fail"
run_boot
check "중간 실패 → 실패 종료" [ "$(cat "$T/code")" = 1 ]
check "실패한 날 이후 CTR 미실행" not grep -q "ctrDetectionJob --run-date $(yesterday)" "$STATE/runs"
check "실패해도 대사·FDS 실행" grep -q "^fdsDetectionJob --run-date $(yesterday)$" "$STATE/runs"
: > "$STATE/runs"; rm -f "$STATE/fail"; run_boot
check "다음 부팅에서 실패한 날부터 재시도" [ "$(grep ctrDetectionJob "$STATE/runs" | head -1)" = "ctrDetectionJob --run-date $(ago 2)" ]

# 9. 기록 없음 → 어제 하나
setup; run_boot
check "기록 없음 → 어제만" [ "$(grep ctrDetectionJob "$STATE/runs")" = "ctrDetectionJob --run-date $(yesterday)" ]

# 10. 경합: CD가 잠금을 쥔 채 Y를 올리는 동안 부팅이 비교하려 함 → 최종 Y, 교착 없음
setup; echo X > "$STATE/label"
( flock "$JBANK_LOCK" bash -c "sleep 2; echo Y > '$STATE/label'; IMAGE_TAG=Y bash '$C/deploy.sh'" ) &
sleep 0.5
run_boot; wait
check "CD 먼저 → 부팅은 기다렸다가 Y를 봄(최종 Y)" [ "$(tag)" = Y ]
check "CD 먼저 → 부팅은 교체 안 함" [ "$(cat "$STATE/deploys")" = Y ]

# 11. 경합: 부팅이 X로 교체하는 중에 CD가 Y 배포 → CD가 기다렸다가 Y(최종 Y)
setup; echo X > "$STATE/label"
DEPLOY_SLEEP=2 run_boot &
sleep 0.5
echo Y > "$STATE/label"
timeout 30 flock "$JBANK_LOCK" bash -c "IMAGE_TAG=Y bash '$C/deploy.sh'"; cd_code=$?
wait
check "부팅 먼저 → CD가 기다렸다가 배포(교착 없음)" [ "$cd_code" = 0 ]
check "부팅 먼저 → X 교체 뒤 Y 배포, 최종 Y" [ "$(tag)" = Y -a "$(tr '\n' ' ' < "$STATE/deploys")" = "X Y " ]

echo "boot-test: $PASS 통과, $FAIL 실패"
[ "$FAIL" = 0 ]
