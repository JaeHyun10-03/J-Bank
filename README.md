# J-Bank

계좌 원장과 이체를 코어로 하는 코어뱅킹 포트폴리오. 원장 정합성, 동시성, 멱등성을 실제 은행 시스템 원칙대로 구현하고 운영 사양에서 부하로 검증함

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-14-black?logo=nextdotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)

[www.j-bank.site](https://www.j-bank.site) · 데모 서버는 평일 09:00~18:00(KST)에만 운영

<img src="docs/assets/screens/hero-screens.png" width="100%" alt="J-Bank 앱 화면 — 홈, 상품, 계좌이체 3단계" />

## 아키텍처

<img src="docs/assets/diagrams/architecture.svg" width="100%" alt="아키텍처" />

- 프론트는 Vercel, 원장·개인정보를 다루는 백엔드는 EC2 한 대 위 Docker Compose
- 외부 진입은 Caddy 80/443만. SSH 없이 배포·운영 접근 모두 SSM

## 주요 문제 해결

### 1. 한 계좌로 몰린 이체가 무관한 조회까지 멈추는 문제를 막기 위해 입금을 수신 계좌별 묶음 반영으로 바꿔 행 락 경합 해결

<img src="docs/assets/diagrams/hot-account-credit.svg" width="100%" alt="입금 묶음 반영" />

- 문제 원인
  - 이체가 받는 계좌 행도 비관적 락으로 잠가, 급여·정산 계좌처럼 한 계좌로 몰리면 모든 이체가 그 행 하나에서 대기
  - 운영 사양(t3.small, 커넥션 풀 10, 거래 1천만 건) 부하 테스트에서 초당 80~140건(6회 중앙값 95)에서 무너짐
  - CPU는 44~66%로 여유. 락을 기다리는 동안 커넥션을 붙잡아 풀이 고갈되고, 핫 계좌와 무관한 잔액 조회 p95가 8ms → 1,397ms
- 해결 과정
  - 대안 비교: 원장만 즉시 기록하면 대변 원장의 잔액 스냅샷 규칙이 깨지고, 하위 버킷 분산은 경합을 1/N로 줄일 뿐 잔액 조회·출금이 복잡해짐. 입금은 잔액 부족 검사가 필요 없다는 점에 착안해 비동기 반영 선택([ADR 0012](docs/adr/0012-async-credit.md))
  - 이체 트랜잭션은 송금 차감·차변 원장·입금 대기(`pending_credits`)까지만 기록. 받는 계좌는 `FOR KEY SHARE`로 존재·상태만 확인해 워커·다른 이체와 충돌하지 않음
  - 반영 워커가 200ms마다 받는 계좌별 대기분을 `SKIP LOCKED`로 최대 500건 모아 락 한 번에 반영. 가상계좌 집금·차액결제처럼 모아서 정산하는 구조
  - 대사 식을 "차변 = 대변 + 미반영 입금"으로 확장. 반영 대기가 남은 계좌의 해지는 거절해 해지 계좌에 돈이 남지 않게 함
  - 테스트: Testcontainers로 반영·받는 계좌 락·해지 경합 시나리오 검증, 개선 전 기준선과 같은 EC2 조건으로 6회 재측정
- 결과
  - 같은 초당 100건에서 이체 p95 395ms → 33ms, 무관한 조회 p95 307ms → 15ms, 커넥션 대기 최대 67.5 → 0(회차 중앙값)
  - 무너진 다음 단계에서 회복한 회차 0/6 → 3/6. 병목이 행 락에서 CPU로 이동
  - 전 회차 입금 중복·유실 0, 새 불일치 계좌 0. 받는 사람에게는 초당 150건 이하에서 p95 220~404ms 늦게 보이는 트레이드오프([결과](perf/results/ec2-async-credit/summary.md))

### 2. OTP 대기 이체의 초과 출금을 막기 위해 지급정지 금액(hold)을 분리해 잔액·원장 정합성 문제 해결

<img src="docs/assets/diagrams/hold-amount.svg" width="100%" alt="지급정지 금액" />

- 문제 원인
  - 고액 이체는 OTP 인증 전까지 `PENDING_OTP`로 대기
  - 대기 금액을 잔액에서 안 빼면 같은 잔액으로 대기 이체를 여러 건 만들 수 있어 초과 출금
  - 잔액에서 실제로 빼면 원장에 없는 금액이 잔액에서 사라져 원장 합과 잔액이 어긋남
- 해결 과정
  - 계좌에 `hold_amount`를 두고 출금 가능 금액은 `잔액 − hold`로 파생 계산(별도 컬럼 없음)
  - 원장은 확정된 이동(`COMPLETED`)만 기록. 리포지토리에 수정·삭제를 노출하지 않고 JPA 리스너로 한 번 더 막아 append-only 보장
  - OTP 성공 시 hold 해제 + 원장 기록, 실패 한도 초과·만료 시 `CANCELLED` + hold 해제
  - 테스트: Testcontainers(PostgreSQL) 통합 테스트로 인증 성공·실패 한도·만료·취소·출금 가능 금액 초과 거절 확인
- 결과
  - 대기 중 추가 출금은 출금 가능 금액 기준으로 거절돼 초과 출금 차단
  - 취소된 거래가 원장에 흔적을 남기지 않아 정합성 대사가 깨지지 않음
  - 1천만 건 규모 EC2 부하 테스트 전 회차에서도 차변 = 대변, 새 불일치 계좌 0 유지

### 3. 동시 이체의 교착과 중복 처리를 막기 위해 락 순서 고정과 멱등키 유니크 제약으로 동시성 문제 해결

<img src="docs/assets/diagrams/lock-order.svg" width="100%" alt="락 순서와 멱등키" />

- 문제 원인
  - A→B, B→A 이체가 동시에 오면 서로 상대 계좌 락을 기다리며 교착
  - 네트워크 재시도나 중복 클릭으로 같은 이체가 두 번 처리될 수 있음
  - 앱에서 키를 조회한 뒤 저장하는 방식은 동시 요청 두 개가 모두 조회를 통과
- 해결 과정
  - 두 계좌번호를 정렬해 항상 같은 순서로 비관적 락(`PESSIMISTIC_WRITE`)
  - 프론트가 제출 시점에 `Idempotency-Key`를 만들고 재시도에는 같은 키 사용. 서버는 앱 조회에 더해 DB 유니크 제약으로 한 번 더 막고, 같은 키면 기존 결과 반환
  - 테스트: 동시성 시나리오 5종을 Testcontainers(PostgreSQL)에서 실제 스레드로 동시 실행
- 결과
  - 잔액 10건분에 동시 출금 100건: 성공 10, 잔액 부족 90, 최종 잔액 0
  - 양방향 이체 50건씩 교착 없이 완료, 두 계좌 총액 보존
  - 같은 키 동시 요청 10건에 거래 1건, 20개 계좌 무작위 이체 1,000건 후 원장 합 = 잔액

### 4. 운영 사양의 한계를 찾기 위해 별도 EC2 부하 환경을 만들고 측정 결함을 바로잡아 병목과 금융 위험 발견

<img src="docs/assets/diagrams/perf-setup.svg" width="100%" alt="부하 테스트 환경" />

- 문제 원인
  - 로컬 k6로는 목표(p95 200ms, 초당 100건) 대비 16~20배 여유라 한계도 병목도 알 수 없었음
  - 운영과 같은 t3.small에서 처음 잰 결과를 검토하니 측정 자체에 결함 3개
    - 포화된 서비스 포트를 거쳐 지표를 수집해 가장 중요한 구간의 지표가 비어 있음
    - 가상 사용자 약 200명이 같은 시각에 BCrypt 재로그인해 서버 병목이 아닌 가짜 포화 발생
    - 실패 응답 수에 테스트 중단 순간의 요청이 섞여 부풀려짐
- 해결 과정
  - 대상과 부하 발생기를 분리한 EC2 두 대를 Terraform으로 측정할 때만 생성, 1천만 건 시드와 회차마다 PG 복사본 복원
  - 지표는 관리 포트(9095)로 분리하고 수집 대상별 결측 검사 추가, 재로그인은 9~12분에 분산
  - 실패 응답을 받은 이체의 멱등키를 모두 기록해 DB와 대조
  - 1차 결과를 버리고 시나리오 4종을 각 3회 재측정, 모든 회차 지표 결측 0
- 결과
  - 한 계좌로 이체가 몰리면 초당 80건에서 무너짐. CPU는 46%로 여유, 행 락 대기(0.77s)가 커넥션 풀을 고갈시키는 병목 확인(1번에서 해결)
  - 클라이언트가 타임아웃(실패)을 받은 이체가 멱등키 대조 결과 사실상 전부 실제로 완료. 재시도 대신 새로 이체하면 중복 송금이 되는 위험

### 5. 운영 비용 절감을 위해 EC2를 평일에만 켜고, 꺼진 시간의 배포·배치 누락 문제 해결

<img src="docs/assets/diagrams/weekday-ops.svg" width="100%" alt="평일 운영" />

- 문제 원인
  - EKS·RDS 구성(월 약 $200)을 EC2 한 대로 낮춘 뒤에도 상시 가동 비용이 월 약 $24
  - 비용 때문에 인스턴스를 수동으로 끄자 main 머지마다 CD가 `InvalidInstanceId`로 실패
  - 새벽 cron 배치(이자·CTR·FDS·대사)는 서버가 꺼진 시간이라 한 번도 실행되지 않음
- 해결 과정
  - EventBridge Scheduler로 평일 09시 시작, 매일 18시 중지
  - CD가 인스턴스 상태를 확인해 꺼져 있으면 배포 스킵. 이미지에 revision 라벨을 붙여 켜질 때 부팅 작업(systemd)이 최신 이미지 반영
  - 새벽 cron 대신 부팅 직후 배치 실행. CTR·FDS는 Spring Batch 실행 기록에서 마지막 완료일을 찾아 빠진 날짜를 하루씩 처리
  - CD와 부팅 작업이 같은 flock 잠금으로 순차 배포. 서버가 꺼져 있으면 프론트에 운영 시간 안내
  - 테스트: 가짜 docker·aws로 부팅·배포 스크립트를 동시 배포 경합까지 검증, 1회용 스케줄로 실제 중지·시작 확인, 실제 DB에서 중단된 배치 기록이 재실행을 막는 문제를 재현하고 정리 로직 확인
- 결과
  - 월 약 $24 → $11, 꺼진 시간 CD 실패 없이 다음 부팅 때 자동 반영
  - 배치가 부팅 때 자동 실행되고 주말분도 누락 없이 처리
  - 결정 과정은 [ADR 0010](docs/adr/0010-ec2-single-instance.md), [ADR 0011](docs/adr/0011-ec2-weekday-hours.md)

### 6. AI 에이전트가 근거 없이 완료를 선언하지 못하게 Hook 단계 게이트와 읽기 전용 검증 에이전트를 도입해 계획·결과 누락 문제 해결

<img src="docs/assets/diagrams/harness-gate.svg" width="100%" alt="AI 개발 하네스" />

- 문제 원인
  - Claude Code로 개발하면 명세가 정해지기 전에 구현하거나, 테스트를 돌리지 않고 완료라고 하거나, 원자료와 다른 수치를 문서에 쓰는 일이 생김
  - 원장·이체 코드에서는 누락 한 건이 돈 문제로 이어짐
- 해결 과정
  - 계획 → 구현 → 검사 → 검토 → 완료를 상태 파일로 관리하고, 단계 전환은 `workflow.py` 명령으로만 가능하게 함
  - PreToolUse Hook이 구현 단계 전 코드 편집, 증거·상태 파일 직접 수정, hook 우회 명령을 거부
  - `verify`가 `checks.json`의 실제 테스트(Testcontainers 포함)를 실행한 로그만 완료 근거로 보존. 건너뛴 검사와 성능 목표 미달은 실패
  - 읽기 전용 verifier 서브에이전트가 명세 확정 전과 완료 전에 요구사항별 구현·테스트·실행 증거를 대조. 응답 원문을 리뷰 기록에 남기고 리뷰는 기본 각 3회로 제한
  - 테스트: 하네스 자체를 회귀 테스트 37개로 검증
- 결과
  - 6개 작업에서 verifier 지적 70건(차단 5, 중요 65), 그중 54건을 구현 전 계획 단계에서 해소
  - 구현 전: 해지와 입금 반영이 겹치면 입금이 묶이거나 잔액이 사라지는 경합을 찾아냄. 이 지적이 1번의 락 모드 설계(`FOR KEY SHARE`·해지 `FOR UPDATE`)로 이어짐
  - 측정 전: 시드에 원장이 없어 대사가 처음부터 약 10만 건 불일치로 나오는 문제를 찾아냄
  - 완료 전: 3개 작업에서 요약·ADR 수치가 원자료와 다른 것을 적발해 정정. 성능 목표 미달을 보고로만 처리한 작업은 완료가 막혀 사용자 결정을 받음
  - 한계: verifier도 같은 모델 계열이라 완전히 독립된 검증은 아님. 지적 건수는 각 작업의 리뷰 기록 기준 집계([하네스 안내](docs/harness-guide.md))

## 기술 스택

| 영역 | 스택 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.5, JPA, Spring Security, Spring Batch, Flyway, Redisson |
| 프론트 | Next.js 14, TypeScript, TanStack Query, Zustand, Tailwind v4, shadcn/ui |
| 테스트 | JUnit5, ArchUnit, Testcontainers, Jest, Playwright, k6 |
| 인프라 | EC2, Docker Compose, Caddy, Terraform, GitHub Actions, Prometheus/Grafana, Vercel |

## 실행

```bash
scripts/up.sh    # Docker(PostgreSQL·Redis·관측) + 백엔드 + 프론트 한 번에
```

API http://localhost:8080/swagger-ui.html · 화면 http://localhost:3000 · 자세한 절차는 [docs/README.md](docs/README.md#로컬-개발-환경)

## 문서

- [설계 문서 11종](docs/README.md): 요구사항, ERD, API, 인프라 등
- [ADR](docs/adr/) · [개발일지](docs/devlog/) · [성능 측정](perf/README.md)
- [알려진 한계와 개선 과제](docs/11_J-Bank_알려진한계와개선과제.md)
