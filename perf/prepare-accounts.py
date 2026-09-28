#!/usr/bin/env python3
"""EC2 부하 테스트용 고객·계좌 준비(perf/README.md "EC2 부하 테스트" 절).

seed-10m.sql의 시드 고객은 암호화 컬럼이 placeholder라 로그인할 수 없다. 로그인이 필요한
부하 주체는 가입 API로 만들어 perf 전용 PII 키로 암호화되게 한다.

  - 발신 고객 N명(기본 200): 계좌 1개 + 입금 1억 원. 이체 발신자이자 본인 계좌 조회 주체.
  - 로그인 전용 고객 M명(기본 20): 계좌 1개. S1 로그인 요청, S2·S3 병행/탐침 잔액 조회 주체.
  - 핫 계좌 1개: 별도 고객 소유. S2의 수신 계좌.

결과는 k6가 읽는 JSON(--out)으로 저장한다. 비밀번호는 모든 perf 고객에 같은 값을 쓰고
PERF_PASSWORD 환경변수로 받는다(perf 환경 전용 테스트 값, 저장소·결과물에 남기지 않는다).
표준 라이브러리만 쓴다 — 부하 발생기(AL2023)에 추가 설치 없이 돈다.

사용법: BASE_URL=https://<대상> PERF_PASSWORD=... python3 perf/prepare-accounts.py --out accounts.json
"""
import argparse
import http.cookiejar
import json
import os
import ssl
import sys
import urllib.error
import urllib.request
import uuid

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")
PASSWORD = os.environ["PERF_PASSWORD"]
# 대상은 내부 CA(tls internal) 자체 서명 인증서라 검증을 끈다. perf 환경 안에서만 쓴다.
SSL_CTX = ssl._create_unverified_context()


def request(method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE_URL + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, context=SSL_CTX, timeout=30) as res:
            return res.status, res.headers, json.loads(res.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, e.headers, json.loads(e.read() or b"{}")


def register(login_id, seq):
    status, _, body = request("POST", "/api/v1/customers", {
        "name": login_id,
        "loginId": login_id,
        "password": PASSWORD,
        # 주민번호는 중복만 검사한다(형식 검증 없음). perf 전용 가짜 번호.
        "residentRegNo": "990101" + str(3000000 + seq),
        "birthDate": "1999-01-01",
        "phone": "010-8%03d-%04d" % (seq // 10000, seq % 10000),
        "address": "서울",
        "occupation": "회사원",
        "identityVerificationMethod": "FACE_TO_FACE",
        "transactionPurpose": "급여",
        "fundSource": "근로소득",
    })
    if status not in (200, 201):
        raise RuntimeError(f"가입 실패 {login_id}: {status} {body}")


def login(login_id):
    status, headers, body = request("POST", "/api/v1/auth/login",
                                    {"loginId": login_id, "password": PASSWORD})
    if status != 200:
        raise RuntimeError(f"로그인 실패 {login_id}: {status} {body}")
    jar = {}
    for raw in headers.get_all("Set-Cookie") or []:
        name, _, rest = raw.partition("=")
        jar[name.strip()] = rest.split(";", 1)[0]
    auth = {
        "Cookie": f"access_token={jar['access_token']}; XSRF-TOKEN={jar['XSRF-TOKEN']}",
        "X-CSRF-TOKEN": jar["XSRF-TOKEN"],
    }
    return auth, str(body["data"]["customerId"])


def open_account(auth):
    status, _, body = request("POST", "/api/v1/accounts",
                              {"productType": "CHECKING", "initialDeposit": 0}, auth)
    if status not in (200, 201):
        raise RuntimeError(f"계좌 개설 실패: {status} {body}")
    return str(body["data"]["accountId"]), body["data"]["accountNumber"]


def deposit(auth, account_id, amount):
    headers = dict(auth, **{"Idempotency-Key": str(uuid.uuid4())})
    status, _, body = request("POST", f"/api/v1/accounts/{account_id}/deposit",
                              {"amount": amount, "channel": "BRANCH"}, headers)
    if status not in (200, 201):
        raise RuntimeError(f"입금 실패 {account_id}: {status} {body}")


def make_customer(login_id, seq, deposit_amount=None):
    register(login_id, seq)
    auth, customer_id = login(login_id)
    account_id, account_number = open_account(auth)
    if deposit_amount:
        deposit(auth, account_id, deposit_amount)
    return {"loginId": login_id, "customerId": customer_id,
            "accountId": account_id, "accountNumber": account_number}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--senders", type=int, default=200)
    parser.add_argument("--login-users", type=int, default=20)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    senders = [make_customer(f"perf-sender-{i:03d}", i, "100000000.00")
               for i in range(1, args.senders + 1)]
    login_users = [make_customer(f"perf-login-{i:02d}", 1000 + i)
                   for i in range(1, args.login_users + 1)]
    hot = make_customer("perf-hot-owner", 2000)

    result = {"senders": senders, "loginUsers": login_users, "hot": hot}
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(f"senders={len(senders)} loginUsers={len(login_users)} hot={hot['accountNumber']}")


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print(e, file=sys.stderr)
        sys.exit(1)
