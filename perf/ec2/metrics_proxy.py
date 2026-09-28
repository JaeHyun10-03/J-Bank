#!/usr/bin/env python3
"""perf 대상 전용 지표 프록시. /actuator/prometheus가 인증을 요구해(SecurityConfig 공개 경로에 없음)
Prometheus가 직접 긁으면 401이 난다. 앱 코드를 바꾸지 않고 측정하기 위해, perf 전용 계정
(perf-metrics)으로 로그인한 쿠키를 붙여 대신 가져와 :9091/metrics로 내놓는다.

  - 계정이 없으면 가입 API로 만든다(비밀번호는 대상 .env의 METRICS_PASSWORD, 인스턴스 안에서 생성).
  - 액세스 토큰 TTL이 15분이라 10분마다 다시 로그인한다. 401을 받으면 즉시 다시 로그인한다.
운영과 다른 점으로 perf/README.md에 기록한다. 표준 라이브러리만 쓴다.
"""
import http.server
import json
import os
import threading
import time
import urllib.error
import urllib.request

API = os.environ.get("API_URL", "http://api:8080")
# 지표는 관리 포트(9095, 별도 커넥터)에서 가져온다. 로그인은 본 포트에만 있다.
METRICS_URL = os.environ.get("METRICS_URL", "http://api:9095/actuator/prometheus")
LOGIN_ID = "perf-metrics"
PASSWORD = os.environ["METRICS_PASSWORD"]
lock = threading.Lock()
state = {"cookie": None, "at": 0.0}


def call(method, path, body=None, cookie=None):
    url = path if path.startswith("http") else API + path
    req = urllib.request.Request(url, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, res.headers, res.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read()


def login():
    status, headers, _ = call("POST", "/api/v1/auth/login", {"loginId": LOGIN_ID, "password": PASSWORD})
    if status != 200:
        return None
    for raw in headers.get_all("Set-Cookie") or []:
        if raw.startswith("access_token="):
            return raw.split(";", 1)[0]
    return None


def register():
    call("POST", "/api/v1/customers", {
        "name": LOGIN_ID, "loginId": LOGIN_ID, "password": PASSWORD,
        "residentRegNo": "9901019999999", "birthDate": "1999-01-01", "phone": "010-9999-9999",
        "address": "서울", "occupation": "회사원", "identityVerificationMethod": "FACE_TO_FACE",
        "transactionPurpose": "급여", "fundSource": "근로소득",
    })


def cookie(force=False):
    with lock:
        if force or not state["cookie"] or time.time() - state["at"] > 600:
            c = login()
            if c is None:
                register()
                c = login()
            state["cookie"], state["at"] = c, time.time()
        return state["cookie"]


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        try:
            status, headers, body = call("GET", METRICS_URL, cookie=cookie())
            if status == 401:
                status, headers, body = call("GET", METRICS_URL, cookie=cookie(force=True))
        except OSError as e:
            status, headers, body = 502, None, str(e).encode()
        self.send_response(status)
        self.send_header("Content-Type", headers.get("Content-Type", "text/plain") if headers else "text/plain")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    http.server.ThreadingHTTPServer(("0.0.0.0", 9091), Handler).serve_forever()
