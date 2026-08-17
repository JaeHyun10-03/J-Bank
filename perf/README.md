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

## W4 거래내역 조회 EXPLAIN 벤치마크 (실행 대기)

구현계획 문서 182행: 거래내역 조회(API-010)에 10만 건을 채운 뒤, 인덱스를 걸기
전 상태의 응답 시간과 실행 계획을 W6 최적화 근거로 기록만 해둔다. 이 세션은
로컬에 Postgres가 없어 아래 절차만 준비하고 실행·기록은 다음에 로컬에서 한다.

### 실행 절차

1. 로컬 인프라 core 프로파일 기동, 백엔드 기동(위 실행 방법과 동일)
2. `psql "$DATABASE_URL" -f perf/sql/seed-100k-transactions.sql` — 트랜잭션 10만 건 적재
   (계좌는 `SeedDataRunner`가 만든 2개를 그대로 씀)
3. 응답 시간: `curl -w '%{time_total}\n' -o /dev/null -s -H "Cookie: ..." \
   'http://localhost:8080/api/v1/accounts/{accountId}/transactions?page=0&size=20'` 반복 실행
4. 실행 계획: `psql`에서 아래 EXPLAIN ANALYZE 실행 후 결과를 이 파일에 붙여넣는다.

```sql
EXPLAIN ANALYZE
SELECT *
FROM transactions
WHERE from_account_id = :accountId OR to_account_id = :accountId
ORDER BY transaction_id DESC
LIMIT 20;
```

인덱스 없는 상태이므로 Seq Scan이 나오는 게 기대값이다. 실제 반영은 W6에서
측정치와 함께 인덱스를 설계한다.
