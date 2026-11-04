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
- ①의 500은 모두 `COMMON_006`이다. 교착으로 롤백된 이체는 거래·원장·입금 대기를 하나도 남기지 않아, 잔액 합·원장 건수 불변식은 모든 회차에서 유지됐다.
- "실패 응답 중 반영"이 0보다 큰 회차(①의 4회, 2~12건)가 있다. 클라이언트는 30초에 포기했지만 서버는 그 뒤 이체를 끝낸 경우다. 사용자는 실패를 봤는데 돈은 옮겨졌다. 같은 멱등키로 다시 보내면 원래 결과를 돌려받는다(기존 멱등 처리). 실패 응답 수 이하라 불변식 위반은 아니다.
- ②와 ③은 둘 다 교착이 없다. ③(현재)이 동시 50에서 처리량이 더 높고(③ 1,131~1,205 vs ② 731~1,072) 락 대기도 짧다(③ 3.3~3.4ms vs ② 4.2~6.1ms). 송금 `NO KEY UPDATE`와 수신 `KEY SHARE`가 서로 막지 않기 때문이다. 동시 100에서는 범위가 겹쳐(③ 1,041~1,117 vs ② 822~983) 회차 순서·같은 맥의 부하 발생기 영향과 구분하기 어렵다.

### 교착을 어떻게 발견하나

조건 ① 회차(동시 50, 1회)에서 측정이 시작된 07:03:19(UTC) 뒤 순서다. 나머지 ① 회차 5회도 세 알림의 발생·해제 메일이 모두 왔다. 동시 100에서는 5xx 비율 알림이 교착 알림보다 1분 먼저 오기도 했다(`mail.json`).

1. 07:03:50 `[FIRING] DeadlockDetected`, `[FIRING] AccountLockWaitHigh` 메일(mailpit). 측정 시작 약 30초 뒤, Alertmanager `group_wait` 30초다.
2. 07:06:50 `[FIRING] TransferServerErrorRate`(이체 5xx 비율).
3. Grafana Loki에서 postgres 로그를 조회하면 `deadlock detected`와 대기 관계(`DETAIL`)가 나온다. 회차 시작부터 종료값까지(워밍업 포함) `deadlock detected` 203~258줄, `DETAIL:  Process` 655~780줄이었다(`loki.json`).
4. 07:13:50 `[RESOLVED] DeadlockDetected`. 교착이 멈춘 뒤 5분 창이 비고 다음 `group_interval`에 해제 메일이 온다.

postgres 로그 원문(`unsorted-for-update-vu50-r1/postgres.log`). 계좌번호는 바인드 변수(`$1`)라 로그에 남지 않는다.

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
