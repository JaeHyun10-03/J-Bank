#!/usr/bin/env python3
"""EC2 부하 테스트 오케스트레이터(로컬에서 실행). 절차·조건은 perf/README.md "EC2 부하 테스트" 절과
.claude/tasks/ec2-load-test/task.md의 측정 조건을 따른다.

대상·부하 발생기는 인바운드가 없어서 모든 원격 작업을 SSM Run Command로 보내고, 결과 파일도
SSM 출력으로 나눠 받아온다(추가 IAM 권한·SSH 없음). 명령은 perf/ec2/target.sh·loadgen.sh를 호출한다.

  python3 perf/run-ec2.py up                      # terraform apply + 생성 조건 기록(REQ-01)
  python3 perf/run-ec2.py setup                   # 두 인스턴스 저장소 동기화·스택 기동(REQ-03)
  python3 perf/run-ec2.py prepare                 # 시드 → 준비 → 기준 대사 → 복사본 → 복원 검증(REQ-04)
  python3 perf/run-ec2.py run s1 1                # 시나리오 회차 실행(s1|s2|s3), 결과를 로컬로 수집
  python3 perf/run-ec2.py run s5 1 --rate 140     # S5는 S1 최대 지속 가능 요청률 최솟값의 70%
  python3 perf/run-ec2.py dry-run                 # 짧은 S1(초당 10건 20초)로 수집 경로 확인(기준선 제외)
  python3 perf/run-ec2.py down                    # terraform destroy + 삭제 확인(REQ-02)

모든 명령은 perf/results/ec2-baseline/env/commands.log에 실행 기록을 남긴다(REQ-13).
"""
import argparse
import base64
import datetime as dt
import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TF_DIR = os.path.join(ROOT, "infra/terraform/envs/perf")
RESULTS = os.path.join(ROOT, "perf/results/ec2-baseline")
REGION = "ap-northeast-2"
GIT_REF = "perf/ec2-load-test"
CHUNK = 20000
UPLOAD_CHUNK = 8000  # SSM 명령 매개변수 크기 한도 안쪽


def log(msg):
    line = f"{dt.datetime.now().isoformat(timespec='seconds')} {msg}"
    print(line, flush=True)
    os.makedirs(os.path.join(RESULTS, "env"), exist_ok=True)
    with open(os.path.join(RESULTS, "env/commands.log"), "a", encoding="utf-8") as f:
        f.write(line + "\n")


def sh(args, check=True, capture=True):
    res = subprocess.run(args, text=True, capture_output=capture)
    if check and res.returncode != 0:
        raise SystemExit(f"실패: {' '.join(args)}\n{res.stdout}\n{res.stderr}")
    return res


def aws(*args):
    out = sh(["aws", "--region", REGION, "--output", "json", *args]).stdout
    return json.loads(out) if out.strip() else {}


def tf_output():
    return json.loads(sh(["terraform", f"-chdir={TF_DIR}", "output", "-json"]).stdout)


def ids():
    o = tf_output()
    return {k: o[k]["value"] for k in o}


# --- SSM ---------------------------------------------------------------------

def ssm_send(instance, command, timeout=7200):
    params = {"commands": [command], "executionTimeout": [str(timeout)]}
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(params, f)
        path = f.name
    try:
        res = aws("ssm", "send-command", "--instance-ids", instance,
                  "--document-name", "AWS-RunShellScript", "--parameters", f"file://{path}",
                  "--timeout-seconds", "60")
    finally:
        os.unlink(path)
    return res["Command"]["CommandId"]


def ssm_wait(instance, command_id, echo=True):
    while True:
        time.sleep(3)
        try:
            inv = aws("ssm", "get-command-invocation", "--command-id", command_id, "--instance-id", instance)
        except SystemExit:
            continue  # 막 보낸 명령은 잠시 InvocationDoesNotExist가 난다
        if inv["Status"] in ("Pending", "InProgress", "Delayed"):
            continue
        out, err = inv.get("StandardOutputContent", ""), inv.get("StandardErrorContent", "")
        if echo and out.strip():
            print(out.rstrip())
        return inv["Status"], inv.get("ResponseCode"), out, err


def ssm(instance, command, timeout=7200, check=True, echo=True):
    log(f"ssm {instance}: {command}")
    status, code, out, err = ssm_wait(instance, ssm_send(instance, command, timeout), echo)
    if check and status != "Success":
        raise SystemExit(f"원격 명령 실패({status}, {code}): {command}\n{out[-3000:]}\n{err[-3000:]}")
    return out


def fetch_dir(instance, remote_dir, local_dir):
    """원격 디렉터리를 tar.gz → base64로 만들어 SSM 출력 한도(24,000자) 안에서 나눠 받는다."""
    tmp = "/tmp/perf-fetch.b64"
    size = int(ssm(instance, f"cd {remote_dir} && tar czf - . | base64 -w0 > {tmp} && stat -c %s {tmp}",
                   echo=False).strip())
    parts = []
    for i in range((size + CHUNK - 1) // CHUNK):
        parts.append(ssm(instance, f"dd if={tmp} bs={CHUNK} skip={i} count=1 2>/dev/null", echo=False).strip())
    data = base64.b64decode("".join(parts))
    os.makedirs(local_dir, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tar:
        # 우리가 만든 perf 인스턴스의 결과물만 받는다. 그래도 경로 이탈 항목은 거부한다.
        for m in tar.getmembers():
            if m.name.startswith("/") or ".." in m.name.split("/") or m.issym() or m.islnk():
                raise SystemExit(f"허용하지 않는 tar 항목: {m.name}")
        tar.extractall(local_dir)
    log(f"수집 {instance}:{remote_dir} → {os.path.relpath(local_dir, ROOT)} ({len(data)} bytes)")


def copy_file(src, src_path, dst, dst_path):
    """한 인스턴스의 파일을 다른 인스턴스로 옮긴다. 두 인스턴스 사이에는 경로가 없어(인바운드 없음)
    SSM 출력으로 받아 SSM 명령으로 나눠 올린다. 실패 이체 멱등키처럼 작은 파일용."""
    tmp = "/tmp/perf-copy.b64"
    size = int(ssm(src, f"base64 -w0 {src_path} > {tmp} && stat -c %s {tmp}", echo=False).strip())
    data = "".join(ssm(src, f"dd if={tmp} bs={CHUNK} skip={n} count=1 2>/dev/null", echo=False).strip()
                   for n in range((size + CHUNK - 1) // CHUNK))
    up = "/tmp/perf-upload.b64"
    ssm(dst, f": > {up}", echo=False)
    for n in range(0, len(data), UPLOAD_CHUNK):
        ssm(dst, f"printf '%s' '{data[n:n + UPLOAD_CHUNK]}' >> {up}", echo=False)
    ssm(dst, f"mkdir -p $(dirname {dst_path}) && base64 -d {up} > {dst_path} && wc -l < {dst_path}")
    log(f"복사 {src}:{src_path} → {dst}:{dst_path} ({len(data)} b64 bytes)")


def target_sh(i, args, **kw):
    return ssm(i["target_instance_id"], f"bash /opt/jbank/perf/ec2/target.sh {args}", **kw)


def loadgen_sh(i, args, **kw):
    return ssm(i["loadgen_instance_id"], f"bash /opt/jbank/perf/ec2/loadgen.sh {args}", **kw)


# --- 명령 --------------------------------------------------------------------

def cmd_up(_):
    log("terraform apply (perf)")
    sh(["terraform", f"-chdir={TF_DIR}", "init", "-input=false"], capture=False)
    sh(["terraform", f"-chdir={TF_DIR}", "apply", "-auto-approve", "-input=false"], capture=False)
    i = ids()
    both = [i["target_instance_id"], i["loadgen_instance_id"]]
    log("SSM 등록 대기")
    for _ in range(60):
        info = aws("ssm", "describe-instance-information", "--filters",
                   f"Key=InstanceIds,Values={','.join(both)}")["InstanceInformationList"]
        if len([x for x in info if x["PingStatus"] == "Online"]) == 2:
            break
        time.sleep(10)
    else:
        raise SystemExit("SSM 등록 시간 초과")
    for inst in both:
        ssm(inst, "for n in $(seq 1 90); do test -f /var/lib/cloud/instance/jbank-perf-ready && exit 0; sleep 10; done; exit 1")
    record_infra(i, info)


def record_infra(i, info):
    both = [i["target_instance_id"], i["loadgen_instance_id"]]
    inst = aws("ec2", "describe-instances", "--instance-ids", *both)
    credit = aws("ec2", "describe-instance-credit-specifications", "--instance-ids", i["target_instance_id"])
    sgs = aws("ec2", "describe-security-groups", "--filters", "Name=tag:Environment,Values=perf")
    lines = ["# perf 인프라 생성 조건 (REQ-01)", "", f"- 기록 시각: {dt.datetime.now().isoformat()}"]
    for r in inst["Reservations"]:
        for x in r["Instances"]:
            role = next((t["Value"] for t in x.get("Tags", []) if t["Key"] == "Role"), "?")
            lines.append(f"- {role}: {x['InstanceId']} {x['InstanceType']} AZ={x['Placement']['AvailabilityZone']} "
                         f"private={x['PrivateIpAddress']} profile={x.get('IamInstanceProfile', {}).get('Arn')}")
    for c in credit["InstanceCreditSpecifications"]:
        lines.append(f"- 대상 CPU 크레딧: {c['CpuCredits']}")
    for x in info:
        lines.append(f"- SSM {x['InstanceId']}: {x['PingStatus']}")
    open_world = []
    for sg in sgs["SecurityGroups"]:
        for perm in sg["IpPermissions"]:
            src = [r["CidrIp"] for r in perm.get("IpRanges", [])] + \
                  [p["GroupId"] for p in perm.get("UserIdGroupPairs", [])]
            lines.append(f"- SG {sg['GroupName']} 인바운드 tcp {perm.get('FromPort')} ← {', '.join(src)}")
            open_world += [r for r in src if r == "0.0.0.0/0"]
    lines.append(f"- 0.0.0.0/0 인바운드: {len(open_world)}건")
    role = aws("iam", "list-attached-role-policies", "--role-name", "jbank-perf-instance")
    inline = aws("iam", "list-role-policies", "--role-name", "jbank-perf-instance")
    lines.append(f"- 인스턴스 역할 관리 정책: {[p['PolicyArn'] for p in role['AttachedPolicies']]}, "
                 f"인라인 정책: {inline['PolicyNames']}")
    path = os.path.join(RESULTS, "env/infra.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    log(f"기록 {os.path.relpath(path, ROOT)}")


def cmd_setup(_):
    i = ids()
    sha = sh(["git", "rev-parse", f"origin/{GIT_REF}"]).stdout.strip()
    for inst in (i["target_instance_id"], i["loadgen_instance_id"]):
        ssm(inst, f"runuser -u ec2-user -- git -C /opt/jbank fetch -q origin {GIT_REF} && "
                  f"runuser -u ec2-user -- git -C /opt/jbank checkout -q -f {sha} && "
                  f"git -c safe.directory='*' -C /opt/jbank rev-parse HEAD")
    target_sh(i, "setup")
    target_sh(i, "environment")
    loadgen_sh(i, f"setup {i['target_private_ip']}")
    fetch_env(i)


def fetch_env(i):
    fetch_dir(i["target_instance_id"], "/opt/perf-out/env", os.path.join(RESULTS, "env/target"))
    fetch_dir(i["loadgen_instance_id"], "/opt/perf-out/env", os.path.join(RESULTS, "env/loadgen"))


def cmd_prepare(_):
    i = ids()
    target_sh(i, "seed")
    loadgen_sh(i, f"prepare {i['target_private_ip']}")
    target_sh(i, "baseline")
    target_sh(i, "snapshot")
    target_sh(i, "restore")
    loadgen_sh(i, f"verify-login {i['target_private_ip']}")
    fetch_env(i)


def k6_run(i, run, script, monitor, extra="", wait=True):
    cmd = f"bash /opt/jbank/perf/ec2/loadgen.sh k6 {run} {script} {monitor} {extra}".rstrip()
    if wait:
        return ssm(i["loadgen_instance_id"], cmd)
    log(f"ssm(async) {i['loadgen_instance_id']}: {cmd}")
    return ssm_send(i["loadgen_instance_id"], cmd)


def epoch_of(i, run, name):
    iso = ssm(i["loadgen_instance_id"], f"cat /opt/perf-out/{run}/{name}", echo=False).strip()
    return dt.datetime.strptime(iso, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc).timestamp()


def run_batches(i, run, tag):
    """S5 배치 3종을 순서대로 실행하고 (라벨, 시작, 끝) 구간을 돌려준다."""
    windows = []
    for job, dated in (("ctrDetectionJob", True), ("fdsDetectionJob", True), ("ledgerReconciliationJob", False)):
        out = target_sh(i, f"batch {run} {job} {run}-{tag}-{job} {'--run-date' if dated else ''}".rstrip(),
                        timeout=2400)
        fields = dict(kv.split("=", 1) for kv in out.strip().splitlines()[-1].split()[1:])
        windows.append((f"{tag}-{job}", float(fields["start"]), float(fields["end"])))
    return windows


def local_mkdir(path):
    os.makedirs(path, exist_ok=True)
    return path


def recover_target(i):
    """대상이 응답하지 않을 때(메모리 스래싱 등) 재부팅하고 SSM·스택이 돌아올 때까지 기다린다."""
    inst = i["target_instance_id"]
    log(f"대상 재부팅 {inst}")
    aws("ec2", "reboot-instances", "--instance-ids", inst)
    time.sleep(60)
    for _ in range(60):
        info = aws("ssm", "describe-instance-information", "--filters",
                   f"Key=InstanceIds,Values={inst}")["InstanceInformationList"]
        if info and info[0]["PingStatus"] == "Online":
            break
        time.sleep(10)
    else:
        raise SystemExit("재부팅 후 SSM 등록 시간 초과")
    ssm(inst, "cd /opt/jbank/infra/compose/perf && docker compose -f docker-compose.target.yml --env-file .env up -d "
              "&& for n in $(seq 1 60); do docker compose -f docker-compose.target.yml exec -T api "
              "wget -qO- http://localhost:9095/actuator/health/readiness >/dev/null && exit 0; sleep 5; done; exit 1")


def cloudwatch_credit(i, start, end):
    res = {}
    for metric in ("CPUCreditBalance", "CPUSurplusCreditsCharged", "CPUSurplusCreditBalance"):
        data = aws("cloudwatch", "get-metric-statistics", "--namespace", "AWS/EC2", "--metric-name", metric,
                   "--dimensions", f"Name=InstanceId,Value={i['target_instance_id']}",
                   "--start-time", dt.datetime.fromtimestamp(start - 300, dt.timezone.utc).isoformat(),
                   "--end-time", dt.datetime.fromtimestamp(end + 300, dt.timezone.utc).isoformat(),
                   "--period", "300", "--statistics", "Minimum", "Maximum")
        pts = sorted(data["Datapoints"], key=lambda p: p["Timestamp"])
        res[metric] = [(p["Timestamp"], p["Minimum"], p["Maximum"]) for p in pts]
    return res


def cmd_run(args):
    i = ids()
    scen, rnd = args.scenario, args.round
    run = f"{scen}-r{rnd}" if not args.dry else "dryrun"
    # 드라이런은 기준선에서 빼므로 실행마다 별도 폴더에 받는다(이전 드라이런 파일과 섞이지 않게).
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    local = os.path.join(RESULTS, f"dryrun/{stamp}" if args.dry else f"{scen}/r{rnd}")
    kst_now = dt.datetime.now(dt.timezone(dt.timedelta(hours=9)))
    log(f"=== {run} 시작 (KST {kst_now.isoformat(timespec='seconds')})")

    target_sh(i, f"clean {run} {run}-noload")
    target_sh(i, "restore")
    k6_run(i, f"{run}-warmup", "s1-mixed.js", "none", "MODE=constant RATE=20 DURATION=2m")
    hot_id = ssm(i["loadgen_instance_id"], "bash /opt/jbank/perf/ec2/loadgen.sh hot-id", echo=False).strip()
    try:
        target_sh(i, f"boundary {run} {hot_id}")
    except SystemExit as e:
        # 프록시 수집 확인에 실패하면 회차를 시작하지 않는다. 회차 수에 넣지 않고 1회만 다시 시도한다.
        log(f"경계 확인 실패, 회차 미시작 — 1회 재시도: {str(e)[:200]}")
        target_sh(i, "restore")
        k6_run(i, f"{run}-warmup", "s1-mixed.js", "none", "MODE=constant RATE=20 DURATION=2m")
        target_sh(i, f"boundary {run} {hot_id}")
    target_sh(i, f"sampler-start {run}")

    windows = []
    if args.dry:
        k6_run(i, run, "s1-mixed.js", "none", "MODE=constant RATE=10 DURATION=20s")
        mode = "warmup"
    elif scen in ("s1", "s2"):
        k6_run(i, run, "s1-mixed.js" if scen == "s1" else "s2-hot-account.js", scen, "MODE=ramp" if scen == "s1" else "")
        mode = scen
    elif scen == "s3":
        k6_run(i, run, "s3-spike.js", "none")
        mode = "s3"
    else:  # s5
        cmd_id = k6_run(i, run, "s1-mixed.js", "none", f"MODE=constant RATE={args.rate} DURATION=20m", wait=False)
        time.sleep(5 * 60)
        try:
            windows = run_batches(i, run, "load")
        except SystemExit as e:
            # 부하 중 배치가 대상을 멈추게 한 경우(메모리 스래싱). 측정 결과로 기록하고 대상을 되살려 수집을 잇는다.
            log(f"부하 중 배치 실패·대상 무응답: {str(e)[:300]}")
            with open(os.path.join(local_mkdir(local), "incident.md"), "a", encoding="utf-8") as f:
                f.write(f"- {dt.datetime.now().isoformat(timespec='seconds')} 부하 중 배치 실패·대상 무응답: {str(e)[:1000]}\n")
            ssm_wait(i["loadgen_instance_id"], cmd_id)
            recover_target(i)
        ssm_wait(i["loadgen_instance_id"], cmd_id)
        mode = "s5"

    target_sh(i, f"sampler-stop {run}")
    start, end = epoch_of(i, run, "k6-start.txt"), epoch_of(i, run, "k6-end.txt")
    win_arg = ",".join(f"{label}:{s}:{e}" for label, s, e in windows)
    loadgen_sh(i, f"analyze {run} {mode} {win_arg}".rstrip())
    loadgen_sh(i, f"export {run}")
    target_sh(i, f"integrity {run}")
    copy_file(i["loadgen_instance_id"], f"/opt/perf-out/{run}/failed-transfers.txt",
              i["target_instance_id"], f"/opt/perf-out/{run}/failed-transfers.txt")
    target_sh(i, f"failed-keys {run}")
    target_sh(i, "stop-api")
    target_sh(i, f"batch {run} ledgerReconciliationJob {run}-recon", timeout=2400)
    since = dt.datetime.fromtimestamp(start - 180, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    target_sh(i, f"logs {run} {since}")

    if scen == "s5" and not args.dry:
        # 부하 없음 비교: 복원 → 같은 요청률 5분 → 부하 중지 → 배치 3종. 이 구간 느린 쿼리로 EXPLAIN.
        nl = f"{run}-noload"
        target_sh(i, "restore")
        k6_run(i, nl, "s1-mixed.js", "none", f"MODE=constant RATE={args.rate} DURATION=5m")
        nl_windows = run_batches(i, nl, "noload")
        s_iso = dt.datetime.fromtimestamp(nl_windows[0][1] - 1, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        e_iso = dt.datetime.fromtimestamp(nl_windows[-1][2] + 1, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        target_sh(i, f"explain {nl} {s_iso} {e_iso}")
        fetch_dir(i["target_instance_id"], f"/opt/perf-out/{nl}", os.path.join(local, "noload/target"))

    fetch_dir(i["loadgen_instance_id"], f"/opt/perf-out/{run}", os.path.join(local, "loadgen"))
    fetch_dir(i["loadgen_instance_id"], f"/opt/perf-out/{run}-warmup", os.path.join(local, "warmup"))
    fetch_dir(i["target_instance_id"], f"/opt/perf-out/{run}", os.path.join(local, "target"))
    credit = cloudwatch_credit(i, start, end)
    loadgen_type = aws("ec2", "describe-instances", "--instance-ids", i["loadgen_instance_id"])[
        "Reservations"][0]["Instances"][0]["InstanceType"]
    write_round_docs(local, run, scen, args, start, end, kst_now, credit, loadgen_type)
    check_artifacts(local, scen, args.dry)


def k6_success_201(summary_path):
    summary = json.load(open(summary_path))
    return sum(v.get("passes", 0) for k, v in summary.get("root_group", {}).get("checks", {}).items()
               if k.endswith(" 201"))


def k6_check(summary_path, name, field="passes"):
    summary = json.load(open(summary_path))
    return summary.get("root_group", {}).get("checks", {}).get(name, {}).get(field, 0)


def k6_fail_201(summary_path):
    summary = json.load(open(summary_path))
    return sum(v.get("fails", 0) for k, v in summary.get("root_group", {}).get("checks", {}).items()
               if k.endswith(" 201"))


def write_round_docs(local, run, scen, args, start, end, kst_now, credit, loadgen_type):
    write_integrity(local, run)
    write_environment(local, run, scen, args, start, end, kst_now, credit, loadgen_type)


def write_integrity(local, run):
    target = dict(line.split("=", 1) for line in
                  open(os.path.join(local, "target/integrity-target.txt"), encoding="utf-8").read().split()
                  if "=" in line)
    ok201 = k6_success_201(os.path.join(local, "loadgen/k6-summary.json"))
    fail201 = k6_fail_201(os.path.join(local, "loadgen/k6-summary.json"))
    hot201 = k6_check(os.path.join(local, "loadgen/k6-summary.json"), "hot-transfer 201")
    fk = dict(line.split("=", 1) for line in
              open(os.path.join(local, "target/failed-keys-target.txt"), encoding="utf-8").read().split()
              if "=" in line)
    completed = int(target["new_transfer_completed"])
    fk_total, fk_committed = int(fk.get("failed_keys_total", -1)), int(fk.get("failed_keys_committed", 0))
    in_flight = completed - ok201 - fk_committed
    by_status = ", ".join(f"{k[len('failed_status_'):-len('_total')]}: {v}건 중 반영 {fk.get(k[:-len('_total')] + '_committed', '0')}"
                          for k, v in sorted(fk.items()) if k.startswith("failed_status_") and k.endswith("_total"))
    rows = [
        ("k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수", f"{ok201} vs {completed}",
         "같음" if ok201 == completed else f"차이 {completed - ok201} (아래 두 행으로 나뉨)"),
        ("실패 이체 멱등키 기록 수 vs k6 이체 체크 실패 수(자기 검증)", f"{fk_total} vs {fail201}",
         "같음" if fk_total == fail201 else
         ("키 기록 유실" if fk_total < fail201 else
          "키가 더 많음: k6 중단(SIGINT) 순간 끊긴 요청은 키가 남지만 체크 집계에는 들어가지 않는다")),
        ("실패 응답인데 실제 반영된 이체(멱등키 대조)", f"{fk_committed} ({by_status or '실패 없음'})", ""),
        ("k6 중단 순간 처리 중이던 요청의 반영(잔차 = 새 완료 − 201 − 실패 중 반영)", str(in_flight), ""),
        ("새 원장 행 수 vs 2 × 새 이체 수", f"{target['new_ledger_rows']} vs {2 * completed}",
         "같음" if int(target["new_ledger_rows"]) == 2 * completed else "다름"),
        ("새 원장 차변 합 = 대변 합", f"{target['new_ledger_debit']} / {target['new_ledger_credit']}",
         "같음" if target["new_ledger_debit"] == target["new_ledger_credit"] else "다름"),
        ("핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합(DB)", f"{target['hot_balance_delta']} vs {target['hot_completed_amount_sum']}",
         "같음" if float(target["hot_balance_delta"]) == float(target["hot_completed_amount_sum"]) else "다름"),
        ("핫 계좌 잔액 증가분 vs k6 hot-transfer 201 × 1,000원", f"{target['hot_balance_delta']} vs {hot201 * 1000}",
         "같음" if float(target["hot_balance_delta"]) == hot201 * 1000 else "다름(실패 응답 반영·중단 잔차 포함)"),
        ("기준 대사 대비 새 불일치 계좌", target["new_mismatch_accounts"], "0" if target["new_mismatch_accounts"] == "0" else "원인 분석 대상"),
        ("기준 불일치 계좌 중 증가분 불일치", target["baseline_account_delta_mismatch"], ""),
        ("전체 차변·대변 증가분", f"{target['global_debit_delta']} / {target['global_credit_delta']}",
         "같음" if target["global_debit_delta"] == target["global_credit_delta"] else "다름"),
        ("경계 이후 멱등키 중복", target["duplicate_idempotency_keys"], ""),
        ("경계 이후 COMPLETED 아닌 이체", target["new_transfer_other_status"], ""),
    ]
    recon = open(os.path.join(local, "target/batch.txt"), encoding="utf-8").read()
    with open(os.path.join(local, "integrity.md"), "w", encoding="utf-8") as f:
        f.write(f"# {run} 정합성 (REQ-10)\n\n| 항목 | 값 | 판정 |\n| --- | --- | --- |\n")
        for r in rows:
            f.write(f"| {r[0]} | {r[1]} | {r[2]} |\n")
        f.write(f"\n대사 잡 실행 기록:\n\n```\n{recon}```\n")


def write_environment(local, run, scen, args, start, end, kst_now, credit, loadgen_type):
    with open(os.path.join(local, "environment.md"), "w", encoding="utf-8") as f:
        f.write(f"# {run} 측정 조건 (PERF-01)\n\n")
        f.write(f"- 시나리오: {scen}, 회차: {args.round}, S5 요청률: {args.rate}\n")
        f.write(f"- 부하 발생기 인스턴스 유형: {loadgen_type}\n")
        f.write(f"- 로컬 저장소 커밋: {sh(['git', 'rev-parse', 'HEAD']).stdout.strip()}\n")
        f.write(f"- 시작(KST): {kst_now.isoformat(timespec='seconds')}\n")
        f.write(f"- k6 구간(UTC epoch): {start} ~ {end}\n")
        f.write("- 회차 시작 조건: PG 복사본 복원 → OS 페이지 캐시 비움 → 스택 재기동(redis 새 컨테이너) → readiness → 예열(초당 20건 2분, 별도 k6 실행) → 측정 경계 기록\n")
        f.write("- 대상·이미지 digest: ../../env/target/environment-target.md, 인프라: ../../env/infra.md\n")
        f.write(f"- CloudWatch 크레딧(5분 단위 min/max): {json.dumps(credit, default=str)}\n")


REQUIRED = ["loadgen/k6-summary.json", "loadgen/analysis.json", "loadgen/prometheus/query_range.json.gz",
            "loadgen/k6-start.txt", "loadgen/k6-end.txt", "loadgen/prometheus/gaps.txt", "target/pg_stat_activity.csv",
            "target/integrity-target.txt", "target/failed-keys-target.txt", "loadgen/failed-transfers.txt",
            "target/postgres-excerpt.log", "target/api-errors-excerpt.log",
            "target/log-counts.txt", "target/boundary.env", "integrity.md", "environment.md"]


def check_artifacts(local, scen, dry):
    need = list(REQUIRED) + (["loadgen/monitor.log"] if scen in ("s1", "s2") and not dry else []) \
        + (["noload/target/batch-explain.txt", "target/batch.txt", "target/batch-counts.txt",
            "noload/target/batch-counts.txt"] if scen == "s5" and not dry else [])
    missing = [p for p in need if not os.path.exists(os.path.join(local, p))]
    gaps_path = os.path.join(local, "loadgen/prometheus/gaps.txt")
    invalid = "unknown"
    if os.path.exists(gaps_path):
        invalid = next((ln.split("=", 1)[1].strip() for ln in open(gaps_path, encoding="utf-8")
                        if ln.startswith("invalid=")), "unknown")
    with open(os.path.join(local, "artifacts.txt"), "w", encoding="utf-8") as f:
        f.write("missing=" + (",".join(missing) or "none") + "\n")
        f.write(f"metrics_gap_invalid={invalid}\n")
    log(f"check-artifacts {os.path.relpath(local, ROOT)}: missing={missing or 'none'} metrics_gap_invalid={invalid}")
    if missing:
        raise SystemExit("필수 결과물 누락")


def cmd_down(_):
    log("terraform destroy (perf)")
    sh(["terraform", f"-chdir={TF_DIR}", "destroy", "-auto-approve", "-input=false"], capture=False)
    state = sh(["terraform", f"-chdir={TF_DIR}", "state", "list"], check=False).stdout.strip()
    res = aws("resourcegroupstaggingapi", "get-resources", "--tag-filters", "Key=Environment,Values=perf")
    live = [r["ResourceARN"] for r in res["ResourceTagMappingList"]]
    if live:
        # 종료된 인스턴스는 한동안 태그 조회에 남는다. 상태를 확인해 종료된 것은 뺀다.
        inst = [a.split("/")[-1] for a in live if ":instance/" in a]
        if inst:
            desc = aws("ec2", "describe-instances", "--instance-ids", *inst)
            dead = {x["InstanceId"] for r in desc["Reservations"] for x in r["Instances"]
                    if x["State"]["Name"] in ("terminated", "shutting-down")}
            live = [a for a in live if a.split("/")[-1] not in dead]
    with open(os.path.join(RESULTS, "env/destroy.md"), "a", encoding="utf-8") as f:
        f.write(f"- {dt.datetime.now().isoformat(timespec='seconds')} destroy 후 state list: "
                f"{state or '(비어 있음)'}; 종료 상태가 아닌 perf 태그 리소스: {live or '0건'}\n")
    log(f"destroy 확인: state={'비어 있음' if not state else state}, live={live or 0}")


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("up", "setup", "prepare", "down", "recover"):
        sub.add_parser(name)
    g = sub.add_parser("regen-integrity")  # 받아 둔 원자료로 회차 정합성 표를 다시 만든다(원격 접속 없음)
    g.add_argument("dirs", nargs="+")
    r = sub.add_parser("run")
    r.add_argument("scenario", choices=["s1", "s2", "s3", "s5"])
    r.add_argument("round", type=int)
    r.add_argument("--rate", type=int, default=0)
    d = sub.add_parser("dry-run")
    args = p.parse_args()
    log(f"$ run-ec2.py {' '.join(sys.argv[1:])}")
    if args.cmd == "regen-integrity":
        for d in args.dirs:
            write_integrity(d, os.path.basename(os.path.dirname(d)) + "-" + os.path.basename(d))
        return
    if args.cmd == "dry-run":
        args.scenario, args.round, args.rate, args.dry = "s1", 0, 0, True
        return cmd_run(args)
    if args.cmd == "run":
        args.dry = False
        if args.scenario == "s5" and args.rate <= 0:
            sys.exit("S5는 --rate(S1 최대 지속 가능 요청률 최솟값의 70%)가 필요하다")
        return cmd_run(args)
    {"up": cmd_up, "setup": cmd_setup, "prepare": cmd_prepare, "down": cmd_down,
     "recover": lambda _: recover_target(ids())}[args.cmd](args)


if __name__ == "__main__":
    main()
