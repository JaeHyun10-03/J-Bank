#!/usr/bin/env python3
"""perf 대상 전용 지표 프록시. /actuator/prometheus가 인증을 요구해(SecurityConfig 공개 경로에 없음)
Prometheus가 직접 긁으면 401이 난다. 앱 코드를 바꾸지 않고 측정하기 위해, perf 전용 계정
(perf-metrics)으로 로그인한 쿠키를 붙여 대신 가져와 :9091/metrics로 내놓는다.

  - 계정이 없으면 가입 API로 만든다(비밀번호는 대상 .env의 METRICS_PASSWORD, 인스턴스 안에서 생성).
  - 지표는 관리 포트(9095, 별도 커넥터)에서 가져오지만 로그인은 본 포트(8080)에만 있어, 본 포트가
    포화되면 로그인이 실패할 수 있다. 그래서 로그인은 수집 요청 경로에서 하지 않는다. 백그라운드
    스레드가 쿠키 발급 5분 뒤부터 30초 간격으로 재로그인을 시도하고, 성공할 때만 쿠키를 바꾼다.
    실패해도 만료(15분) 전인 기존 쿠키를 계속 쓴다.
  - 매 회차 측정 경계에서 target.sh가 이 컨테이너를 재시작해 새 쿠키로 측정을 시작한다.
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
REFRESH_AFTER = int(os.environ.get("REFRESH_AFTER_SECONDS", "300"))  # 쿠키 발급 후 재로그인 시도 시작(초). 검증용으로만 바꾼다
RETRY_EVERY = 30
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
    try:
        status, headers, _ = call("POST", "/api/v1/auth/login", {"loginId": LOGIN_ID, "password": PASSWORD})
    except OSError:
        return None
    if status != 200:
        return None
    for raw in headers.get_all("Set-Cookie") or []:
        if raw.startswith("access_token="):
            return raw.split(";", 1)[0]
    return None


def register():
    try:
        call("POST", "/api/v1/customers", {
            "name": LOGIN_ID, "loginId": LOGIN_ID, "password": PASSWORD,
            "residentRegNo": "9901019999999", "birthDate": "1999-01-01", "phone": "010-9999-9999",
            "address": "서울", "occupation": "회사원", "identityVerificationMethod": "FACE_TO_FACE",
            "transactionPurpose": "급여", "fundSource": "근로소득",
        })
    except OSError:
        pass


def refresher():
    """쿠키가 없으면 곧바로, 있으면 발급 5분 뒤부터 재로그인을 시도한다. 성공할 때만 교체한다."""
    while True:
        if not state["cookie"] or time.time() - state["at"] > REFRESH_AFTER:
            c = login()
            if c is None and not state["cookie"]:
                register()
                c = login()
            if c:
                state["cookie"], state["at"] = c, time.time()
        time.sleep(5 if not state["cookie"] else RETRY_EVERY)


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        if not state["cookie"]:
            status, headers, body = 503, None, b"no session yet"
        else:
            try:
                status, headers, body = call("GET", METRICS_URL, cookie=state["cookie"])
            except OSError as e:
                status, headers, body = 502, None, str(e).encode()
        self.send_response(status)
        self.send_header("Content-Type", headers.get("Content-Type", "text/plain") if headers else "text/plain")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    threading.Thread(target=refresher, daemon=True).start()
    http.server.ThreadingHTTPServer(("0.0.0.0", 9091), Handler).serve_forever()
