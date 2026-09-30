"""입금 비동기 반영(ADR 0012)용 측정 도구 검사. 실행: python3 perf/ec2/tests/test_async_credit.py

- write_integrity: 미반영 입금이 남은 결과에서 원장·차변/대변·핫 계좌 식이 "같음"이 되고, 이전 이미지 결과(미반영 항목 없음)도
  그대로 "같음"이다.
- credit_lag: DB 생성·반영 시각으로 단계별 p95와 판정값(무너지기 전 단계 최댓값)을 계산한다.
"""
import importlib.util
import json
import os
import shutil
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
spec = importlib.util.spec_from_file_location("run_ec2", os.path.join(ROOT, "perf/run-ec2.py"))
run_ec2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_ec2)


def make_round(target_lines, hot201=100):
    d = tempfile.mkdtemp()
    os.makedirs(os.path.join(d, "target"))
    os.makedirs(os.path.join(d, "loadgen"))
    with open(os.path.join(d, "target/integrity-target.txt"), "w") as f:
        f.write("\n".join(target_lines) + "\n")
    with open(os.path.join(d, "target/failed-keys-target.txt"), "w") as f:
        f.write("failed_keys_total=0\nfailed_keys_committed=0\n")
    with open(os.path.join(d, "target/batch.txt"), "w") as f:
        f.write("batch job=ledgerReconciliationJob exit=0\n")
    checks = {"hot-transfer 201": {"passes": hot201, "fails": 0}}
    with open(os.path.join(d, "loadgen/k6-summary.json"), "w") as f:
        json.dump({"root_group": {"checks": checks}, "metrics": {}}, f)
    return d


BASE = [
    "new_transfer_completed=100", "new_transfer_other_status=0", "new_non_transfer=0",
    "new_mismatch_accounts=0", "baseline_account_delta_mismatch=0", "duplicate_idempotency_keys=0",
    "new_transfer_amount_sum=100000.00", "hot_completed_amount_sum=100000.00",
]


def verdicts(d):
    text = open(os.path.join(d, "integrity.md"), encoding="utf-8").read()
    return {line.split("|")[1].strip(): line.split("|")[3].strip() for line in text.splitlines()
            if line.startswith("| ") and not line.startswith("| 항목") and line.count("|") >= 4}


def test_unapplied_included():
    # 100건 중 30건 미반영: 대변 원장 70건, 핫 계좌 잔액 +70,000, 미반영 30,000
    d = make_round(BASE + [
        "new_ledger_rows=170", "new_ledger_debit=100000.00", "new_ledger_credit=70000.00",
        "hot_balance_delta=70000.00", "global_debit_delta=100000.00", "global_credit_delta=70000.00",
        "new_unapplied_count=30", "new_unapplied_sum=30000.00", "hot_unapplied_sum=30000.00",
        "all_unapplied_sum=30000.00", "transfer_pending_row_mismatch=0", "transfer_credit_entry_mismatch=0",
    ])
    run_ec2.write_integrity(d, "t")
    v = verdicts(d)
    assert v["새 원장 행 수 vs 2 × 새 이체 수 − 미반영"] == "같음", v
    assert v["새 원장 차변 합 = 대변 합 + 미반영"] == "같음", v
    assert v["핫 계좌 잔액 증가분 + 미반영 vs 핫 계좌 성공 이체 금액 합(DB)"] == "같음", v
    assert v["핫 계좌 잔액 증가분 + 미반영 vs k6 hot-transfer 201 × 1,000원"] == "같음", v
    assert v["전체 차변·대변 증가분(+ 미반영 전체)"] == "같음", v
    assert v["완료 이체별 입금 대기 1건 아님 / 대변 원장 수 불일치(반영 1, 미반영 0)"] == "중복·유실 없음", v
    shutil.rmtree(d)


def test_detects_mismatch():
    d = make_round(BASE + [
        "new_ledger_rows=170", "new_ledger_debit=100000.00", "new_ledger_credit=70000.00",
        "hot_balance_delta=70000.00", "global_debit_delta=100000.00", "global_credit_delta=70000.00",
        "new_unapplied_count=20", "new_unapplied_sum=20000.00", "hot_unapplied_sum=20000.00",
        "all_unapplied_sum=20000.00", "transfer_pending_row_mismatch=0", "transfer_credit_entry_mismatch=3",
    ])
    run_ec2.write_integrity(d, "t")
    v = verdicts(d)
    assert v["새 원장 차변 합 = 대변 합 + 미반영"] == "다름", v
    assert v["완료 이체별 입금 대기 1건 아님 / 대변 원장 수 불일치(반영 1, 미반영 0)"] == "원인 분석 대상", v
    shutil.rmtree(d)


def test_previous_image_results_unchanged():
    # 미반영 항목이 없는 이전 이미지 결과: 대변 원장이 이체마다 즉시 기록
    d = make_round(BASE + [
        "new_ledger_rows=200", "new_ledger_debit=100000.00", "new_ledger_credit=100000.00",
        "hot_balance_delta=100000.00", "global_debit_delta=100000.00", "global_credit_delta=100000.00",
    ])
    run_ec2.write_integrity(d, "t")
    v = verdicts(d)
    assert v["새 원장 행 수 vs 2 × 새 이체 수 − 미반영"] == "같음", v
    assert v["새 원장 차변 합 = 대변 합 + 미반영"] == "같음", v
    assert v["전체 차변·대변 증가분(+ 미반영 전체)"] == "같음", v
    assert run_ec2.credit_lag(d) is None  # 원자료 없음
    shutil.rmtree(d)


def test_credit_lag():
    d = make_round(BASE)
    analysis = {"collapse_rps": 60, "stages": [
        {"target_rps": 40, "window": [1000, 1050]},
        {"target_rps": 50, "window": [1060, 1110]},
        {"target_rps": 60, "window": [1120, 1170]},
    ]}
    with open(os.path.join(d, "loadgen/analysis.json"), "w") as f:
        json.dump(analysis, f)
    rows = []
    for i in range(20):  # 40 단계: 0.1초
        rows.append(f"{i},1,{1000 + i},{1000 + i + 0.1}")
    for i in range(20):  # 50 단계: 0.3초, 마지막 하나 0.9초
        lag = 0.9 if i == 19 else 0.3
        rows.append(f"{100 + i},1,{1060 + i},{1060 + i + lag}")
    for i in range(10):  # 60 단계(무너진 단계): 5초, 2건 미반영
        rows.append(f"{200 + i},1,{1120 + i},{'' if i < 2 else 1120 + i + 5}")
    with open(os.path.join(d, "target/pending-credits.csv"), "w") as f:
        f.write("\n".join(rows) + "\n")
    lag = run_ec2.credit_lag(d)
    by = {s["target_rps"]: s for s in lag["stages"]}
    assert by[40]["p95_ms"] == 100.0, by[40]
    assert by[50]["p95_ms"] == 300.0 and by[50]["max_ms"] == 900.0, by[50]
    assert by[60]["unapplied"] == 2 and by[60]["p95_ms"] == 5000.0, by[60]
    assert lag["judge_p95_ms"] == 300.0, lag  # 무너진 60 단계는 판정에서 뺀다
    assert lag["total"] == 50 and lag["unapplied_total"] == 2, lag
    shutil.rmtree(d)


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
