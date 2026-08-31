# 성능 측정 규약

구현계획 문서 7.5절 근거. W2부터 매주 같은 조건으로 이체 엔드포인트 부하 테스트를 돌리고
결과를 이 디렉터리에 커밋한다. 목적은 두 가지다. 하나는 인덱스도 캐시도 없는 상태의 진짜
개선 전 수치를 남기는 것이고, 다른 하나는 어느 주차의 어떤 변경이 성능을 떨어뜨렸는지 즉시
잡아내는 회귀 감지다.

## 기준선

요구사항명세서 NFR-PERF-001: 이체 200ms 이내, 초당 100건 처리.

## 실행 방법

1. 로컬 인프라 core 프로파일 기동: `scripts/dev.sh core`
2. 백엔드 기동: `apps/jbank-api`에서 `./gradlew bootRun --args='--spring.profiles.active=local'`
3. 고객 등록(API-001) → 같은 고객으로 계좌 개설(API-002) 두 번 → 두 계좌 모두 입금(API-006)
   으로 이체에 쓸 두 계좌를 시드한다. 이체 총량보다 훨씬 큰 잔액을 넣어야 부하 테스트 도중
   출금 가능 금액 부족으로 실패하지 않는다. 반드시 같은 고객 소유 두 계좌여야 한다 — k6
   스크립트가 방향을 번갈아 보내므로 두 계좌 다 발신 계좌가 되고, 로그인은 한 번만 한다.
4. `scripts/perf.sh <라벨> <로그인ID> <비밀번호> <출금계좌번호> <입금계좌번호>` 실행. 결과가
   `perf/results/<날짜>-<라벨>.json`, `.log`로 저장된다.

W3부터 이체 API가 인증을 요구한다(JWT 쿠키 + CSRF 이중제출, `SecurityConfig`). k6
스크립트의 `setup()`이 로그인해서 받은 쿠키·CSRF 토큰을 매 요청에 수동으로 실어 보낸다 —
`access_token` 쿠키에 `Secure` 속성이 있어 k6 기본 쿠키 저장소가 `http://` 로컬 실행에서
이를 되돌려보내지 않기 때문이다. `--summary-export` JSON에는 `setup()` 반환값(로그인
토큰)이 그대로 직렬화되므로, `perf.sh`가 저장 직후 `setup_data`를 지운다.

## 측정 조건 고정 규약

비교가 성립하려면 매주 다음 조건을 동일하게 고정하고 결과 파일 옆에 함께 기록한다. 관측
스택(Prometheus/Grafana/Loki)이 떠 있는지 여부만으로도 수치가 흔들리므로 마지막 항목이
특히 중요하다.

| 조건 | W2 값 | W5 값 |
|---|---|---|
| 데이터 건수 | 계좌 2개, 거래내역 사실상 0건(인덱스 없는 상태의 최초 측정) | 계좌 2개, 거래내역 사실상 0건(고객·계좌 시퀀스는 이전 로컬 세션분이 남아 있으나 거래내역 자체는 이번 시드 4건뿐) |
| 인스턴스 수 | 1 (로컬 단일 프로세스) | 1 (로컬 단일 프로세스) |
| 컨테이너 자원 제한 | 없음(로컬 Docker Desktop 기본값, 개발 머신 자원 그대로) | 없음(동일) |
| 떠 있는 Compose 프로파일 | `core`만 (PostgreSQL, Redis) — messaging, observability 없음 | `core`만 (동일) |
| k6 실행 방식 | `constant-arrival-rate`, 100 iterations/s, 30초, preAllocatedVUs 50 | 동일 |
| 인증 | 없음(보안 필터체인 붙기 전) | JWT 쿠키 + CSRF 이중제출(W3부터 필수, `setup()`에서 로그인) |

## W2 베이스라인 결과

`2026-07-31-w2-baseline.json` / `.log`. 총 3000건, 100.00 iterations/s로 30초간 유지, 실패
0건.

| 지표 | 값 |
|---|---|
| http_req_duration p95 | 15.3ms |
| http_req_duration max | 158.47ms |
| http_req_failed | 0.00% |
| 처리량 | 99.996973 req/s |

NFR-PERF-001 기준(200ms, 초당 100건)을 큰 여유로 통과했다. 인덱스도 캐시도 없는 초기
구현치고 이미 여유가 큰 이유는 계좌 2개, 거래내역 0건인 최소 데이터 규모 때문이다. 이후
주차에 데이터 건수를 늘려가며 같은 조건으로 재측정해야 실제 병목이 드러난다.

## W5 재측정 결과

W3~W4 구간은 재측정을 걸러뛰었다(누락). `2026-08-17-w5-baseline.json` / `.log`. 총
3002건(체크 3002건 포함, iteration 3001건), 99.69 iterations/s로 30초간 유지, 실패 0건.

| 지표 | W2 값 | W5 값 |
|---|---|---|
| http_req_duration p95 | 15.3ms | 7.74ms |
| http_req_duration max | 158.47ms | 103.44ms |
| http_req_failed | 0.00% | 0.00% |
| 처리량 | 99.996973 req/s | 99.689829 req/s |

W3에서 JWT 인증과 CSRF 이중제출 검증이 이체 API에 추가됐는데도 지연시간이 W2보다
줄었다. 이번 측정은 K6가 매 요청 로그인·CSRF 검증 오버헤드를 추가로 지는데도 이 결과가
나온 것이라, W2와 W5 사이의 차이를 "인증 오버헤드가 없다"로 해석할 근거는 아니다 —
데이터 건수(여전히 거래내역 사실상 0건)와 측정 조건이 동일해 병목 자체가 아직 드러나지
않는 구간이고, JIT 워밍업이나 개발 머신의 순간 부하 차이가 더 컸을 수 있다. 여전히
계좌 2개·거래내역 0건 규모라 실질적인 병목 측정은 W6에 10만 건 데이터로 재실행해야
의미가 생긴다(`perf/README.md`의 W4 EXPLAIN 벤치마크 절차, `todo/W6.md` 화요일분).

## W6 재측정 결과

`2026-08-21-w6-baseline.json` / `.log`. W2·W5와 같은 측정 조건 유지(계좌 2개,
거래내역 사실상 0건 — 이체 엔드포인트 자체는 거래내역 테이블 규모와 무관하므로
아래 EXPLAIN 벤치마크용 10만 건 시드 이전에 측정). 총 3002건(체크 3002건, iteration
3001건), 99.73 iterations/s로 30초간 유지, 실패 0건.

| 지표 | W2 값 | W5 값 | W6 값 |
|---|---|---|---|
| http_req_duration p95 | 15.3ms | 7.74ms | 8.34ms |
| http_req_duration max | 158.47ms | 103.44ms | 89.27ms |
| http_req_failed | 0.00% | 0.00% | 0.00% |
| 처리량 | 99.996973 req/s | 99.689829 req/s | 99.728139 req/s |

W5 대비 p95가 소폭(7.74ms → 8.34ms) 늘었지만 오차 범위 안이다 — W2~W6 구간에
이체 경로 자체에 추가된 로직이 없다(감사 로그·발신함은 이벤트 리스너로 비동기
분리, 2차 인증은 임계 금액 미만 이체엔 관여 안 함). NFR-PERF-001 기준(200ms,
초당 100건) 대비 20배 이상 여유. 여전히 거래내역 0건에 가까운 최소 데이터
규모라 이체 API 자체의 병목은 이 측정으로는 드러나지 않는다 — 아래 EXPLAIN
벤치마크가 실제 데이터 규모에서 드러나는 병목이다.

## W4 거래내역 조회 EXPLAIN 벤치마크 (W6 화요일 실행 완료)

구현계획 문서 182행: 거래내역 조회(API-010)에 10만 건을 채운 뒤, 인덱스를 걸기
전 상태의 응답 시간과 실행 계획을 W6 최적화 근거로 기록한다.

### 실행 절차

1. 로컬 인프라 core 프로파일 기동, 백엔드 기동(위 실행 방법과 동일)
2. `psql "$DATABASE_URL" -f perf/sql/seed-100k-transactions.sql` — 트랜잭션 10만 건 적재
   (계좌는 시연용 1개 + 로그인 계정 소유 2개, 총 3개 계좌 풀에서 무작위 배정)
3. 응답 시간: `curl -w '%{time_total}\n' -o /dev/null -s -b cookies.txt \
   'http://localhost:8080/api/v1/accounts/{accountId}/transactions?page=0&size=20'` 반복 실행
4. 실행 계획: `psql`에서 아래 EXPLAIN ANALYZE 실행

```sql
EXPLAIN ANALYZE
SELECT *
FROM transactions
WHERE from_account_id = :accountId OR to_account_id = :accountId
ORDER BY transaction_id DESC
LIMIT 20;
```

### 시드 스크립트 버그와 수정

최초 실행 결과 10만 행 전부가 동일한 (거래유형, 계좌쌍) 값으로 들어갔다. 원인은
`perf/sql/seed-100k-transactions.sql`의 `LATERAL` 서브쿼리가 바깥 `generate_series`의
`g`를 전혀 참조하지 않는다는 것 — PostgreSQL은 상관관계 없는 `LATERAL` 서브쿼리를
행마다가 아니라 한 번만 평가해 재사용한다(VOLATILE 함수인 `random()`도 예외 없음).
`WHERE g >= 1`(항상 참, `g` 참조만이 목적)을 추가해 매 행 재평가를 강제하도록
고쳤다. 잘못 적재된 10만 행을 지우고 수정한 스크립트로 재적재해 아래 결과를 얻었다.

### 결과

계좌 3개 풀(시연용 1개 + 이번 벤치마크용 로그인 계정 소유 2개)에 10만 건 적재 후
`account_id=2` 기준 거래유형 분포: TRANSFER 36470, WITHDRAWAL 33244, DEPOSIT 33289,
`account_id=2`가 걸리는 행 43545건(약 42%).

응답 시간(연속 5회, `page=0&size=20`): 40.6ms(첫 요청, 캐시 워밍업 포함), 이후
12.8~15.4ms로 안정.

```
Limit  (cost=0.42..4.02 rows=20 width=95) (actual time=0.022..0.028 rows=20 loops=1)
  ->  Index Scan Backward using transactions_pkey on transactions
        (cost=0.42..7544.51 rows=41940 width=95) (actual time=0.020..0.026 rows=20 loops=1)
        Filter: ((from_account_id = 2) OR (to_account_id = 2))
        Rows Removed by Filter: 22
Planning Time: 0.285 ms
Execution Time: 0.055 ms
```

인덱스가 하나도 없는데 `transactions_pkey`(PK, `transaction_id`)를 역순으로 훑으며
필터를 거는 계획이 나왔다 — Seq Scan을 기대했는데 아니다. `ORDER BY transaction_id
DESC LIMIT 20`이 있어서 옵티마이저가 "PK 역순으로 훑다가 필터에 맞는 20건 채우면
멈춘다"는 전략을 Seq Scan+정렬보다 싸다고 판단한 것. 이 계좌 풀에서는 계좌당 매칭률이
약 42%로 높아 22건만 더 보고 멈췄으니 빠르다(0.055ms). 이 전략의 위험은 매칭률이
낮아질 때 드러난다 — 계좌 수가 늘어 계좌당 매칭률이 1% 근처로 떨어지면 20건을 채우기
위해 PK 역순으로 수천~수만 건을 훑어야 할 수 있다(아래 병목 후보 참고).

## W6 화요일분 병목 후보 정리

`todo/W6.md` 후보군 중 이번 측정으로 실제 근거가 확인된 것과 아닌 것을 구분한다.
구현계획 7.5절 원칙대로, 근거 없는 항목은 반영하지 않는다.

### 근거 확인됨 — 다음 병목 수정 사이클 대상

- **거래내역 조회 복합 인덱스 부재**: 위 EXPLAIN 결과가 근거. 현재 계좌당 매칭률이
  약 42%라 PK 역순 스캔이 그럭저럭 버티지만, 계좌 수가 늘어 매칭률이 낮아지면
  이 전략의 비용이 매칭률에 반비례해 커진다. `from_account_id`, `to_account_id`
  각각에 `(계좌id, transaction_id DESC)` 복합 인덱스를 걸고 두 인덱스 스캔 결과를
  합치는 형태(UNION 기반 쿼리로 재작성 필요 — 현재 `OR` 조건은 옵티마이저가
  두 인덱스를 함께 못 씀)로 바꾸는 안이 유력. 다음 사이클에서 실제 반영 후
  재측정한다.

### 근거 불충분 — 반영 보류

- **이체 엔드포인트(JPA N+1 / 커넥션 풀 / Redis 캐시 / 발신함 폴링 주기 / 청크
  크기 / 파티셔닝 / 가상 스레드 / 로그 레벨)**: W6 재측정에서 p95 8.34ms로
  기준(200ms) 대비 20배 이상 여유, W2·W5 대비 유의미한 악화 없음. 계좌 2개·
  거래내역 사실상 0건 조건에서 병목이 드러나지 않으므로 지금 손댈 근거가 없다.
  거래내역 규모를 키운 상태로 이체 흐름 자체를 재측정하기 전엔 보류.

### 다음 사이클 절차

1. 위 복합 인덱스 안 반영 (Flyway 마이그레이션 1건)
2. EXPLAIN ANALYZE 재실행, 동일 계좌·동일 LIMIT 20 조건으로 비교
3. 매칭률이 낮은 계좌(예: 새로 만든 거래내역 0~1건 계좌)에서도 비교 — 현재
   측정은 매칭률 42%로 유리한 조건이라 인덱스 유무 차이가 작게 나올 수 있음
4. 트레이드오프 포함해 결정 기록 (인덱스 쓰기 비용, 저장공간)

## 복합 인덱스 반영 후 재측정 (V13 마이그레이션)

`V13__add_transaction_account_composite_indexes.sql` — `idx_transactions_from_account_id_transaction_id`,
`idx_transactions_to_account_id_transaction_id` 두 개를 `(계좌id, transaction_id DESC)`로 추가했다.
`ANALYZE transactions` 실행 후 동일 계좌(`account_id=2`)·동일 조건으로 EXPLAIN ANALYZE 재실행.

```
Limit  (cost=0.42..4.05 rows=20 width=95) (actual time=0.032..0.036 rows=20 loops=1)
  ->  Index Scan Backward using transactions_pkey on transactions
        (cost=0.42..7544.51 rows=41535 width=95) (actual time=0.031..0.035 rows=20 loops=1)
        Filter: ((from_account_id = 2) OR (to_account_id = 2))
        Rows Removed by Filter: 22
Planning Time: 0.536 ms
Execution Time: 0.069 ms
```

**결과: 계획이 그대로다.** 새 인덱스가 존재해도 옵티마이저는 여전히 `transactions_pkey`
역순 스캔+필터를 고른다 — 실행시간도 이전(0.055ms)과 오차범위 안(0.069ms)이다. 예상과
다른 결과라 원인을 추가로 확인했다.

### UNION 재작성도 시도해봤는데 더 느려졌다

새 인덱스를 실제로 타게 하려면 `OR` 조건을 두 개의 `SELECT ... WHERE 계좌id = ? ORDER BY
transaction_id DESC LIMIT 20`로 쪼개 `UNION`으로 합치는 재작성이 필요하다고 판단했었다.
실제로 그렇게 쿼리를 짜서 psql로 직접 실행해봤다:

```
Limit (cost=15.94..15.99 rows=20 width=740) (actual time=0.319..0.322 rows=20 loops=1)
  -> Sort (cost=15.94..16.04 rows=40 width=740) (actual time=0.318..0.320 rows=20 loops=1)
    -> HashAggregate (UNION 중복제거, actual time=0.237..0.243 rows=38 loops=1)
      -> Append (actual time=0.086..0.158 rows=40 loops=1)
        -> Limit -> Index Scan Backward using transactions_pkey  Filter: (from_account_id = 2)  (actual 0.085..0.097, rows=20)
        -> Limit -> Index Scan Backward using transactions_pkey transactions_1  Filter: (to_account_id = 2)  (actual 0.020..0.056, rows=20)
Execution Time: 1.222 ms
```

여기서도 옵티마이저가 새 복합 인덱스를 안 쓰고 각 절반 쿼리마다 PK 역순 스캔을 골랐다 —
`from_account_id=2` 단독 매칭률도 여전히 약 20%(23454/103003)라 PK 스캔이 더 싸다고 판단한
것. 게다가 `UNION`의 중복제거(HashAggregate)와 병합 정렬(Sort) 오버헤드가 더해져 원래
쿼리(0.069ms)보다 18배 느린 1.222ms가 나왔다.

### 결정

- **인덱스는 남긴다, 쿼리는 안 바꾼다.** 복합 인덱스는 지금 당장 아무것도 개선 안 하지만
  쓰기 비용도 이 데이터 규모(10만 건)에서 무시할 만하고, 계좌당 매칭률이 실제로 낮아지는
  시점(계좌 수가 훨씬 많아지는 시점)엔 옵티마이저가 자동으로 선택지에 넣을 후보가 된다 —
  인덱스가 없으면 그 시점에 골라 쓸 방법 자체가 없다.
- **UNION 재작성은 반려한다.** 코드 복잡도(Specification 기반 동적 필터 조합을 native
  UNION 쿼리로 다시 짜야 함)를 늘리면서 지금 데이터 규모에서는 오히려 18배 느려지는 걸
  실측으로 확인했다. `todo/W6.md` 애초 후보 정리에서 "계좌 수가 늘어 매칭률이 낮아지면"을
  전제로 삼았는데, 로컬 계좌 3개로는 그 전제 자체를 재현할 수 없다 — 실제 검증하려면
  계좌를 수백~수천 개로 늘린 별도 시나리오가 필요하고, 지금 근거 없이 반영하지 않는다는
  원칙(구현계획 7.5절)에 따라 보류한다.
- **다음에 재검토할 조건**: 실 서비스 계좌 수가 늘어난 스테이징/운영 데이터로 같은
  EXPLAIN을 다시 돌렸을 때 PK 역순 스캔의 `Rows Removed by Filter`가 지금(22건)보다
  훨씬 커지는 게 확인되면 그때 UNION 재작성을 다시 꺼낸다.

## W7 무중단 배포(롤링 업데이트) 검증

W2·W5·W6과 측정 조건이 다르다 — 로컬 단일 프로세스가 아니라 실제 EKS
(jbank-dev 네임스페이스) `jbank-api-dev` Deployment(replicaCount=1,
terminationGracePeriodSeconds=30, `server.shutdown=graceful` +
`timeout-per-shutdown-phase=20s`) 대상이라 비교표에 넣지 않는다. 목적도
다르다 — 처리량 재측정이 아니라 "롤링 업데이트 도중 실패 요청이
0건인가"(이번 주 완료 기준의 절반)를 확인하는 것이다.

`kubectl port-forward`는 특정 파드에 고정돼 롤링 업데이트 도중 엔드포인트
전환을 반영하지 못한다는 걸 이 과정에서 확인했다 — 검증 도구로 못 쓴다.
대신 k6를 클러스터 내부 Job(공식 `grafana/k6` 이미지)으로 실행해 실제
kube-proxy 로드밸런싱을 거쳐 Service(ClusterIP) DNS로 접근했다. 부하도
낮췄다(15 iterations/s, 300초) — dev 환경 리소스 요청량(250m CPU)이 작아
baseline 강도(100 iterations/s)로는 무중단 여부와 무관한 자원 경합이
결과를 흐리기 때문이다.

절차: k6 Job 시작(t=0) → 15초 후 새 이미지 태그로 ArgoCD 강제 동기화
트리거 → 새 파드 생성·Ready 전환·이전 파드 Terminating까지 전체 롤아웃
수명주기가 이 300초 창 안에 들어옴(새 파드 Ready t≈5m40s 무렵, 이전 파드
Terminating이 k6 종료 직전 관측됨).

### 결과

총 4502건 요청, **실패 0건**(`http_req_failed` 0.00%). k6 임계값 둘 다
통과: `http_req_duration p(95)<200` → 실측 32ms, `http_req_failed rate<0.01`
→ 실측 0.00%.

| 지표 | 값 |
|---|---|
| 총 요청 | 4502건 |
| 실패율 | 0.00% |
| p95 | 32ms |
| 처리량 | 14.98 req/s |

롤링 업데이트 전체 수명주기(새 파드 생성 → Ready → 이전 파드 Terminating)가
측정 창 안에 들어갔고 그 구간 포함 실패 요청이 0건이었다 — W7 완료
기준("배포 중 부하 스크립트 실패 요청 0건")을 실제 클러스터 배포로
충족했다.

### 검증 중 발견한 dev 노드 용량 제약

이 검증을 준비하며 두 번의 시도가 노드 파드 용량 부족(`0/1 nodes are
available: 1 Too many pods`, node max-pods=17)으로 서지(surge) 파드가
스케줄 자체를 못 받았다 — ArgoCD(7)·External Secrets Operator(3)·
kube-system(4)·jbank-dev 워크로드가 이미 17개를 채우고 있어 롤링
업데이트가 필요로 하는 +1 서지 여유가 전혀 없었다. 완료된 CronJob 파드
정리와 CoreDNS 레플리카를 일시적으로 2→1로 줄여 슬롯을 확보해 검증을
진행했고, 검증 직후 원복했다. 실제 운영에서는 노드그룹을 최소 2대
이상으로 둬야 이 문제가 안 생긴다(인프라아키텍처 문서의 상시구동
사양이 이미 이걸 전제한다) — dev의 `demo` 워크스페이스가 노드 1대로
축소하는 비용 절감 설정과 서지 여유가 서로 충돌하는 지점이라는 걸
이번에 실측으로 확인했다.
