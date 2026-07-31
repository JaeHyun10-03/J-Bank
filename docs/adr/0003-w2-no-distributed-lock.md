# ADR 0003: W2 시점 분산 락(Redisson) 도입 보류

## 상태

승인됨. 2026-07-31 W2 이체 코어 구현 시점.

## 배경

`redisson-spring-boot-starter` 의존성은 W1부터 이미 build.gradle.kts에 올라가 있다. 이체 처리(FR-TXN-003)는 두 계좌를 동시에 잠가야 하는 동시성 코드라, 구현 시점에 "이 락을 Redisson 분산 락으로 잡을지, DB 비관적 락(`SELECT ... FOR UPDATE`)만으로 잡을지"를 결정해야 했다.

## 결정

W2에서는 Redisson 분산 락을 쓰지 않는다. `AccountRepository`에 `@Lock(PESSIMISTIC_WRITE)` 쿼리 메서드(`findByAccountNumberForUpdate`, `findByIdForUpdate`)만 두고, `TransferService`/`DepositService`/`WithdrawalService` 모두 이 DB 행 락만으로 동시성을 직렬화한다.

## 근거

지금 애플리케이션 인스턴스는 하나뿐이다. 인스턴스가 하나인 동안에는 모든 요청이 결국 같은 PostgreSQL에 붙으므로, DB 행 수준 락이 모든 동시성을 이미 완전히 직렬화한다. 이 상태에서 분산 락을 얹으면 같은 것을 두 겹으로 잠그는 중복 방어일 뿐 실질적 이득이 없고, Redis 왕복이라는 지연과 락 해제 누락(TTL 만료, 커넥션 끊김 등) 실패 모드만 추가된다.

분산 락이 실제로 필요해지는 지점은 인스턴스를 여러 개로 늘리는 W7이 아니라, 애초에 DB 트랜잭션 경계 밖에서 조율해야 하는 작업이다. 이체·입금·출금은 항상 DB 트랜잭션 안에서 끝나므로 인스턴스를 늘려도 행 락으로 계속 충분하다. 반면 W5의 Spring Batch 배치 잡(이자 계산, 원장 정합성 대사, 고액현금거래 집계)은 여러 인스턴스에서 동시에 같은 배치가 중복 실행되는 것을 막아야 하는데, 이건 단일 DB 트랜잭션으로 표현되지 않는 "잡 전체의 단일 실행 보장" 문제라 분산 락이 실제로 값을 한다. 이 판단 기준은 구현계획 문서 7.1절의 "되돌리는 비용" 기준과 같은 결이다 — 필요하지도 않은 것을 미리 넣는 것은 미루는 것과 반대로 나중에 걷어내야 할 부채가 된다.

동시성 시나리오 5종(잔액 부족 100건, 양방향 이체 100건, 멱등성 키 10건 동시, 랜덤 이체 1000건, 강제 예외 롤백)을 Testcontainers 기반 실제 PostgreSQL로 검증했고 전부 DB 행 락만으로 통과했다(`apps/jbank-api/src/test/java/com/jbank/concurrency/`).

## 영향

- W2: `TransferService`, `DepositService`, `WithdrawalService`는 Redisson을 참조하지 않는다.
- W7: 인스턴스를 여러 개로 늘리는 시점에 Spring Batch 잡의 단일 실행 보장을 위해 Redisson 분산 락을 도입한다. 이체·입금·출금 경로는 그때도 그대로 DB 행 락을 유지한다(도입 범위가 배치 잡으로 한정됨).
- `redisson-spring-boot-starter` 의존성 자체는 W7 전까지 미사용 상태로 남는다.
