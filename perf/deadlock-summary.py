#!/usr/bin/env python3
"""교착 재현 실험 결과 요약표(docs/runbook/transfer-deadlock-experiment.md, REQ-08).

perf/results/deadlock/<환경>/<조건>-vu<N>-r<회차>/의 결과 파일을 읽어 회차별 markdown 표를 출력한다.
보고서(perf/results/deadlock/README.md)는 이 출력을 <!-- summary:<환경> --> 표시 사이에 그대로 넣는다.

  python3 perf/deadlock-summary.py local            # 표 출력
  python3 perf/deadlock-summary.py local --check    # 보고서의 표와 같은지 확인(다르면 diff를 보이고 1로 끝남)

불변식(REQ-05): 잔액 합 보존, 원장 증가 = 완료 증가 × 2, 음수 잔액 0,
"실패 응답 중 반영 수 = 완료 증가 − 2xx"가 0 이상·실패 응답 수 이하.
"""
import difflib
import json
import sys
from decimal import Decimal
from pathlib import Path

RESULTS = Path(__file__).resolve().parent / "results" / "deadlock"
ORDER = {"unsorted-for-update": 1, "sorted-for-update": 2, "current": 3}
LABEL = {"unsorted-for-update": "① 정렬 없음+FOR UPDATE", "sorted-for-update": "② 정렬+FOR UPDATE",
         "current": "③ 현재(정렬+NO KEY UPDATE/KEY SHARE)"}
LOCK_METHODS = ("lockSenderByAccountNumber", "lockReceiverByAccountNumber", "lockForUpdateByAccountNumber")


def load(path):
    return json.loads(path.read_text()) if path.exists() else None


def metric(summary, name, field):
    m = summary.get("metrics", {}).get(name)
    if m is None:
        return 0
    return m.get("values", m).get(field, 0)


def lock_wait_ms(before, after):
    """락 메서드들의 측정 구간 평균 대기(ms). Prometheus 수집 간격(15초)만큼 늦을 수 있어 기록용이다."""
    if not before or not after:
        return "-"
    def series(snap, key):
        return {r["metric"].get("method"): float(r["value"][1]) for r in snap.get(key, [])}
    count_key, sum_key = "sum by (method) (db_lock_wait_seconds_count)", "sum by (method) (db_lock_wait_seconds_sum)"
    c0, c1 = series(before, count_key), series(after, count_key)
    s0, s1 = series(before, sum_key), series(after, sum_key)
    count = sum(c1.get(m, 0) - c0.get(m, 0) for m in LOCK_METHODS)
    total = sum(s1.get(m, 0) - s0.get(m, 0) for m in LOCK_METHODS)
    return f"{total / count * 1000:.1f}" if count > 0 else "-"


def row(run):
    conditions, result = load(run / "conditions.json"), load(run / "result.json") or {}
    status = result.get("status", "기록 없음")
    base = [run.name, LABEL[conditions["strategy"]], str(conditions["vus"]), status]
    summary, before, after = load(run / "k6-summary.json"), load(run / "before.json"), load(run / "after.json")
    if status != "완료" or not (summary and before and after):
        return base + ["-"] * 12 + [result.get("release", "-")]

    # http_reqs에는 setup()의 로그인 요청이 섞이므로 이체 응답 카운터의 합을 요청 수로 쓴다.
    ok = int(metric(summary, "status_2xx", "count"))
    s5xx = int(metric(summary, "status_5xx", "count"))
    s0 = int(metric(summary, "status_0", "count"))
    requests = ok + int(metric(summary, "status_4xx", "count")) + s5xx + s0
    code006 = int(metric(summary, "code_COMMON_006_INTERNAL_ERROR", "count"))
    other = int(metric(summary, "code_other", "count"))
    deadlocks = after["deadlocks"] - before["deadlocks"]
    completed = after["completed"] - before["completed"]
    reflected = completed - ok
    failures = requests - ok
    invariant = (Decimal(str(after["balance_sum"])) == Decimal(str(before["balance_sum"]))
                 and after["ledger"] - before["ledger"] == completed * 2
                 and after["negative"] == 0
                 and 0 <= reflected <= failures)
    mem = run / "mem-min-mib.txt"
    return base + [
        str(requests), str(ok), f"{s5xx} ({code006})", str(other), str(s0), str(deadlocks), str(reflected),
        "유지" if invariant else "위반",
        f"{metric(summary, 'http_req_duration', 'p(95)'):.0f}", f"{metric(summary, 'http_req_duration', 'p(99)'):.0f}",
        f"{metric(summary, 'http_reqs', 'rate'):.1f}",
        lock_wait_ms(load(run / "lock-wait-before.json"), load(run / "lock-wait-after.json"))
        + (f" / {mem.read_text().strip()}" if mem.exists() else ""),
        result.get("release", "-"),
    ]


def table(env):
    runs = [p for p in (RESULTS / env).iterdir() if (p / "conditions.json").exists()]
    runs.sort(key=lambda p: (ORDER[load(p / "conditions.json")["strategy"]],
                             load(p / "conditions.json")["vus"], load(p / "conditions.json")["round"]))
    header = ["회차", "조건", "VU", "결과", "요청", "2xx", "5xx (COMMON_006)", "기타 오류", "타임아웃",
              "교착 증가", "실패 응답 중 반영", "불변식", "p95 ms", "p99 ms", "처리량 req/s",
              "락 대기 평균 ms" + (" / 최저 가용 MiB" if env == "prod" else ""), "알림 해제"]
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    lines += ["| " + " | ".join(row(run)) + " |" for run in runs]
    return "\n".join(lines) + "\n"


def readme_block(env):
    text = (RESULTS / "README.md").read_text()
    start, end = f"<!-- summary:{env} -->\n", f"<!-- /summary:{env} -->"
    return text[text.index(start) + len(start):text.index(end)]


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("local", "prod"):
        sys.exit(__doc__)
    env = sys.argv[1]
    generated = table(env)
    if "--check" not in sys.argv:
        sys.stdout.write(generated)
        return
    written = readme_block(env)
    if generated != written:
        sys.stdout.writelines(difflib.unified_diff(written.splitlines(True), generated.splitlines(True),
                                                   "README.md", "deadlock-summary.py"))
        sys.exit(1)
    print(f"{env}: 보고서 표와 스크립트 출력이 같다")


if __name__ == "__main__":
    main()
