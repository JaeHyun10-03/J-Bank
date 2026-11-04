# 이체 교착 재현 실험 결과

운영 장애가 아니라 **일부러 재현한 실험**이다. 서비스 코드는 첫 구현부터 두 계좌를 정렬된 순서로 잠갔고(FR-TXN-003), ADR 0012 이후 송금 `FOR NO KEY UPDATE`·수신 `FOR KEY SHARE`로 락 모드까지 나눴다. 이 실험은 그 두 장치를 설정으로 끈 "수정 전 방식"을 다시 만들어, 교착이 어떻게 드러나고 무엇으로 사라지는지 같은 조건의 수치로 비교한다.
절차는 [runbook](../../../docs/runbook/transfer-deadlock-experiment.md), 회차별 원자료는 이 폴더의 `<환경>/<조건>-vu<N>-r<회차>/`에 있다.

## 조건

| 조건 | 설정 | 락 순서 | 락 모드 |
| --- | --- | --- | --- |
| ① | `unsorted-for-update` | 요청 방향(송금 → 수신) | 둘 다 `FOR UPDATE` |
| ② | `sorted-for-update` | 계좌번호 오름차순 | 둘 다 `FOR UPDATE` |
| ③ | `current`(서비스 기본) | 계좌번호 오름차순 | 송금 `FOR NO KEY UPDATE`, 수신 `FOR KEY SHARE` |

## 측정 조건(모든 회차 같음)

- 같은 고객의 두 계좌(각 1억 원) 사이 1,000원 즉시 이체. 방향은 `(__VU + __ITER) % 2`로 섞는다. 반복마다 새 멱등키.
- k6 `constant-vus` 60초, 요청 타임아웃 30초, VU 50·100. 회차 전 api 재생성 → 미반영 입금 0 → `VACUUM (ANALYZE) accounts` → 워밍업 20초(VU 5) → 10초 뒤 기준값 → 측정 → 10초 뒤 미반영 입금 0 → 종료값.
- 교착 증가는 `pg_stat_database.deadlocks`의 기준값·종료값 차이다(워밍업 중 교착은 빠진다).
- Hikari 풀 10(기본), Tomcat 스레드 200(기본). VU가 커져도 DB 동시성은 풀 크기로 묶이고 나머지는 커넥션을 기다린다.

## 로컬 결과

환경: MacBook Air(Apple Silicon), Docker Desktop, compose core+api+observability. k6도 같은 맥에서 돌아 부하 발생기와 대상이 자원을 나눠 쓴다. 회차 순서는 1회 ①②③, 2회 ②③①, 3회 ③①②(조건 안에서 VU 50 → 100)다.

아래 표는 `python3 perf/deadlock-summary.py local` 출력이다(손으로 고치지 않는다, `--check`로 대조).

<!-- summary:local -->
| 회차 | 조건 | VU | 결과 | 요청 | 2xx | 5xx (COMMON_006) | 기타 오류 | 타임아웃 | 교착 증가 | 실패 응답 중 반영 | 불변식 | p95 ms | p99 ms | 처리량 req/s | 락 대기 평균 ms | 알림 해제 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| unsorted-for-update-vu50-r1 | ① 정렬 없음+FOR UPDATE | 50 | 완료 | 224 | 24 | 199 (199) | 0 | 1 | 202 | 0 | 유지 | 23266 | 24367 | 2.8 | 2438.3 | 해제 확인 |
| unsorted-for-update-vu50-r2 | ① 정렬 없음+FOR UPDATE | 50 | 완료 | 186 | 16 | 164 (164) | 0 | 6 | 167 | 2 | 유지 | 28358 | 30002 | 2.4 | 2840.2 | 해제 확인 |
| unsorted-for-update-vu50-r3 | ① 정렬 없음+FOR UPDATE | 50 | 완료 | 195 | 17 | 173 (173) | 0 | 5 | 175 | 0 | 유지 | 25381 | 30002 | 2.5 | 2755.7 | 해제 확인 |
| unsorted-for-update-vu100-r1 | ① 정렬 없음+FOR UPDATE | 100 | 완료 | 277 | 7 | 134 (134) | 0 | 136 | 222 | 8 | 유지 | 30010 | 30027 | 3.1 | 2683.2 | 해제 확인 |
| unsorted-for-update-vu100-r2 | ① 정렬 없음+FOR UPDATE | 100 | 완료 | 282 | 6 | 105 (105) | 0 | 171 | 194 | 10 | 유지 | 30006 | 30012 | 3.1 | 2996.0 | 해제 확인 |
| unsorted-for-update-vu100-r3 | ① 정렬 없음+FOR UPDATE | 100 | 완료 | 281 | 13 | 105 (105) | 0 | 163 | 201 | 12 | 유지 | 30002 | 30004 | 3.1 | 2854.3 | 해제 확인 |
| sorted-for-update-vu50-r1 | ② 정렬+FOR UPDATE | 50 | 완료 | 51717 | 51717 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 110 | 206 | 858.9 | 5.2 | - |
| sorted-for-update-vu50-r2 | ② 정렬+FOR UPDATE | 50 | 완료 | 64543 | 64543 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 76 | 112 | 1071.9 | 4.2 | - |
| sorted-for-update-vu50-r3 | ② 정렬+FOR UPDATE | 50 | 완료 | 44136 | 44136 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 139 | 266 | 731.1 | 6.1 | - |
| sorted-for-update-vu100-r1 | ② 정렬+FOR UPDATE | 100 | 완료 | 59148 | 59148 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 166 | 231 | 982.6 | 4.5 | - |
| sorted-for-update-vu100-r2 | ② 정렬+FOR UPDATE | 100 | 완료 | 58012 | 58012 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 163 | 231 | 963.5 | 4.6 | - |
| sorted-for-update-vu100-r3 | ② 정렬+FOR UPDATE | 100 | 완료 | 49600 | 49600 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 192 | 278 | 821.6 | 5.4 | - |
| current-vu50-r1 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 50 | 완료 | 72141 | 72141 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 76 | 109 | 1198.3 | 3.3 | - |
| current-vu50-r2 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 50 | 완료 | 72506 | 72506 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 76 | 102 | 1204.7 | 3.4 | - |
| current-vu50-r3 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 50 | 완료 | 68061 | 68061 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 79 | 110 | 1130.7 | 3.4 | - |
| current-vu100-r1 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 100 | 완료 | 63673 | 63673 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 167 | 233 | 1056.7 | 3.8 | - |
| current-vu100-r2 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 100 | 완료 | 67291 | 67291 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 146 | 201 | 1117.1 | 3.6 | - |
| current-vu100-r3 | ③ 현재(정렬+NO KEY UPDATE/KEY SHARE) | 100 | 완료 | 62690 | 62690 | 0 (0) | 0 | 0 | 0 | 0 | 유지 | 165 | 241 | 1040.9 | 3.8 | - |
<!-- /summary:local -->

### 읽는 법

- 교착은 ①에서만 났다. 측정 60초마다 167~222건이다. ②·③은 18회 중 12회 모두 0건이다.
- ①은 처리량이 무너진다.
  - 동시 50: 처리량 초당 2.4~2.8건, 성공 16~24건이다. ②·③은 같은 조건에서 초당 731~1,205건이다.
  - 동시 100: 성공 6~13건이다. 나머지는 30초 타임아웃(136~171건)과 500이다.
  - 교착 하나를 PostgreSQL이 찾아내는 데 `deadlock_timeout`(1초)이 걸린다. 그동안 커넥션 풀 10개가 락 대기에 묶여, 뒤의 요청이 커넥션을 기다리다 타임아웃된다. 락 대기 평균은 ① 2.4~3.0초, ②·③ 3~6ms다.
  - 표의 처리량은 k6 `http_reqs` 비율이라 setup 로그인 1건과 측정 끝의 마무리 대기(최대 30초)까지 포함한 전체 시간 기준이다. ①은 이 시간이 80~90초로 늘어 60초 기준보다 낮게 나온다.
- ①의 500은 모두 `COMMON_006`이다. 교착으로 롤백된 이체는 거래·원장·입금 대기를 하나도 남기지 않아, 잔액 합·원장 건수 불변식은 모든 회차에서 유지됐다.
- 500·타임아웃과 교착 증가의 대조(REQ-06)
  - 동시 50: 교착 202·167·175건, 실패 응답(500+타임아웃) 200·170·178건으로 회차마다 2~3건 차이다. 1회는 교착이 2건 많고, 2·3회는 실패 응답이 3건 많아 방향이 일정하지 않다. 교착 피해 트랜잭션 하나가 500 하나가 된다. 남는 차이는 측정 끝 경계에서 진행 중이던 요청으로 보이며, api 로그를 회차별로 남기지 않아 확정하지 못했다.
  - 동시 100: 500은 105~134건으로 교착(194~222건)보다 88~96건 적고, 타임아웃이 136~171건이다. 커넥션을 기다리느라 30초를 넘긴 뒤에 교착으로 롤백된 요청은 클라이언트에 500이 아니라 타임아웃으로 잡힌다. 나머지 타임아웃은 끝내 커넥션을 받지 못했거나 30초 뒤에 처리된 요청이다(아래 "실패 응답 중 반영").
  - 커넥션 대기 30초(Hikari `connectionTimeout`) 초과도 500(`COMMON_006`)이 될 수 있다. 응답 코드만으로는 교착과 구분되지 않는다.
- "실패 응답 중 반영"이 0보다 큰 회차(①의 4회, 2~12건)가 있다. 클라이언트는 30초에 포기했지만 서버는 그 뒤 이체를 끝낸 경우다. 사용자는 실패를 봤는데 돈은 옮겨졌다. 같은 멱등키로 다시 보내면 원래 결과를 돌려받는다(기존 멱등 처리). 실패 응답 수 이하라 불변식 위반은 아니다.
- ②와 ③은 둘 다 교착이 없다. ③(현재)이 동시 50에서 처리량이 더 높고(③ 1,131~1,205 vs ② 731~1,072) 락 대기도 짧다(③ 3.3~3.4ms vs ② 4.2~6.1ms). 송금 `NO KEY UPDATE`와 수신 `KEY SHARE`가 서로 막지 않기 때문이다. 동시 100에서는 범위가 겹쳐(③ 1,041~1,117 vs ② 822~983) 회차 순서·같은 맥의 부하 발생기 영향과 구분하기 어렵다.

### 교착을 어떻게 발견하나

①은 워밍업(동시 5, 20초)에서 이미 교착이 난다. 워밍업마다 500이 28~37건 났고, 첫 교착은 회차 시작(api 재생성 포함) 16~26초 뒤 워밍업 중에 나왔다. 그래서 알림 시각은 측정이 아니라 워밍업 첫 교착부터 센다(교착 증가량 집계에서는 워밍업이 빠진다).
조건 ① 회차(동시 50, 1회)의 시간 순서다(UTC).

1. 07:02:47 첫 `deadlock detected`(워밍업 중, `postgres-log.txt`).
2. 07:03:19 기준값 기록, 측정 시작. 이때 교착 카운터가 이미 워밍업 교착을 반영하고 있다.
3. 07:03:50 `[FIRING] DeadlockDetected`, `[FIRING] AccountLockWaitHigh` 메일(mailpit). 첫 교착 약 63초 뒤다. Prometheus 수집 간격(15초), 규칙 평가, Alertmanager `group_wait`(30초)를 거친다.
4. 07:06:50 `[FIRING] TransferServerErrorRate`(이체 5xx 비율).
5. Grafana Loki에서 postgres 로그를 조회하면 `deadlock detected`와 대기 관계(`DETAIL`)가 나온다. 회차 시작부터 종료값까지(워밍업 포함) `deadlock detected` 203~258줄이었다. `DETAIL:  Process` 655~780줄에는 교착 고리와 락 대기 로그("Process holding the lock")가 함께 들어 있다(`loki.json`).
6. 07:13:50 `[RESOLVED] DeadlockDetected`. 교착이 멈춘 뒤 5분 창이 비고 다음 `group_interval`에 해제 메일이 온다.

나머지 ① 회차 5회도 세 알림의 발생·해제 메일이 모두 왔다. 동시 100에서는 5xx 비율·락 대기 알림이 기준값 기록 시각과 같아(워밍업이 일으킴) 교착 알림보다 1분 먼저 오기도 했다(`mail.json`).

postgres 로그 발췌(`unsorted-for-update-vu50-r1/postgres-log.txt`의 1·3·12~16줄, 시각 접두 생략). 계좌번호는 바인드 변수(`$1`)라 로그에 남지 않는다.

```text
LOG:  process 446 still waiting for AccessExclusiveLock on tuple (61,51) of relation 16408 of database 16384 after 1001.908 ms
STATEMENT:  select * from accounts where account_number = $1 for update
ERROR:  deadlock detected
DETAIL:  Process 443 waits for ShareLock on transaction 78123; blocked by process 447.
        Process 447 waits for AccessExclusiveLock on tuple (61,51) of relation 16408 of database 16384; blocked by process 444.
        Process 444 waits for ShareLock on transaction 78124; blocked by process 445.
        Process 445 waits for AccessExclusiveLock on tuple (51,28) of relation 16408 of database 16384; blocked by process 443.
```

같은 `accounts` 행 두 개(tuple 61,51과 51,28)를 프로세스들이 서로 반대 순서로 기다리는 고리다. 문장이 `for update` 하나뿐이므로, 같은 쿼리로 두 계좌를 잠그는 순서가 요청 방향에 따라 엇갈린다는 것을 알 수 있다. 해결은 두 가지다. 잠그는 순서를 계좌번호로 고정하면(②) 고리가 생기지 않는다. 락 모드를 나누면(③) 수신 계좌 읽기가 송금 계좌 갱신을 막지 않아 대기 자체가 줄어든다.

## 운영 결과

(운영 실험 후 작성)

## 한계

- 로컬과 운영은 서로 비교하지 않는다(장비·네트워크·관측 스택 상주 여부가 다르다).
- 로컬은 k6와 대상이 같은 맥을 쓴다. 운영은 k6가 사용자 맥에서 인터넷·Caddy(HTTPS)를 거친다.
- 부하는 즉시 이체만 쓴다. OTP 완료 경로의 교착은 통합 테스트(`UnsortedForUpdateLockStrategyScenarioTest`)로만 확인했다. OTP 완료가 교착으로 실패하면 OTP 코드는 이미 소모돼 다시 시도하면 만료 처리된다(기존 동작).
- 교착으로 실패한 요청은 일반 500(`COMMON_006`)이다. 재시도·전용 오류 코드는 이 실험 범위 밖이다.
- k6 `gracefulStop`은 기본값(30초)이라 요청 타임아웃(30초)과 같다. 측정 끝에 진행 중이던 요청 일부가 집계에서 빠질 수 있다. 로컬 18회가 이 값으로 돌았으므로 조건을 바꾸지 않았다.
- 첫 회차(① 동시 50, 1회)는 Loki 조회 함수를 고치기 전 스크립트로 돌았다(`conditions.json`의 git 945c35f, 고친 스크립트 커밋 전). 그 회차의 `loki.json`은 회차가 끝난 뒤 같은 시간 범위로 고친 함수를 다시 돌려 만들었다. 다른 회차는 커밋된 스크립트로 돌았다.
- OTP 완료 경로의 for-update 쿼리(`lockForStatusChange`)에는 `db.lock.wait` 타이머가 없다. 부하가 즉시 이체만 쓰므로 결과에는 영향이 없다.
