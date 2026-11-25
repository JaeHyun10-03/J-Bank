# 시작 전 잔액 확인 시나리오 (REQ-01~04)

- 시나리오 실행: 2026-11-25 23:25:56 KST ~ 23:26:05, 변이 실행: 23:26:05 ~ 23:27:18. 명령 `bash scenario.sh <스크립트 경로>`. 이 기록을 만든 시각은 2026-11-25 23:27:35 KST다.
- 환경: GNU bash, version 3.2.57(1)-release (arm64-apple-darwin25), jq-1.7.1-apple, macOS
- 대상 사본: 커밋 전 작업 폴더의 `perf/run-deadlock.sh`(sha256 1447ff994b68f674…). 복귀 확인 비교 대상은 `git show 69e0665:perf/run-deadlock.sh`(두 파일이 다름을 `cmp`로 확인).
- 격리: 사본을 임시 폴더에 두고 `curl`·`aws`·`docker`·`k6`를 가짜로 바꿨다. 주소는 닫힌 포트, 인스턴스 ID와 AWS 자격증명은 가짜다. 실행 전후 저장소 `perf/results`의 `git status --porcelain`이 같았다.
- 결과: 정상 구현 검사 233개, 실패 0개 (종료 코드 0).
- 정적 검사(2026-11-25 23:27:35 KST): `bash -n perf/run-deadlock.sh` 종료 코드 0, `shellcheck -x perf/run-deadlock.sh` 종료 코드 0 경고 0줄(변경 전 69e0665는 종료 코드 0 경고 0줄).
- 요약표 대조(2026-11-25 23:27:35 KST): `deadlock-summary.py prod --check` 종료 코드 0, `local --check` 종료 코드 0.

## 틀린 구현을 잡는지 확인

아래 `mutate.py`로 스크립트를 일부러 틀리게 바꾼 사본을 만들어 같은 시나리오를 돌렸다. 모두 실패를 냈다.

| 변이 | 결과(233개 중 실패) |
| --- | --- |
| first_only | 10 |
| no_check | 135 |
| null_as_zero | 60 |
| resume_each | 9 |
| resume_strict_greater | 13 |
| second_only | 10 |
| start_strict_greater | 3 |
| start_sum_only | 15 |
| string_compare | 30 |
| tonumber_in_login | 34 |
| uses_balance | 135 |

## 변이 생성 스크립트 mutate.py

```python
# 변이: 스크립트를 일부러 틀리게 바꾼 사본을 만든다.
import sys
from pathlib import Path
T = Path(sys.argv[1]); src = Path(sys.argv[2]).read_text(encoding="utf-8")
A = '$mode == "start" and any($c[]; . < 10000000000)'
B = '$mode == "resume" and ($c | add) < 20000000000'
M = {
    "first_only": [(A, '$mode == "start" and $c[0] < 10000000000')],
    "second_only": [(A, '$mode == "start" and $c[1] < 10000000000')],
    "start_sum_only": [(A, '$mode == "start" and ($c | add) < 20000000000')],
    "resume_each": [(B, '$mode == "resume" and any($c[]; . < 10000000000)')],
    "start_strict_greater": [(A, '$mode == "start" and any($c[]; . <= 10000000000)')],
    "resume_strict_greater": [(B, '$mode == "resume" and ($c | add) <= 20000000000')],
    "string_compare": [(A, '$mode == "start" and any($b[].available; . < 100000000)')],
    "uses_balance": [("available: .availableBalance", "available: .balance")],
    "null_as_zero": [("else null end;", "else 0 end;")],
    "tonumber_in_login": [("available: .availableBalance", "available: (.availableBalance | tonumber | tostring)")],
    "no_check": [('if [ -n "$FROM" ]; then check_start_balance resume; else check_start_balance start; fi', "true")],
}
for name, pairs in M.items():
    s = src
    for a, b in pairs:
        assert s.count(a) >= 1, (name, a); s = s.replace(a, b)
    (T / "mut2" / (name + ".sh")).write_text(s, encoding="utf-8")
```

## 정상 구현 출력

```text
== REQ-01 새 시작
[1억/1억] rc=1 rounds=1 aws=1 curl=3
  OK 회차 시작 로그
  OK conditions.json 1개, account_ids 9·10
  OK 첫 aws 호출 ssm send-command
[시작 "100000000.00"|"99999999.99"] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[시작 "99999999.99"|"100000000.01"] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[시작 "100000000.01"|"99999999.99"] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[시작 "0.00"|"200000000.00"] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[시작 "0.00"|"0.00"] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[balance 2억, available 부족] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 시작 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
== REQ-02 재개
[재개 "100000000.00"|"100000000.00"] rc=1 rounds=1 aws=1 curl=3
  OK 회차 시작 로그
  OK conditions.json 1개, account_ids 9·10
  OK 첫 aws 호출 ssm send-command
[재개 "99999000.00"|"100001000.00"] rc=1 rounds=1 aws=1 curl=3
  OK 회차 시작 로그
  OK conditions.json 1개, account_ids 9·10
  OK 첫 aws 호출 ssm send-command
[재개 "0.00"|"200000000.00"] rc=1 rounds=1 aws=1 curl=3
  OK 회차 시작 로그
  OK conditions.json 1개, account_ids 9·10
  OK 첫 aws 호출 ssm send-command
[재개 "99999999.99"|"100000000.01"] rc=1 rounds=1 aws=1 curl=3
  OK 회차 시작 로그
  OK conditions.json 1개, account_ids 9·10
  OK 첫 aws 호출 ssm send-command
[재개 합 부족(balance 각 2억)] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 재개 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[재개 해제 미확인+합 부족] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 재개 전 잔액 부족
  OK 계좌번호 없음
  OK 계좌 ID·잔액·필요 금액·다음 할 일
[재개 해제 미확인+합 충분(대조)] rc=1 rounds=0 aws=1 curl=3
  OK 회차 시작 전에 aws 호출
  OK 회차 시작 로그 없음
== REQ-03 잔액을 읽지 못함
[값 MISSING 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 MISSING 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 MISSING 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 MISSING 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 null 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 null 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 null 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 null 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "" 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "" 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "" 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "" 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "abc" 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "abc" 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "abc" 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "abc" 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "-5.00" 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "-5.00" 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "-5.00" 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 "-5.00" 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 100000000 계좌A 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 100000000 계좌A 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 100000000 계좌B 새 시작] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[값 100000000 계좌B 재개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: 잔액을 읽지 못했다
  OK 계좌번호 없음
[ACTIVE 1개] rc=1 rounds=0 aws=0 curl=2
  OK rc != 0
  OK aws 0회
  OK 새 회차 폴더 0개
  OK curl 2회(로그인·계좌 조회)
  OK 사유 로그: ACTIVE 계좌가 2개 이상 필요하다
  OK 계좌번호 없음
== REQ-04 다른 모드
[local 잔액 0] rc=1 rounds=1 aws=0 curl=2
  OK 회차 시작 로그
  OK 잔액 사유 로그 없음
[prod-check] rc=1 rounds=0 aws=1 curl=1
  OK 로그인 0회
[복귀 확인 새 스크립트 잔액 "0.00"] rc=0 rounds=0 aws=2 curl=4
  OK 이체 POST 1회·HTTP 201 COMPLETED
[복귀 확인 변경 전 잔액 "0.00"] rc=0 rounds=0 aws=2 curl=4
  OK restore.md 변경 전과 같음
[복귀 확인 새 스크립트 잔액 MISSING] rc=0 rounds=0 aws=2 curl=4
  OK 이체 POST 1회·HTTP 201 COMPLETED
[복귀 확인 변경 전 잔액 MISSING] rc=0 rounds=0 aws=2 curl=4
  OK restore.md 변경 전과 같음
== 비밀번호
[비밀번호 전달] rc=1 rounds=1 aws=1 curl=3
  OK curl 표준 입력에만 있음
  OK curl 인자에 없음
  OK 다른 명령 인자·출력·결과 폴더에 없음
검사 233개, 실패 0개
```

## 시나리오 스크립트 scenario.sh

```bash
#!/usr/bin/env bash
# 잔액 확인 시나리오: 사본 스크립트를 가짜 curl·aws·docker·k6로 돌린다. 운영 주소는 닫힌 포트.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; SRC="$1"; N=0; F=0
export PATH="$HERE/bin:$PATH" AWS_ACCESS_KEY_ID=fake AWS_SECRET_ACCESS_KEY=fake AWS_SESSION_TOKEN=fake
acct() { # id available balance
  printf '{"accountId":"%s","accountNumber":"fake-%s","status":"ACTIVE","balance":%s,"availableBalance":%s}' "$1" "$1" "$3" "$2"; }
resp() { # availA availB [balA] [balB] : 11(큰 잔액) 10(B) 9(A) 순서로 섞는다
  printf '{"data":{"content":[%s,%s,%s]}}' "$(acct 11 '"900000000.00"' '"900000000.00"')" "$(acct 10 "$2" "${4:-\"200000000.00\"}")" "$(acct 9 "$1" "${3:-\"200000000.00\"}")"; }
run() { # name mode args... (응답은 $R, 사전 준비는 $PRE)
  local name="$1"; shift; W="$(mktemp -d)"; export FAKE_LOG="$W/log" FAKE_ACCOUNTS="$W/accounts.json"; mkdir -p "$W/perf" "$FAKE_LOG"
  cp "$SRC" "$W/perf/run-deadlock.sh"; printf '%s' "$R" > "$FAKE_ACCOUNTS"; touch "$FAKE_LOG/curl.args" "$FAKE_LOG/aws.args"
  [ -z "${PRE:-}" ] || { mkdir -p "$W/perf/results/deadlock/prod/unsorted-for-update-vu50-r1"; printf '%s' "$PRE" > "$W/perf/results/deadlock/prod/unsorted-for-update-vu50-r1/result.json"; }
  PW='Zz-fake-pw-9'
  (cd "$W" && printf '%s\n' "$PW" | env BASE_URL=http://127.0.0.1:9 INSTANCE_ID=i-fake LOGIN_ID=fakeuser PASSWORD="$PW" bash perf/run-deadlock.sh "$@" > "$W/out.txt" 2>&1); RC=$?
  ROUNDS=$(find "$W/perf/results" -type d -name "*-vu*-r1" ! -path "*unsorted-for-update-vu50-r1" 2>/dev/null | wc -l | tr -d ' '); AWS=$(grep -c . "$FAKE_LOG/aws.args" || true); CURL=$(grep -c . "$FAKE_LOG/curl.args" || true)
  LOGIN=$(grep -c auth/login "$FAKE_LOG/curl.args" || true)
  echo "[$name] rc=$RC rounds=$ROUNDS aws=$AWS curl=$CURL"
}
ok() { N=$((N+1)); if [ "$2" = 1 ]; then echo "  OK $1"; else F=$((F+1)); echo "  FAIL $1"; fi; }
t() { [ "$@" ] && echo 1 || echo 0; }
stops() { # 중단 판정, 사유 정규식, 잔액 로그를 확인할지(yes/no)
  ok "rc != 0" "$(t "$RC" -ne 0)"; ok "aws 0회" "$(t "$AWS" -eq 0)"; ok "새 회차 폴더 0개" "$(t "$ROUNDS" -eq 0)"; ok "curl 2회(로그인·계좌 조회)" "$(t "$CURL" -eq 2)"
  ok "사유 로그: $1" "$(grep -qE "$1" "$W/out.txt" && echo 1 || echo 0)"
  ok "계좌번호 없음" "$(grep -q 'fake-9\|fake-10' "$W/out.txt" && echo 0 || echo 1)"
  if [ "${2:-no}" = yes ]; then ok "계좌 ID·잔액·필요 금액·다음 할 일" "$(grep -qE '계좌 9 사용 가능 .*계좌 10 사용 가능 ' "$W/out.txt" && grep -qE '100000000원|200000000원' "$W/out.txt" && grep -qE '입금|확인하고 다시 실행' "$W/out.txt" && echo 1 || echo 0)"; fi; }
passes() { ok "회차 시작 로그" "$(grep -q '회차 시작' "$W/out.txt" && echo 1 || echo 0)"; ok "conditions.json 1개, account_ids 9·10" "$(jq -e '.account_ids == [9, 10]' "$(find "$W/perf/results" -name conditions.json | head -1)" > /dev/null 2>&1 && echo 1 || echo 0)"; ok "첫 aws 호출 ssm send-command" "$(head -1 "$FAKE_LOG/aws.args" | grep -q '^ssm send-command' && echo 1 || echo 0)"; }
H='"100000000.00"'; H1='"100000000.01"'; L='"99999999.99"'
PRE=""
echo "== REQ-01 새 시작"; unset PRE
R="$(resp "$H" "$H")"; run "1억/1억" prod-all; passes
for pair in "$H|$L" "$L|$H1" "$H1|$L" '"0.00"|"200000000.00"' '"0.00"|"0.00"'; do R="$(resp "${pair%%|*}" "${pair##*|}")"; run "시작 ${pair}" prod-all; stops '시작 전 잔액 부족' yes; done
R="$(resp "$L" "$L" '"200000000.00"' '"200000000.00"')"; run "balance 2억, available 부족" prod-all; stops '시작 전 잔액 부족' yes
echo "== REQ-02 재개"
for pair in "$H|$H" '"99999000.00"|"100001000.00"' '"0.00"|"200000000.00"' "$L|$H1"; do R="$(resp "${pair%%|*}" "${pair##*|}")"; run "재개 ${pair}" prod-all --from current-vu50; passes; done
R="$(resp '"99999999.00"' "$H" '"200000000.00"' '"200000000.00"')"; run "재개 합 부족(balance 각 2억)" prod-all --from current-vu50; stops '재개 전 잔액 부족' yes
PRE='{"status":"완료","release":"해제 미확인(테스트)"}'
R="$(resp '"99999999.00"' "$H")"; run "재개 해제 미확인+합 부족" prod-all --from unsorted-for-update-vu100; stops "재개 전 잔액 부족" yes
R="$(resp "$H" "$H")"; run "재개 해제 미확인+합 충분(대조)" prod-all --from unsorted-for-update-vu100; ok "회차 시작 전에 aws 호출" "$(t "$AWS" -ge 1)"; ok "회차 시작 로그 없음" "$(grep -q '회차 시작' "$W/out.txt" && echo 0 || echo 1)"
unset PRE; PRE=""
echo "== REQ-03 잔액을 읽지 못함"
bad_start() { R="$1"; run "$2" prod-all; stops "$3" no; }
B='"200000000.00"'
for v in MISSING null '""' '"abc"' '"-5.00"' 100000000; do
  for side in A B; do for mode in start resume; do
    if [ "$v" = MISSING ]; then
      if [ "$side" = A ]; then R="$(printf '{"data":{"content":[%s,{"accountId":"10","accountNumber":"fake-10","status":"ACTIVE","balance":%s,"availableBalance":%s},{"accountId":"9","accountNumber":"fake-9","status":"ACTIVE","balance":%s}]}}' "$(acct 11 '"900000000.00"' '"900000000.00"')" "$B" "$B" "$B")"
      else R="$(printf '{"data":{"content":[%s,{"accountId":"10","accountNumber":"fake-10","status":"ACTIVE","balance":%s},%s]}}' "$(acct 11 '"900000000.00"' '"900000000.00"')" "$B" "$(acct 9 "$B" "$B")")"; fi
    elif [ "$side" = A ]; then R="$(resp "$v" "$B")"; else R="$(resp "$B" "$v")"; fi
    if [ "$mode" = start ]; then run "값 $v 계좌$side 새 시작" prod-all; else run "값 $v 계좌$side 재개" prod-all --from current-vu50; fi
    stops '잔액을 읽지 못했다' no
  done; done; done
R='{"data":{"content":[{"accountId":"9","accountNumber":"fake-9","status":"ACTIVE","balance":"200000000.00","availableBalance":"200000000.00"}]}}'; run "ACTIVE 1개" prod-all; stops 'ACTIVE 계좌가 2개 이상 필요하다' no
echo "== REQ-04 다른 모드"
R="$(resp '"0.00"' '"0.00"')"; run "local 잔액 0" local current 50 1; ok "회차 시작 로그" "$(grep -q '회차 시작' "$W/out.txt" && echo 1 || echo 0)"; ok "잔액 사유 로그 없음" "$(grep -q '잔액 부족\|잔액을 읽지' "$W/out.txt" && echo 0 || echo 1)"
R="$(resp '"0.00"' '"0.00"')"; run "prod-check" prod-check; ok "로그인 0회" "$(t "$LOGIN" -eq 0)"
for v in '"0.00"' MISSING; do
  if [ "$v" = MISSING ]; then R='{"data":{"content":[{"accountId":"9","accountNumber":"fake-9","status":"ACTIVE"},{"accountId":"10","accountNumber":"fake-10","status":"ACTIVE"}]}}'; else R="$(resp "$v" "$v")"; fi
  run "복귀 확인 새 스크립트 잔액 $v" prod-restore-check; A="$(sed -E 's/[0-9]{4}-[0-9-]+T[0-9:]+Z/TS/' "$W/perf/results/deadlock/prod/restore.md")"; POSTS=$(grep -c . "$FAKE_LOG/transfer.posts"); ok "이체 POST 1회·HTTP 201 COMPLETED" "$(t "$POSTS" -eq 1 -a -n "$(grep '이체 1건: HTTP 201, COMPLETED' <<< "$A")")"
  SAVED="$SRC"; SRC="$HERE/before.sh"; run "복귀 확인 변경 전 잔액 $v" prod-restore-check; B2="$(sed -E 's/[0-9]{4}-[0-9-]+T[0-9:]+Z/TS/' "$W/perf/results/deadlock/prod/restore.md")"; SRC="$SAVED"
  ok "restore.md 변경 전과 같음" "$([ "$A" = "$B2" ] && echo 1 || echo 0)"
done
echo "== 비밀번호"
R="$(resp "$H" "$H")"; run "비밀번호 전달" prod-all
ok "curl 표준 입력에만 있음" "$([ "$(grep -c 'Zz-fake-pw-9' "$FAKE_LOG/curl.stdin")" -ge 1 ] && echo 1 || echo 0)"
ok "curl 인자에 없음" "$(grep -q 'Zz-fake-pw-9' "$FAKE_LOG/curl.args" && echo 0 || echo 1)"
ok "다른 명령 인자·출력·결과 폴더에 없음" "$(grep -rqs 'Zz-fake-pw-9' "$FAKE_LOG/aws.args" "$FAKE_LOG"/k6.args "$FAKE_LOG"/docker.args "$W/out.txt" "$W/perf/results" && echo 0 || echo 1)"
echo "검사 $N개, 실패 $F개"; [ "$F" -eq 0 ]
```

## 가짜 curl

```python
#!/usr/bin/env python3
# 가짜 curl: 실험 스크립트가 쓰는 옵션만 흉내 낸다. 호출 인자를 기록한다.
import json, os, sys
a = sys.argv[1:]
log = os.environ["FAKE_LOG"]
open(log + "/curl.args", "a").write(json.dumps(a, ensure_ascii=False) + "\n")
out = hdr = wfmt = None; data = None; url = None; method = "GET"; i = 0
while i < len(a):
    x = a[i]
    if x in ("-o",): out = a[i+1]; i += 2; continue
    if x == "-D": hdr = a[i+1]; i += 2; continue
    if x == "-w": wfmt = a[i+1]; i += 2; continue
    if x == "-X": method = a[i+1]; i += 2; continue
    if x in ("-H", "--max-time"): i += 2; continue
    if x == "--data-binary": data = a[i+1]; i += 2; continue
    if x.startswith("http"): url = x
    i += 1
if data == "@-":
    open(log + "/curl.stdin", "a").write(sys.stdin.read() + "\n")
body, code = "", "200"
if url.endswith("/auth/login"):
    open(hdr, "w").write("HTTP/1.1 200\r\nSet-Cookie: access_token=tokA; Path=/\r\nSet-Cookie: XSRF-TOKEN=csrfB; Path=/\r\n\r\n")
    body = json.dumps({"data": {"customerId": "7"}})
elif "/customers/7/accounts" in url:
    body = open(os.environ["FAKE_ACCOUNTS"]).read()
elif url.endswith("/api/v1/transfers"):
    open(log + "/transfer.posts", "a").write("1\n")
    body, code = json.dumps({"data": {"status": "COMPLETED"}}), "201"
if out and out != "/dev/null": open(out, "w").write(body)
elif not out: sys.stdout.write(body)
if wfmt: sys.stdout.write(code)
```

## 가짜 aws

```bash
#!/usr/bin/env bash
echo "$*" >> "$FAKE_LOG/aws.args"
case "$*" in *"get-schedule"*) echo ENABLED; exit 0;; *"send-command"*) echo "fake ssm refused" >&2; exit 1;; esac
exit 0
```

## 가짜 docker, k6(같은 내용, 이름만 다름)

```bash
#!/usr/bin/env bash
echo "$(basename "$0") $*" >> "$FAKE_LOG/k6.args"
[ "$(basename "$0")" = k6 ] && echo "k6 vfake"
exit 1
```
