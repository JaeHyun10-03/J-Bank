#!/usr/bin/env bash
# EC2 부하 테스트 진입점. 실제 동작은 run-ec2.py(로컬 오케스트레이터)에 있다.
#   perf/run-ec2.sh up|setup|prepare|down
#   perf/run-ec2.sh s1|s2|s2f|s3 <회차>
#   perf/run-ec2.sh s5 <회차> --rate <요청률>
#   perf/run-ec2.sh s1 --dry-run | s2f --dry-run
#   결과 루트·원격 브랜치: PERF_RESULTS·PERF_GIT_REF 환경변수(run-ec2.py 머리말)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-}" in
  s1|s2|s2f|s3|s5)
    if [ "${2:-}" = "--dry-run" ]; then exec python3 "$HERE/run-ec2.py" dry-run "$1"; fi
    scen="$1"; shift
    exec python3 "$HERE/run-ec2.py" run "$scen" "$@" ;;
  *)
    exec python3 "$HERE/run-ec2.py" "$@" ;;
esac
