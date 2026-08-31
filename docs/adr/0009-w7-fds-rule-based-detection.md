# ADR 0009: 룰 기반 이상거래 탐지(FDS) 간이 버전

## 상태

승인됨. 2026-08-31 W7 시점.

## 배경

FR-SUP-003·API-022는 "단일 거래 임계금액 초과, 짧은 시간 내 반복 이체, 심야
시간대 고액 이체" 세 룰만 적용하는 간이 FDS를 요구한다. 이미 같은 배치 패턴인
CTR 판별 잡(구현계획 W5)이 있어 구조를 그대로 따랐다.

## 결정

- `suspicious_transactions` 테이블(ERD에 없던 신규) — 거래 하나가 여러 룰에
  동시에 걸릴 수 있어 `(transaction_id, rule_type)` 조합을 유니크 키로 뒀다.
  CTR의 `status`(PENDING/LOGGED) 같은 워크플로 상태는 없다 — FDS는 "실제 전송"
  단계가 없고 운영자 조회가 최종 목적지라 그럴 필요가 없다.
- `fdsDetectionJob`(K8s CronJob, 매일 04시)이 하루치 완료된 TRANSFER 거래를
  한 번에 읽어 세 룰을 자바에서 계산한다. "짧은 시간 내 반복"은 계좌별
  슬라이딩 윈도우가 필요해 SQL 집계만으로는 안 되고, 트랜잭션을
  `(fromAccountId, processedAt)` 순으로 미리 정렬해 받아 한 번의 순회로
  계좌 그룹이 바뀌는 지점마다 윈도우를 리셋하는 방식으로 처리했다.
- 심야 시간(23시~06시) 판정은 `ZoneId.of("Asia/Seoul")`로 고정했다 —
  컨테이너 기본 타임존(대개 UTC)에 맡기면 배포 환경에 따라 "심야"의 의미가
  달라진다. 다른 배치 잡들이 `ZoneId.systemDefault()`를 쓰는 것과 다른
  선택인데, 이 잡의 룰 자체가 "한국 은행 영업 기준 심야"라는 도메인 의미를
  담고 있어 서버 로케일에 의존하면 안 된다고 판단했다(다른 잡들은 날짜
  경계만 다루지 시각 자체의 도메인 의미는 없어 지금까지는 괜찮았다).
- API-022(`GET /admin/suspicious-transactions`)는 `AuditLogController`와
  똑같이 "운영자 역할 모델이 아직 없어 인증된 고객이면 누구나 호출 가능"
  상태로 남겨뒀다(`ponytail:` 표시). 새로 발명하지 않고 이미 있는 선례를
  따랐다.
- 자동 차단·거래 취소는 하지 않는다 — 탐지해서 조회 가능하게 적재까지가
  요구사항명세서가 정한 범위다(인프라아키텍처 문서 11절에 실제 은행
  FDS와의 규모 차이가 이미 명시돼 있다).

## 근거

- 임계금액(단일 500만원, 심야 300만원)과 반복 기준(5분·3건)은 CTR의
  1천만원 기준과 별개로 임의로 정한 값이다 — 실제 이상거래 통계 없이
  demo 스케일에서 룰이 실제로 걸리는 걸 보여주는 게 목적이라, 값 자체보다
  세 룰이 각각 독립적으로 정확히 판별되는지가 중요하다고 봤다. 전부
  `application.yml`(`jbank.batch.fds.*`)로 빼서 나중에 조정 가능하다.
- CTR과 마찬가지로 `(transaction_id, rule_type)` 유니크 인덱스로 재실행
  안전성을 보장한다.

## 영향

- 신규: `com.jbank.support.fds.*`(도메인·리포지토리·서비스·컨트롤러),
  `com.jbank.batch.fds.*`(탐지 잡), `V15__create_suspicious_transactions.sql`.
- `TransactionRepository`에 FDS 전용 조회 메서드 1개 추가.
- Helm `batchJobs`에 `fdsDetectionJob` 추가(매일 04시, CTR·이자 잡과 겹치지
  않는 시간대).
