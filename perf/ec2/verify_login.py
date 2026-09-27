#!/usr/bin/env python3
"""복원 검증(준비 단계 1회): 복사본에서 복원한 상태로 perf 고객 로그인과 핫 계좌 조회가 되는지 본다.

발신 고객 첫 명·로그인 전용 고객 첫 명으로 로그인하고, 핫 계좌 소유자로 핫 계좌 잔액을 조회한다.
사용법: BASE_URL=https://<대상> PERF_PASSWORD=... python3 verify_login.py accounts.json
"""
import json
import os
import ssl
import sys
import urllib.request

BASE_URL = os.environ["BASE_URL"]
PASSWORD = os.environ["PERF_PASSWORD"]
CTX = ssl._create_unverified_context()


def call(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE_URL + path, method=method,
                                 data=json.dumps(body).encode() if body else None)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, context=CTX, timeout=30) as res:
        return res.status, res.headers, json.loads(res.read() or b"{}")


def login(login_id):
    status, headers, _ = call("POST", "/api/v1/auth/login", {"loginId": login_id, "password": PASSWORD})
    cookies = dict(c.split(";", 1)[0].split("=", 1) for c in headers.get_all("Set-Cookie"))
    return status, {"Cookie": f"access_token={cookies['access_token']}"}


accounts = json.load(open(sys.argv[1]))
checks = []
for user in (accounts["senders"][0], accounts["loginUsers"][0]):
    status, _ = login(user["loginId"])
    checks.append((f"login {user['loginId']}", status))
hot = accounts["hot"]
_, auth = login(hot["loginId"])
status, _, body = call("GET", f"/api/v1/accounts/{hot['accountId']}/balance", headers=auth)
checks.append((f"hot balance {hot['accountNumber']}", status))
for name, status in checks:
    print(f"{name}: {status}")
sys.exit(0 if all(s == 200 for _, s in checks) else 1)
