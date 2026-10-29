# 로컬 통합 확인 (완료 기준 3, REQ-01·03~08·10·11, OPS-04·06·07, SEC-05·07)

환경: macOS OrbStack(Docker 29.2.1, 메모리 4GB), 브랜치 feat/observability-logs-alerts 커밋 5419093, `docker compose --profile core --profile api --profile observability`.
테스트 고객 2명은 로컬 api에 가입 API로 만들었고 비밀번호는 실행마다 무작위로 만들어 출력·저장하지 않았다.

## 1차 실행 (e2e.py, 10:38~11:02Z)

결과: 전체 22개 검사 중 실패 2건, 마지막 단계에서 스크립트 오류(exit=1). 원본 출력 그대로:

```text
[10:38:51Z +0s] 0. 사전 조건
[10:38:52Z +0s] PASS 사전 조건 up·pg_up·알림 inactive up={'jbank-api': '1', 'postgres': '1'} pg_up=['1'] active=[]
[10:38:52Z +0s] 1. 테스트 고객 2명 가입·로그인·계좌 개설·입금 후 이체 1건
[10:38:59Z +7s] PASS 인증된 이체 성공 status=201
[10:39:34Z +42s] PASS 규칙 지표 존재(이체 URI·락 대기·교착 카운터) transfers=[('/api/v1/transfers', '201', '1')] lock_wait_methods=['findByIdForUpdate', 'lockForBalanceUpdate', 'lockReceiverByAccountNumber', 'lockSenderByAccountNumber'] deadlocks=['0']
[10:39:34Z +42s] 2. Loki 컨테이너 이름 라벨 조회
[10:39:34Z +42s] PASS Loki api·postgres 로그 조회 containers=['compose-alertmanager-1', 'compose-alloy-1', 'compose-api-1', 'compose-grafana-1', 'compose-loki-1', 'compose-mailpit-1', 'compose-postgres-1', 'compose-postgres-exporter-1', 'compose-prometheus-1', 'compose-redis-1', 'compose-socket-proxy-1'] api_lines=50 postgres_lines=11
[10:39:34Z +42s] 3. psql 두 세션 교착 + 1초 넘는 락 대기
[10:39:38Z +46s] PASS psql 교착 발생 stderr=['', 'ERROR:  deadlock detected\nDETAIL:  Process 1104 waits for ShareLock on transacti']
[10:39:52Z +60s] PASS Loki 교착 로그·DETAIL ['2026-10-29 10:39:38.705 UTC [1104] ERROR:  deadlock detected'] ['2026-10-29 10:39:40.452 UTC [1127] DETAIL:  Process holding the lock: 1120. Wait queue: 1127.', '2026-10-29 10:39:38.705 UTC [1104] DETAIL:  Process 1104 waits for ShareLock on transaction 5594; blocked by process 1105.']
[10:39:52Z +60s] PASS Loki 락 대기 로그(still waiting for) ['2026-10-29 10:39:40.452 UTC [1127] LOG:  process 1127 still waiting for ShareLock on transaction 5595 after 1003.653 ms']
[10:39:52Z +60s] 4. 교착 알림 firing → Grafana 조회 → 발생 메일
[10:40:22Z +90s] PASS 교착 알림 firing 
[10:40:24Z +92s] PASS Grafana(Alertmanager 데이터소스)에서 발생 중 알림 조회 ['DeadlockDetected']
[10:40:55Z +123s] PASS 교착 발생 메일 도착 
[10:40:55Z +123s] PASS 메일 내용 DeadlockDetected FIRING  제목='[FIRING:1] DeadlockDetected (16384 jbank postgres-exporter:9187 postgres critical)'
[10:40:55Z +123s] 5. api 2분 넘게 정지 → 다운 메일, 재기동 → 해제 메일
[10:43:54Z +302s] PASS api 다운 메일 도착 
[10:43:54Z +302s] PASS 메일 내용 TargetDown FIRING api 제목='[FIRING:1] TargetDown api (api:9095 jbank-api critical)'
[10:43:57Z +305s] 6. postgres-exporter 2분 넘게 정지 → 다운 메일
[10:48:00Z +548s] PASS postgres-exporter 다운 메일 도착 
[10:48:00Z +548s] PASS 메일 내용 TargetDown FIRING postgres-exporter 제목='[FIRING:1] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)'
[10:48:00Z +548s] 7. exporter DB 접속 정보 틀림(pg_up 0) → 다운 메일(대상 postgres)
[10:50:51Z +719s] PASS pg_up 0 다운 메일(대상 postgres) 도착 ['[FIRING:1] TargetDown postgres (postgres-exporter:9187 postgres critical)']
[10:50:51Z +719s] PASS 메일 내용 TargetDown FIRING postgres 제목='[FIRING:1] TargetDown postgres (postgres-exporter:9187 postgres critical)'
[10:50:51Z +719s] 8. alertmanager 재기동 뒤 6분 동안 같은 알림 메일이 다시 오지 않는지
[10:56:52Z +1080s] FAIL 재기동 뒤 6분 중복 메일 없음 새 메일=['[RESOLVED] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)']
[10:56:52Z +1080s] 9. exporter 원복 → 해제 메일(api·exporter·postgres·교착)
[10:56:53Z +1081s] PASS 해제 메일 TargetDown api ['[RESOLVED] TargetDown api (api:9095 jbank-api critical)']
[10:56:53Z +1081s] PASS 해제 메일 TargetDown postgres-exporter ['[RESOLVED] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)']
[11:02:23Z +1411s] PASS 해제 메일 TargetDown postgres ['[RESOLVED] TargetDown postgres (postgres-exporter:9187 postgres critical)']
[11:02:23Z +1411s] PASS 해제 메일 DeadlockDetected  ['[RESOLVED] DeadlockDetected (16384 jbank postgres-exporter:9187 postgres critical)']
[11:02:23Z +1411s] 10. socket-proxy 허용 목록·네트워크 격리
[11:02:25Z +1413s] FAIL 프록시 허용·거부 {'GET /containers/json': '400', 'POST /containers/create': '405', 'GET /containers/{id}/archive': '403', 'POST /containers/{id}/exec': '405'}
[11:02:25Z +1413s] PASS api·grafana에서 socket-proxy 접속 실패 api rc=1 "wget: bad address 'socket-proxy:2375'" grafana rc=1 "wget: bad address 'socket-proxy:2375'"
[11:02:25Z +1413s] 11. 새 컨테이너 상한·재시작 횟수
Traceback (most recent call last):
  File "/private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/e2e.py", line 287, in <module>
    i = json.loads(sh("docker", "inspect", f"compose-{c}-1").stdout)[0]
IndexError: list index out of range
exit=1
```

### 1차 실패 원인

- **"재기동 뒤 6분 중복 메일 없음" FAIL**: 판정 방식 오류. "재기동 뒤 새 메일 0통"으로 판정했지만 그 사이 온 메일은 exporter 다운의 해제 메일(`[RESOLVED] TargetDown postgres-exporter`)이었다. 7단계의 대체 exporter(`e2e-pe-bad`, 같은 별칭)가 수집에 응답하면서 exporter 다운이 해제된 것이다. 요구(REQ-08)는 같은 알림의 발생 메일을 다시 보내지 않는 것이므로 2차에서 알림·대상별 발생 메일 수로, 3차에서 독립 재검사로 다시 판정했다.
- **"프록시 허용·거부" FAIL**: 검사 오류. 허용 경로를 `/v1.43/...`로 불렀는데 Docker 29는 최소 API 1.44라 **데몬이** 400을 냈다(응답 헤더 `Server: Docker/29.2.1`, 프록시는 통과시킴). 거부 요청은 프록시가 메서드 밖이면 405, 경로 밖이면 403으로 막았다(프록시 로그 `blocked request`).
- **IndexError**: 9단계 정리에서 `docker container prune -f`가 6단계에서 멈춰 둔 `compose-postgres-exporter-1`까지 지웠다. 그래서 9단계의 `compose start postgres-exporter`가 아무것도 띄우지 못했고(10:59:50Z exporter 다운이 새로 발생), 11단계 inspect가 실패했다. `up -d postgres-exporter`로 다시 만들었다.

## 2차 실행 (e2e2.py, 1차 원인 수정 후 8·10~12단계)

```text
[11:03:28Z] Mailpit 메일 전체(시각, 제목):
[11:03:28Z]   2026-10-29T10:40:52.759Z [FIRING:1] DeadlockDetected (16384 jbank postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T10:43:51.482Z [FIRING:1] TargetDown api (api:9095 jbank-api critical)
[11:03:28Z]   2026-10-29T10:45:52.532Z [RESOLVED] DeadlockDetected (16384 jbank postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T10:47:50.463Z [FIRING:1] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T10:48:51.218Z [RESOLVED] TargetDown api (api:9095 jbank-api critical)
[11:03:28Z]   2026-10-29T10:50:50.53Z [FIRING:1] TargetDown postgres (postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T10:52:20.449Z [RESOLVED] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T10:59:50.478Z [FIRING:1] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical)
[11:03:28Z]   2026-10-29T11:02:20.479Z [RESOLVED] TargetDown postgres (postgres-exporter:9187 postgres critical)
[11:03:28Z] FIRING 메일 수(알림 대상별)={'DeadlockDetected': 1, 'TargetDown api': 1, 'TargetDown postgres-exporter': 2, 'TargetDown postgres': 1} RESOLVED={'DeadlockDetected': 1, 'TargetDown api': 1, 'TargetDown postgres-exporter': 1, 'TargetDown postgres': 1}
[11:03:28Z] FAIL 알림·대상별 발생 메일 1통(재기동 뒤 재발송 없음) 
[11:03:28Z] PASS 발생 4종 모두 해제 메일 1통 
[11:03:30Z] PASS 프록시 허용 GET 200, 그 밖은 403·405로 거부 {'GET /containers/json': '200', 'GET /containers/{id}/json': '200', 'POST /containers/create': '405', 'GET /containers/{id}/archive': '403', 'POST /containers/{id}/exec': '405', 'DELETE /containers/{id}': '405'}
[11:03:30Z] socket-proxy 거부 로그:
socket-proxy-1  | time=2026-10-29T11:02:24.943Z level=WARN msg="blocked request" reason="method not allowed" method=POST URL=/v1.43/containers/create client=172.22.0.3:47920 response=405
socket-proxy-1  | time=2026-10-29T11:02:25.125Z level=WARN msg="blocked request" reason="path not allowed" method=GET URL="/v1.43/containers/844c591b73e17904a6ab7180ab7a3a0e38bfdb4fef5d165e522d2365e9c94dec/archive?path=/etc" client=172.22.0.3:47928 response=403
socket-proxy-1  | time=2026-10-29T11:02:25.277Z level=WARN msg="blocked request" reason="method not allowed" method=POST URL=/v1.43/containers/844c591b73e17904a6ab7180ab7a3a0e38bfdb4fef5d165e522d2365e9c94dec/exec client=172.22.0.3:47944 response=405
socket-proxy-1  | time=2026-10-29T11:03:29.397Z level=WARN msg="blocked request" reason="method not allowed" method=POST URL=/containers/create client=172.22.0.3:33296 response=405
socket-proxy-1  | time=2026-10-29T11:03:29.711Z level=WARN msg="blocked request" reason="path not allowed" method=GET URL="/containers/844c591b73e17904a6ab7180ab7a3a0e38bfdb4fef5d165e522d2365e9c94dec/archive?path=/etc" client=172.22.0.3:33300 response=403
socket-proxy-1  | time=2026-10-29T11:03:29.963Z level=WARN msg="blocked request" reason="method not allowed" method=POST URL=/containers/844c591b73e17904a6ab7180ab7a3a0e38bfdb4fef5d165e522d2365e9c94dec/exec client=172.22.0.3:33312 response=405
socket-proxy-1  | time=2026-10-29T11:03:30.499Z level=WARN msg="blocked request" reason="method not allowed" method=DELETE URL=/containers/844c591b73e17904a6ab7180ab7a3a0e38bfdb4fef5d165e522d2365e9c94dec client=172.22.0.3:33328 response=405
[11:03:31Z] PASS api·grafana에서 socket-proxy 접속 실패 api rc=1 "wget: bad address 'socket-proxy:2375'" grafana rc=1 "wget: bad address 'socket-proxy:2375'"
[11:03:31Z] PASS 상한·restart 정책·재시작 0·OOM 없음 (이름, MiB, 정책, 재시작, OOM)=[('loki', 192, 'unless-stopped', 0, False), ('alloy', 96, 'unless-stopped', 0, False), ('socket-proxy', 16, 'unless-stopped', 0, False), ('postgres-exporter', 32, 'unless-stopped', 0, False), ('alertmanager', 32, 'unless-stopped', 0, False)]
[11:03:31Z] postgres-exporter는 1차 실행 정리 단계에서 컨테이너가 지워져 다시 만들었으므로 재시작 횟수는 그 뒤부터다.
[11:03:33Z] docker stats:
compose-postgres-exporter-1 7.277MiB / 32MiB
compose-prometheus-1 41.87MiB / 3.827GiB
compose-mailpit-1 19.54MiB / 3.827GiB
compose-alertmanager-1 14.25MiB / 32MiB
compose-socket-proxy-1 12.03MiB / 16MiB
compose-alloy-1 64.98MiB / 96MiB
compose-loki-1 86.13MiB / 192MiB
compose-postgres-1 43.66MiB / 3.827GiB
compose-grafana-1 84.92MiB / 3.827GiB
compose-api-1 499.1MiB / 3.827GiB
compose-redis-1 20.83MiB / 3.827GiB

[11:03:33Z] 표본 390줄(최근 90분), 검색 결과: {'JWT': [], '주민번호': [], 'password=': []}
[11:03:33Z] postgres DETAIL·STATEMENT 표본:
2026-10-29 10:39:38.705 UTC [1104] ERROR:  deadlock detected
2026-10-29 10:56:43.532 UTC [2785] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:56:43.532 UTC [2786] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:56:28.540 UTC [2761] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:56:28.539 UTC [2760] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:56:13.535 UTC [2734] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:56:13.535 UTC [2735] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:55:58.550 UTC [2707] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:55:58.549 UTC [2708] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:55:43.537 UTC [2682] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:55:43.536 UTC [2681] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
2026-10-29 10:55:28.551 UTC [2657] DETAIL:  Connection matched file "/var/lib/postgresql/data/pg_hba.conf" line 128: "host all all all scram-sha-256"
[11:03:33Z] PASS 로그 표본 비밀 형태 0건 
[11:03:33Z] 요약: FAIL:알림·대상별 발생 메일 1통(재기동 뒤 재발송 없음), PASS:발생 4종 모두 해제 메일 1통, PASS:프록시 허용 GET 200, 그 밖은 403·405로 거부, PASS:api·grafana에서 socket-proxy 접속 실패, PASS:상한·restart 정책·재시작 0·OOM 없음, PASS:로그 표본 비밀 형태 0건
exit=1
```

2차의 "알림·대상별 발생 메일 1통" FAIL은 `TargetDown postgres-exporter`가 2통이어서다. 두 번째(10:59:50Z)는 위 IndexError 원인과 같이 정리 명령이 exporter 컨테이너를 지워 실제로 다시 2분 넘게 다운된 새 장애다. alertmanager 재기동(10:50:51Z) 뒤 6분 동안 계속 발생 중이던 `TargetDown postgres`의 발생 메일은 다시 오지 않았다. 판정이 해석에 기대므로 3차에서 재기동 중복만 독립적으로 다시 검사했다.

## 3차 실행 (e2e3.py, 재기동 중복 발송 독립 재검사)

```text
[11:04:04Z] Mailpit 비움. api 정지
[11:06:56Z] 발생 메일: ['[FIRING:1] TargetDown api (api:9095 jbank-api critical)']
[11:06:56Z] alertmanager 재기동
[11:06:56Z] Container compose-alertmanager-1 Restarting 
 Container compose-alertmanager-1 Started
[11:12:56Z] 재기동 후 6분: TargetDown api 발생 메일 1통, 계속 firing 중인 대상=['api']
[11:12:56Z] PASS 재기동 뒤 6분 같은 알림 재발송 없음(알림은 계속 발생 중)
[11:18:29Z] PASS 해제 메일 ['[RESOLVED] TargetDown api (api:9095 jbank-api critical)']
[11:18:29Z] 전체 메일: 2026-10-29T11:04:50.552Z [RESOLVED] TargetDown postgres-exporter (postgres-exporter:9187 postgres critical); 2026-10-29T11:06:50.42Z [FIRING:1] TargetDown api (api:9095 jbank-api critical); 2026-10-29T11:18:20.492Z [RESOLVED] TargetDown api (api:9095 jbank-api critical)
exit=0
```

(첫 줄의 `[RESOLVED] TargetDown postgres-exporter`는 2차 뒤 exporter를 다시 만든 데 따른 이전 장애의 해제 메일이다.)

## 완료 기준 3 항목별 판정

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 사전 조건 up·pg_up·알림 inactive | 통과 | 1차 0단계 |
| 인증된 성공 이체 → 규칙 지표 존재 | 통과 | 1차 1단계(이체 URI 200, 락 대기 method, 교착 카운터) |
| Loki api·postgres 로그(컨테이너 이름 라벨) | 통과 | 1차 2단계 |
| 교착 `deadlock detected`·`DETAIL`, `still waiting for` | 통과 | 1차 3단계 |
| 교착 알림 firing → Grafana 조회 → 발생·해제 메일, 내용 대조 | 통과 | 1차 4·9단계 |
| api·exporter·pg_up 다운 발생·해제 메일, 대상 이름 대조 | 통과(pg_up 해제는 4차) | api·exporter는 1차 5·6·9단계. pg_up 해제의 1차 근거(11:02:20Z)는 exporter가 사라져 시계열이 없어진 결과라 무효(결과 리뷰 1회차 I-1) → 4차에서 pg_up 0→1 전환 뒤 해제 메일 확인 |
| alertmanager 재기동 뒤 6분 재발송 없음 | 통과(3·4차) | 1차 판정 방식 오류 → 3차 `restart`, 4차 `up -d --force-recreate`(상태 볼륨 효과)로 재검사 |
| socket-proxy 허용 GET 200, POST·DELETE 405, archive 403, api·grafana 접근 불가 | 통과(2차) | 1차 검사 오류(API 1.43) → 2차 |
| 새 컨테이너 상한·restart·재시작 0·OOM 없음 | 통과(2차) | exporter는 1차 정리에서 지워져 다시 만든 뒤 기준 |
| 로그 표본 비밀 형태 0건 | 통과(2·4차) | 2차 390줄. 4차는 새 테스트 비밀번호 원문·JSON `"password":` 키까지 309줄 0건 |
| 교착 쿼리 줄(`Process NNNN: …`)·`STATEMENT` | 통과(4차) | 결과 리뷰 1회차 권고 2 |
| 새 컨테이너 재시작 0·OOM 없음(마지막 시점) | 통과(4차) | 결과 리뷰 1회차 권고 4 |

## 4차 실행 (e2e4.py, 결과 리뷰 1회차 지적 재검사)

세션이 다시 시작된 뒤 Docker 컨텍스트가 `desktop-linux`로 바뀌어 있었고 OrbStack에는 컨테이너·볼륨·이 작업 이미지가 없었다(이 작업이 지운 것 아님). 전역 설정은 바꾸지 않고 명령마다 `DOCKER_CONTEXT=orbstack`으로 실행했고, 로컬 스택은 `up -d --build`로 다시 만들었다(커밋 c500c5b).

### 4차 첫 시도 (스크립트 버그 2개로 중단)

```text
[12:10:58Z] PASS 사전 조건 up·pg_up·알림 없음 up={'postgres': '1', 'jbank-api': '1'} pg_up=['1'] active=[]
[12:10:59Z] PASS 테스트 고객 가입·로그인(실패 1회 포함)·이체 이체 status=201
[12:11:13Z] 교착 관련 postgres 로그(Loki, 최근 5분):
2026-10-29 12:11:02.829 UTC [209] ERROR:  deadlock detected
2026-10-29 12:11:02.829 UTC [209] STATEMENT:  begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
	Process 210: begin; select * from e2e_lock where id=2 for update; select pg_sleep(2); select * from e2e_lock where id=1 for update; commit;
	Process 209: begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
2026-10-29 12:11:02.829 UTC [209] DETAIL:  Process 209 waits for ShareLock on transaction 793; blocked by process 210.
2026-10-29 12:11:02.829 UTC [209] STATEMENT:  begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
2026-10-29 12:11:02.829 UTC [209] DETAIL:  Process holding the lock: 210. Wait queue: .
[12:11:13Z] FAIL 교착 쿼리 줄(Process NNNN: select …)·STATEMENT 조회 Process줄=0 STATEMENT줄=2
[12:11:13Z] exporter를 틀린 DB 비밀번호로 재생성: unknown docker command: "compose /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/exporter-wrong-pass.yml"
[12:11:53Z] FAIL exporter 수집은 유지(up=1)되고 pg_up=0 up={'postgres': '1', 'jbank-api': '1'} pg_up=['1']
[12:16:54Z] FAIL pg_up 0 → 발생 메일(대상 postgres) []
[12:16:55Z] alertmanager --force-recreate: Container compose-alertmanager-1  Recreate |  Container compose-alertmanager-1  Recreated |  Container compose-alertmanager-1  Starting |  Container compose-alertmanager-1  Started
exit=143
```

- "교착 쿼리 줄" FAIL: 쿼리 줄은 들어와 있었다(`Process 210: begin; select ...`). 검사가 `Process N: select`로 시작하는 줄만 찾아서 놓쳤다. 정규식을 `Process \d+: \S`로 고쳤다.
- "pg_up=0"·"발생 메일" FAIL: override 파일을 넣는 명령을 `docker compose -f -f <override> ...`로 잘못 조립해(`unknown docker command`) exporter가 실제로 바뀌지 않았다. 인자 순서를 고쳤다.
- 이후 단계가 의미 없어 중단(exit=143)했다.

### 4차 재실행 (버그 수정 후)

```text
[12:21:24Z] PASS 사전 조건 up·pg_up·알림 없음 up={'postgres': '1', 'jbank-api': '1'} pg_up=['1'] active=[]
[12:21:24Z] PASS 테스트 고객 가입·로그인(실패 1회 포함)·이체 이체 status=201
[12:21:38Z] 교착 관련 postgres 로그(Loki, 최근 5분):
2026-10-29 12:21:28.267 UTC [1296] ERROR:  deadlock detected
2026-10-29 12:21:28.267 UTC [1296] STATEMENT:  begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
	Process 1297: begin; select * from e2e_lock where id=2 for update; select pg_sleep(2); select * from e2e_lock where id=1 for update; commit;
	Process 1296: begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
2026-10-29 12:21:28.267 UTC [1296] DETAIL:  Process 1296 waits for ShareLock on transaction 804; blocked by process 1297.
2026-10-29 12:21:28.266 UTC [1296] STATEMENT:  begin; select * from e2e_lock where id=1 for update; select pg_sleep(2); select * from e2e_lock where id=2 for update; commit;
2026-10-29 12:21:28.266 UTC [1296] DETAIL:  Process holding the lock: 1297. Wait queue: .
[12:21:38Z] PASS 교착 쿼리 줄(Process NNNN: select …)·STATEMENT 조회 Process줄=2 STATEMENT줄=2
[12:21:39Z] exporter를 틀린 DB 비밀번호로 재생성: Container compose-postgres-exporter-1  Recreate |  Container compose-postgres-exporter-1  Recreated |  Container compose-postgres-exporter-1  Starting |  Container compose-postgres-exporter-1  Started
[12:22:19Z] PASS exporter 수집은 유지(up=1)되고 pg_up=0 up={'postgres': '1', 'jbank-api': '1'} pg_up=['0']
[12:25:00Z] PASS pg_up 0 → 발생 메일(대상 postgres) ['[FIRING:1] TargetDown postgres (postgres-exporter:9187 postgres critical)']
[12:25:00Z] alertmanager --force-recreate: Container compose-alertmanager-1  Recreate |  Container compose-alertmanager-1  Recreated |  Container compose-alertmanager-1  Starting |  Container compose-alertmanager-1  Started
[12:31:00Z] PASS 재생성 뒤 6분 재발송 없음(알림은 계속 발생 중) 발생 메일 1통, firing=['postgres']
[12:31:02Z] exporter를 원래 설정으로 재생성: Container compose-postgres-exporter-1  Recreate |  Container compose-postgres-exporter-1  Recreated |  Container compose-postgres-exporter-1  Starting |  Container compose-postgres-exporter-1  Started
[12:36:33Z] pg_up 값 변화(query_range 15초 간격, UTC): [('12:21:08', '1'), ('12:21:53', '0'), ('12:31:23', '1')]
[12:36:33Z] PASS pg_up 0→1 전환 관찰 
[12:36:33Z] PASS 원복 뒤 해제 메일(대상 postgres) [('2026-10-29T12:36:20.712Z', '[RESOLVED] TargetDown postgres (postgres-exporter:9187 postgres critical)')]
[12:36:33Z] pg_up이 다시 1이 된 첫 표본 12:31:23, 해제 메일 2026-10-29T12:36:20.712Z
[12:36:34Z] 표본 309줄(api·postgres, 최근 40분, 이번 가입·실패 로그인·이체 포함) 검색 건수: {'테스트 비밀번호 원문': 0, 'JSON password 키': 0, 'password=': 0, 'JWT': 0, '주민번호': 0}
[12:36:34Z] PASS 로그 비밀값 0건 
[12:36:34Z] PASS 마지막 재시작 0·OOM 없음 (이름, 상한MiB, 재시작, OOM, 생성시각)=[('loki', 192, 0, False, '2026-10-29T12:09:50'), ('alloy', 96, 0, False, '2026-10-29T12:09:50'), ('socket-proxy', 16, 0, False, '2026-10-29T12:09:50'), ('postgres-exporter', 32, 0, False, '2026-10-29T12:31:01'), ('alertmanager', 32, 0, False, '2026-10-29T12:25:00')]
[12:36:36Z] docker stats:
compose-postgres-exporter-1 8.184MiB / 32MiB 25.57%
compose-alertmanager-1 13.22MiB / 32MiB 41.32%
compose-api-1 521.3MiB / 3.884GiB 13.11%
compose-alloy-1 27.82MiB / 96MiB 28.98%
compose-socket-proxy-1 12.79MiB / 16MiB 79.96%
compose-prometheus-1 68.17MiB / 3.884GiB 1.71%
compose-mailpit-1 23.99MiB / 3.884GiB 0.60%
compose-grafana-1 68.57MiB / 3.884GiB 1.72%
compose-redis-1 5.234MiB / 3.884GiB 0.13%
compose-loki-1 146.5MiB / 192MiB 76.32%
compose-postgres-1 49.16MiB / 3.884GiB 1.24%

[12:36:36Z] 요약: PASS:사전 조건 up·pg_up·알림 없음, PASS:테스트 고객 가입·로그인(실패 1회 포함)·이체, PASS:교착 쿼리 줄(Process NNNN: select …)·STATEMENT 조회, PASS:exporter 수집은 유지(up=1)되고 pg_up=0, PASS:pg_up 0 → 발생 메일(대상 postgres), PASS:재생성 뒤 6분 재발송 없음(알림은 계속 발생 중), PASS:pg_up 0→1 전환 관찰, PASS:원복 뒤 해제 메일(대상 postgres), PASS:로그 비밀값 0건, PASS:마지막 재시작 0·OOM 없음
exit=0
```
