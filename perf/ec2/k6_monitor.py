#!/usr/bin/env python3
"""부하 발생기에서 도는 k6 감시·분석·원자료 수집(perf/README.md "EC2 부하 테스트" 절).

  watch   <s1|s2> <run> <k6 pid> <시작 ISO>  : 단계가 끝날 때마다 무너짐을 판정하고, 무너진 뒤
                                              한 단계를 더 채우면 k6에 SIGINT를 보낸다(task.md 측정 조건).
  analyze <s1|s2|s3|s5|warmup> <run> <시작> <끝> [label:시작epoch:끝epoch,...]
                                            : 단계·구간별 지표를 계산해 JSON 한 줄과 표를 출력한다.
  export  <run> <시작> <끝> <대시보드 JSON>   : 대시보드 전 패널 쿼리의 query_range 원자료를 저장한다.

무너짐: 단계 구간(시작 후 10초 제외)의 클라이언트 p95 > 200ms, 오류율 > 1%(timeout 포함),
대상 컨테이너 재시작·OOM 중 하나. 통계는 k6가 Prometheus로 보낸 네이티브 히스토그램으로 계산한다.
표준 라이브러리만 쓴다.
"""
import datetime as dt
import gzip
import json
import os
import signal
import sys
import time
import urllib.parse
import urllib.request

PROM = "http://localhost:9090"
OUT = "/opt/perf-out"
P95_LIMIT = 0.2
ERR_LIMIT = 0.01
SKIP = 10  # 단계 시작 후 제외 구간(초)

# 시나리오별 단계 정의(k6 스크립트와 같아야 한다): (앞선 구간 초, 단계 길이 초, 시작 요청률, 증가폭, 상한)
RAMPS = {
    "s1": {"lead": 0, "stage": 120, "step": 50, "max": 1000, "scenario": "mixed"},
    "s2": {"lead": 60, "stage": 60, "step": 20, "max": 400, "scenario": "hot_transfer"},
}


def ts(iso):
    return dt.datetime.strptime(iso, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc).timestamp()


def query(expr, at):
    url = f"{PROM}/api/v1/query?" + urllib.parse.urlencode({"query": expr, "time": at})
    with urllib.request.urlopen(url, timeout=30) as res:
        data = json.load(res)["data"]["result"]
    if not data:
        return None
    value = float(data[0]["value"][1])
    return None if value != value else value  # NaN → None


def query_range(expr, start, end, step=5):
    url = f"{PROM}/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": expr, "start": start, "end": end, "step": step})
    with urllib.request.urlopen(url, timeout=60) as res:
        return json.load(res)["data"]["result"]


def window_stats(run, sel, start, end):
    """[start, end] 구간의 요청 통계. sel은 k6 지표에 붙일 추가 라벨 조건."""
    d = max(int(end - start), 1)
    base = f'run="{run}"' + (f",{sel}" if sel else "")
    total = query(f"sum(increase(k6_http_reqs_total{{{base}}}[{d}s]))", end) or 0
    failed = query(f'sum(increase(k6_http_reqs_total{{{base},expected_response="false"}}[{d}s]))', end) or 0
    stats = {
        "requests": round(total),
        "rps": round(total / d, 1),
        "error_rate": round(failed / total, 4) if total else None,
        "p95_s": query(f"histogram_quantile(0.95, sum(increase(k6_http_req_duration_seconds{{{base}}}[{d}s])))", end),
        "p99_s": query(f"histogram_quantile(0.99, sum(increase(k6_http_req_duration_seconds{{{base}}}[{d}s])))", end),
        "status_401": round(query(f'sum(increase(k6_http_reqs_total{{{base},status="401"}}[{d}s]))', end) or 0),
        "timeouts": round(query(f'sum(increase(k6_http_reqs_total{{{base},status="0"}}[{d}s]))', end) or 0),
    }
    return stats


def system_stats(run, start, end):
    d = max(int(end - start), 1)
    r = f"[{d}s:5s]"
    q = {
        "dropped_iterations": f'sum(increase(k6_dropped_iterations_total{{run="{run}"}}[{d}s]))',
        "loadgen_cpu_max": f'max_over_time((1 - avg(rate(node_cpu_seconds_total{{job="loadgen-node",mode="idle"}}[30s]))){r})',
        "target_cpu_avg": f'avg_over_time((1 - avg(rate(node_cpu_seconds_total{{job="target-node",mode="idle"}}[30s]))){r})',
        "target_steal_avg": f'avg_over_time(avg(rate(node_cpu_seconds_total{{job="target-node",mode="steal"}}[30s])){r})',
        "hikari_active_max": f'max_over_time(sum(hikaricp_connections_active{{job="api"}}){r})',
        "hikari_pending_max": f'max_over_time(sum(hikaricp_connections_pending{{job="api"}}){r})',
        "lock_wait_max_s": f'max_over_time(max(db_lock_wait_seconds_max{{job="api"}}){r})',
        "tomcat_busy_max": f'max_over_time(sum(tomcat_threads_busy_threads{{job="api"}}){r})',
        "heap_used_max": f'max_over_time(sum(jvm_memory_used_bytes{{job="api",area="heap"}}){r})',
        "gc_pause_ratio": f'sum(increase(jvm_gc_pause_seconds_sum{{job="api"}}[{d}s])) / {d}',
        "pg_lock_waiting_max": f'max_over_time(pg_lock_waiting_sessions{{job="target-postgres"}}{r})',
        "pg_cache_hit": f'sum(increase(pg_stat_database_blks_hit{{job="target-postgres",datname="jbank"}}[{d}s])) / (sum(increase(pg_stat_database_blks_hit{{job="target-postgres",datname="jbank"}}[{d}s])) + sum(increase(pg_stat_database_blks_read{{job="target-postgres",datname="jbank"}}[{d}s])))',
        "mem_available_min": f'min_over_time(node_memory_MemAvailable_bytes{{job="target-node"}}{r})',
        "container_restarts": f'sum(changes(container_start_time_seconds{{job="target-cadvisor",name!=""}}[{d}s]))',
        "container_oom_events": f'sum(increase(container_oom_events_total{{job="target-cadvisor",name!=""}}[{d}s]))',
    }
    return {k: query(v, end) for k, v in q.items()}


def collapsed(stats, system):
    reasons = []
    if stats["p95_s"] is not None and stats["p95_s"] > P95_LIMIT:
        reasons.append("p95")
    if stats["error_rate"] is not None and stats["error_rate"] > ERR_LIMIT:
        reasons.append("error_rate")
    if (system.get("container_restarts") or 0) > 0 or (system.get("container_oom_events") or 0) > 0:
        reasons.append("restart_or_oom")
    return reasons


def stage_windows(mode, start, end):
    cfg = RAMPS[mode]
    out, i = [], 0
    while True:
        s = start + cfg["lead"] + i * cfg["stage"]
        e = s + cfg["stage"]
        rate = cfg["step"] * (i + 1)
        if rate > cfg["max"] or s + SKIP >= end:
            break
        out.append((rate, s + SKIP, min(e, end)))
        i += 1
    return out


def evaluate_stage(mode, run, rate, s, e):
    cfg = RAMPS[mode]
    stats = window_stats(run, f'scenario="{cfg["scenario"]}"', s, e)
    system = system_stats(run, s, e)
    row = {"target_rps": rate, "window": [s, e], **stats, **system, "collapse": collapsed(stats, system)}
    if mode == "s2":
        row["parallel_balance"] = window_stats(run, 'scenario="parallel_balance"', s, e)
    return row


def watch(mode, run, pid, start_iso):
    start = ts(start_iso)
    cfg = RAMPS[mode]
    collapse_stage = None
    i = 0
    while True:
        s = start + cfg["lead"] + i * cfg["stage"]
        e = s + cfg["stage"]
        # 원격 쓰기 반영을 기다려 단계 종료 15초 뒤에 판정한다.
        while time.time() < e + 15:
            if not os.path.exists(f"/proc/{pid}"):
                print("k6 종료 감지", flush=True)
                return
            time.sleep(5)
        row = evaluate_stage(mode, run, cfg["step"] * (i + 1), s + SKIP, e)
        print(json.dumps(row), flush=True)
        if collapse_stage is None and row["collapse"]:
            collapse_stage = i
        elif collapse_stage is not None:
            print(f"무너짐 단계 {collapse_stage} 이후 한 단계 추가 완료 → k6 중단", flush=True)
            os.kill(pid, signal.SIGINT)
            return
        i += 1


def analyze(mode, run, start_iso, end_iso, windows_arg=""):
    start, end = ts(start_iso), ts(end_iso)
    result = {"run": run, "mode": mode, "start": start_iso, "end": end_iso}
    if mode in RAMPS:
        rows = [evaluate_stage(mode, run, rate, s, e) for rate, s, e in stage_windows(mode, start, end)]
        result["stages"] = rows
        first = next((r for r in rows if r["collapse"]), None)
        result["collapse_rps"] = first["target_rps"] if first else None
        result["collapse_reasons"] = first["collapse"] if first else []
        prev = [r for r in rows if not r["collapse"] and (first is None or r["target_rps"] < first["target_rps"])]
        result["max_sustainable_rps"] = prev[-1]["target_rps"] if prev else None
        result["loadgen_cpu_max"] = max((r["loadgen_cpu_max"] or 0) for r in rows) if rows else None
        if mode == "s2":
            pre = window_stats(run, 'scenario="parallel_balance"', start, start + 60)
            result["parallel_pre_p95_s"] = pre["p95_s"]
            for r in rows:
                pb = r["parallel_balance"]["p95_s"]
                r["propagation"] = bool(pb is not None and (
                    pb > P95_LIMIT or (pre["p95_s"] and pb >= 3 * pre["p95_s"])))
            result["propagation_at_collapse"] = first["propagation"] if first else None
    elif mode == "s3":
        pre = window_stats(run, 'scenario="probe"', start, start + 60)
        spike = window_stats(run, 'scenario="spike"', start + 60, start + 140)
        result["probe_pre"] = pre
        result["spike"] = spike
        result["spike_system"] = system_stats(run, start + 60, start + 140)
        series = query_range(
            f'histogram_quantile(0.95, sum(increase(k6_http_req_duration_seconds{{run="{run}",scenario="probe"}}[10s])))',
            start + 150, end, 5)
        points = [(float(t), float(v)) for t, v in (series[0]["values"] if series else []) if v != "NaN"]
        limit = 1.5 * pre["p95_s"] if pre["p95_s"] else None
        recovered_at = None
        if limit:
            for idx, (t, _) in enumerate(points):
                span = [v for tt, v in points[idx:] if tt <= t + 30]
                if span and all(v <= limit for v in span) and points[-1][0] >= t + 30:
                    recovered_at = t
                    break
        spike_end = start + 140
        result["recovery_limit_s"] = limit
        result["recovery_seconds"] = round(recovered_at - spike_end, 1) if recovered_at else None
        result["recovered_within_60s"] = bool(recovered_at is not None and recovered_at - spike_end <= 60)
    else:  # s5, warmup: 전체 + 요청한 구간들
        result["overall"] = window_stats(run, "", start, end)
        result["overall_system"] = system_stats(run, start, end)
        result["windows"] = {}
        for item in filter(None, windows_arg.split(",")):
            label, s, e = item.split(":")
            s, e = float(s), float(e)
            result["windows"][label] = {**window_stats(run, "", s, e), **system_stats(run, s, e)}
    print(json.dumps(result, ensure_ascii=False))


def export(run, start_iso, end_iso, dashboard_path):
    start, end = ts(start_iso) - 60, ts(end_iso) + 60
    dash = json.load(open(dashboard_path, encoding="utf-8"))
    out = {"run": run, "start": start, "end": end, "step": 5, "panels": []}
    for panel in dash["panels"]:
        entry = {"title": panel["title"], "targets": []}
        for target in panel["targets"]:
            expr = target["expr"].replace("$__rate_interval", "30s")
            entry["targets"].append({"expr": expr, "result": query_range(expr, start, end, 5)})
        out["panels"].append(entry)
    os.makedirs(f"{OUT}/{run}/prometheus", exist_ok=True)
    path = f"{OUT}/{run}/prometheus/query_range.json.gz"
    with gzip.open(path, "wt", encoding="utf-8") as f:
        json.dump(out, f)
    empty = [f"{p['title']} :: {t['expr'][:60]}" for p in out["panels"] for t in p["targets"] if not t["result"]]
    print(f"saved {path}; empty_series={len(empty)}")
    for item in empty:
        print(f"EMPTY {item}")


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "watch":
        watch(sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5])
    elif command == "analyze":
        analyze(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6] if len(sys.argv) > 6 else "")
    elif command == "export":
        export(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    else:
        sys.exit(f"알 수 없는 명령: {command}")
