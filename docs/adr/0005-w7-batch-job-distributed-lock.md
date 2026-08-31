# ADR 0005: W7 배치 잡 Redisson 분산 락 도입

## 상태

승인됨. 2026-08-31 W7 시점.

## 배경

`0003-w2-no-distributed-lock.md`에서 Redisson 분산 락 도입을 W7까지 보류하기로
결정하면서, 그 시점을 "인스턴스를 여러 개로 늘리는 W7"이 아니라 "여러
인스턴스에서 같은 배치 잡이 중복 실행될 수 있게 되는 시점"으로 명시해뒀다.
W7에서 HPA로 API 파드를 2~5개로 늘리고 나면 그 조건이 실제로 성립한다.

배치 잡(`interestMaturityJob`, `ctrDetectionJob`, `ledgerReconciliationJob`)은
K8s CronJob으로 도는데, `concurrencyPolicy: Forbid`가 평상시 같은 CronJob의
중복 실행을 이미 막아준다. 그런데도 이 시점에 분산 락을 넣기로 한 이유는
Forbid가 못 막는 경로가 남기 때문이다 — `kubectl create job --from=cronjob`
같은 수동 재실행, 혹은 스케줄이 겹치는 배포 중 경합처럼 CronJob 컨트롤러의
"활성 Job 추적" 바깥에서 같은 잡이 시작되는 경우. 이체·입금·출금과 달리
배치 잡은 DB 트랜잭션 하나로 끝나지 않는 "잡 전체의 단일 실행"을 보장해야
하는 문제라 DB 행 락으로는 커버가 안 된다.

## 결정

`SingleInstanceJobExecutionListener`(`com.jbank.batch.lock`)를 만들어 세 배치
Job 빌더에 전부 `.listener(...)`로 붙였다. `beforeJob`에서
`batch-job-lock:{jobName}` 이름의 Redisson 락을 `tryLock(0, 30분, SECONDS)`로
즉시 시도하고, 못 잡으면 예외를 던진다. `AbstractJob#execute`는 `beforeJob`이
던진 예외를 스텝 실행 전에 잡아 잡을 FAILED로 종료하는 Spring Batch 표준
동작이라, 락을 못 잡은 쪽은 스텝을 하나도 실행하지 않고 실패한다.

30분 리스타임은 각 잡의 실제 실행 시간을 재보지 않고 넉넉히 잡은 값이다
(`ponytail:` 주석으로 표시). 프로세스가 비정상 종료돼도 이 시간이 지나면
락이 자동 해제된다.

## 근거

- `tryLock(0, ...)`으로 즉시 실패시키는 이유: 이 락은 "먼저 온 게 이길 때까지
  기다렸다가 순서대로 실행"하는 큐가 아니라 "이미 도는 게 있으면 중복
  실행을 막는" 용도다. 대기시켜 두 번째 실행을 뒤늦게라도 통과시키면 같은
  잡이 두 번 도는 것과 다를 게 없다.
- 락을 못 잡았을 때 FAILED로 실패시키는 이유: 이 상황은 정상 스케줄에서는
  일어나지 않아야 하는 경우(Forbid가 이미 막음)라, 조용히 SKIPPED 처리해서
  숨기는 대신 실패로 드러내는 쪽을 택했다. Pod가 Error로 보이는 게 오히려
  의도한 신호다.
- `tryLock`을 잡는 쪽과 재진입 여부를 테스트로 검증할 때, Redisson 락은
  (락 이름, 스레드 ID) 기준 재진입 가능이라 같은 스레드에서 먼저 잡고
  바로 다음 줄에서 잡 실행을 트리거하면 재진입으로 통과해버린다.
  `LedgerReconciliationJobIntegrationTest`의 락 충돌 테스트는 이 문제를
  피하려고 락을 별도 스레드에서 잡아 실제 "다른 실행 주체"를 흉내낸다.

## 영향

- `interestMaturityJob`, `ctrDetectionJob`, `ledgerReconciliationJob` 세
  Job 빈이 모두 `RedissonClient`를 주입받는다.
- 이체·입금·출금 경로는 이 변경과 무관 — 여전히 DB 행 락만 쓴다(`0003-w2-no-distributed-lock.md`
  결정 유지).
- `redisson-spring-boot-starter`가 W7부터 실제로 쓰이기 시작한다.
