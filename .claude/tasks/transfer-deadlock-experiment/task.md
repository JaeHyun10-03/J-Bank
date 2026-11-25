# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 교착 실험 시작
(앞선 대화) 포트폴리오의 수정 전·후 근거가 부족하다. 교착이 있어야 정렬해서 락을 건다는 게 어필된다. 거짓이 아니라 실험이다. "어떤 로그를 보고 교착을 발견했고, 그래서 해결했다"가 중요하다. 관측 스택(observability-logs-alerts)을 먼저 만들고 그 위에서 실험한다.
요청 해석: 정렬 없는 락 순서로 양방향 동시 이체 교착을 일부러 재현하고, 교착이 알림·DB 로그·지표로 어떻게 드러나는지 남긴 뒤, 락 순서 정렬(과 현재 락 모드)로 사라지는 것을 같은 조건의 수치로 비교한다. 운영 장애를 겪은 것이 아니라 재현 실험이라는 점을 기록에 분명히 한다.
조사한 사실:
- `TransferService.transfer`는 계좌번호 오름차순으로 두 계좌를 잠근다(FR-TXN-003). `completeAfterOtp`는 계좌 ID 순서다. 정렬은 첫 구현부터 있었다. 즉 "정렬 없는 FOR UPDATE" 버전은 이 저장소에 존재한 적이 없다.
- ADR 0012 이후 송금 계좌는 `FOR NO KEY UPDATE`, 수신 계좌는 `FOR KEY SHARE`다. 두 모드는 서로 충돌하지 않아, 이체끼리는 정렬이 없어도 교착이 나지 않는다. 지금 코드는 교착 방지 장치가 두 겹이다.
- `ConcurrentBidirectionalTransferScenarioTest`는 현재 코드로 A→B·B→A 50건씩(스레드 20)이 모두 성공하고 잔액 합이 보존되는지만 본다. 교착 횟수 기록이나 조건 비교는 없다.
- 로컬 관측 스택: postgres `log_lock_waits=on`, postgres-exporter(`pg_stat_database_deadlocks`), `DeadlockDetected` 알림 → mailpit, Loki에서 postgres 로그 조회(runbook 7절의 여러 줄 조회식).
- 부하 도구: `perf/k6/transfer.js`가 같은 고객의 두 계좌로 방향을 번갈아 이체한다(`perf/run-transfer.sh`).
- 동시성 테스트 기반 `AbstractConcurrencyTest`는 Testcontainers PostgreSQL 16을 쓴다.
- (2026-11-23 추가) 운영 실험 고객을 만들려던 사용자가 `https://www.j-bank.site/signup/pin`에서 가입 실패를 겪었다. 원인: 프론트 프록시(`apps/frontend/app/api/proxy/[...path]/route.ts`)가 브라우저 요청 헤더를 그대로 넘겨 `Origin: https://www.j-bank.site`가 API에 닿고, API CORS 허용 목록(`SecurityConfig.corsConfigurationSource`)은 `http://localhost:3000`, `https://api.j-bank.site`뿐이라 403 `Invalid CORS request`가 난다. 운영에서 빈 본문 요청으로 확인: Origin 없음·`https://api.j-bank.site`는 400 검증 오류(정상 처리), `https://www.j-bank.site`·`https://j-bank.site`는 가입·로그인 모두 403(계정 생성 없음). 프론트 DNS는 `j-bank.site`, `www.j-bank.site` 모두 Vercel. 이 차단이 풀려야 운영 실험(REQ-07)을 할 수 있다.
질문하지 않은 이유: 해당 없음. 제품 동작·운영 영향이 있는 선택은 Q-01~Q-06으로 물었다. Q-07·Q-08·Q-11~Q-16도 사용자에게 물었다. Q-09·Q-10은 선택지가 아니라 기존 구성·안전 규칙에서 정해지는 사항이라 근거만 적었다.

원문 요청의 첫 줄은 콜론 뒤에 적고, 길면 이어지는 줄에 계속 적는다. 개인정보·비밀값은 문서에 복사하지 않고 위치와 필요한 의미만 남긴다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 재현 환경. 운영 재현이면 운영 DB에 실제 이체 실패·알림이 생긴다 | "운영에서도" 선택. JUnit 테스트 + 로컬 관측 스택 + 운영 세 곳에서 재현한다. devops.md의 "운영 변경 점검은 격리 환경에서" 원칙에 대한 사용자 승인 예외다 | 해결 |
| Q-02 | 수정 전 코드 보관 방식. 메인 코드에 넣으면 운영에 교착 경로가 생긴다 | "설정값으로 켜고 끄기" 선택. 메인 코드에 락 전략 설정을 넣는다 | 해결 |
| Q-03 | 비교 조건 수 | "세 조건" 선택. ① 정렬 없음 + FOR UPDATE ② 정렬 + FOR UPDATE ③ 현재 코드(정렬 + NO KEY UPDATE/KEY SHARE) | 해결 |
| Q-04 | 운영 DB에 남는 실험 흔적 | "진짜로 에러가 터질 수 있게 해. 포폴용 서버·DB라 문제 생겨도 DB 다시 밀면 된다. 현업처럼 심각하게 나오도록 유도해." 운영 DB 흔적·이체 실패를 수용한다. 전용 테스트 고객 한 명과 계좌 두 개로 실험한다 | 해결 |
| Q-05 | 운영 부하 크기. 과거 부하+배치로 가용 메모리 127MB, 50분 무응답 사고가 있었다 | "동시에 50~100개씩 해야 하는 거 아냐?" 운영·로컬 모두 동시 50과 100 두 단계로 건다. 서버 무응답이 나도 중단 기준으로 삼지 않고 기록한 뒤 EC2 중지·시작으로 복구한다. 무응답으로 끝난 회차도 실행 1회로 세고 결과를 "무응답"으로 기록한다(재실행하지 않음) | 해결 |
| Q-06 | 실험 후 설정 코드 처리 | "기본 꺼짐으로 유지". 기본값은 현재 코드(③)이고 설정을 바꿀 때만 ①·②가 된다 | 해결 |
| Q-07 | 교착 예외(40P01)의 API 응답 | "그대로 500" 선택. 지금처럼 일반 500(COMMON_006)을 준다. 재시도·전용 오류 코드는 다음 작업으로 남긴다 | 해결 |
| Q-08 | 설정이 적용되는 경로 | "OTP 완료 경로까지" 선택. 즉시 이체(`transfer`)와 OTP 완료(`completeAfterOtp`) 모두 설정을 따른다. OTP 완료의 정렬 기준은 지금처럼 계좌 ID다. 실험 부하는 OTP 임계금액(1천만 원) 미만 즉시 이체만 쓴다 | 해결 |
| Q-09 | 운영 설정 변경 방법 | 근거: api는 `.env`를 `env_file`로 통째로 받고 설정은 기동 때 읽는다(observability runbook 2절과 같은 방식). 따라서 운영 `.env`의 `JBANK_TRANSFER_LOCK_STRATEGY`를 바꾸고 api만 재생성한다(조건 바꿀 때마다 수 초 중단). 실험 뒤 그 줄을 지우고 기본값 복귀를 확인한다 | 해결 |
| Q-10 | 운영 테스트 고객·비밀번호 | 근거: 에이전트 안전 규칙상 운영 서비스의 계정 생성·비밀번호 입력은 하지 않는다. 따라서 운영 고객 가입·계좌 개설·입금과 부하 실행 때 비밀번호 입력은 사용자가 직접 한다. 운영 Grafana·Gmail 화면 캡처도 사용자가 한다. 로컬은 에이전트가 테스트 고객을 만든다 | 해결 |
| Q-11 | 운영 설정 변경의 실행 주체 | 처음엔 "Claude가 SSM으로"를 골랐으나, 비밀번호 입력이 필요한 운영 회차와 한 흐름이어야 해서 Q-13으로 바뀌었다. 실험 스크립트가 SSM으로 `.env`의 그 한 줄만 바꾸고 api를 재생성한다. 실험이 중단된 뒤의 복구·확인은 에이전트가 SSM으로 한다(이체 1건은 Q-10에 따라 사용자가 `prod-restore-check`로). 어느 쪽이든 다른 값은 출력하지 않는다(REQ-07 조회 규칙) | 해결 |
| Q-12 | 실험 이체(2만~5만 건 예상)가 다음 부팅 FDS 배치에 주는 영향. `FdsDetectionTasklet`이 하루치 완료 이체를 한 번에 읽고 거의 전부 반복 이체로 기록한다 | "수용하고 관찰" 선택. 요청 수 상한은 두지 않는다. 다음 부팅 때 FDS 배치의 결과·소요 시간·최저 가용 메모리를 기록한다(REQ-09). 운영 DB에 남는 의심 거래 기록도 수용한다. 관찰 방법은 Q-14 | 해결 |
| Q-13 | 운영 회차 실행 방식(비밀번호는 에이전트가 입력할 수 없음, access token 15분) | "스크립트 한 번 실행" 선택. 사용자가 자기 터미널에서 `perf/run-deadlock.sh prod-all`을 실행하고 비밀번호를 `read -s`로 한 번 입력한다. 스크립트가 6회 전체(전략 적용 SSM → DB 기록 SSM → 워밍업·측정 k6, 회차마다 다시 로그인)를 사용자 AWS 자격증명으로 돌린다. 비밀번호는 셸 변수에만 두고 파일·인자·로그에 남기지 않는다. 에이전트는 진행을 지켜보고 결과를 정리한다 | 해결 |
| Q-14 | FDS 배치 관찰 방법(운영엔 노드 메모리 지표가 없고 부팅 배치는 자동 실행) | "실험 당일 직접 실행" 선택. 운영 실험이 끝난 뒤 18시 전에 에이전트가 SSM으로 1초 간격 MemAvailable 표본을 켜고 `run-batch.sh fdsDetectionJob --run-date <실험일>`을 실행한다. 다음 부팅의 따라잡기는 그 날짜를 건너뛴다(REQ-09는 "다음 부팅"이 아니라 "실험 당일 수동 실행"을 관찰한다) | 해결 |
| Q-15 | 운영 실험이 18시를 넘겨 이틀에 걸칠 때 | "그날 서버 연장" 선택. 17:40에 남은 일(남은 회차 + FDS 관찰)이 있으면 에이전트가 그날만 중지 스케줄 `jbank-dev-app-stop`(운영 인스턴스의 Terraform 환경 이름이 dev)을 끄고, 실험·FDS 관찰과 설정 복귀가 끝나면 다시 켠 뒤 EC2를 직접 중지한다. 방법: 끄기 전 `aws scheduler get-schedule` JSON 전체를 저장 → 그 정의(일정 식, 시간대 `Asia/Seoul`, 시간대 창, 대상·역할 ARN)에서 State만 DISABLED로 바꿔 `update-schedule`에 넣음 → 켤 때도 같은 정의에 State ENABLED → 다시 `get-schedule` 결과를 저장본과 diff해 State 외 차이 0을 기록. update에 넣을 때와 diff할 때 읽기 전용 항목(Arn, CreationDate, LastModificationDate)은 뺀다. 실험일 전에 `get-schedule` 실행으로 권한을 미리 확인한다. 사용자 자격증명의 SSM 권한도 실험일 전에 읽기 전용 SSM 명령(`echo ok`)으로 확인한다 | 해결 |
| Q-16 | 운영 프론트 가입·로그인 CORS 차단(조사한 사실 마지막 항목) 해결 방식과 작업 위치. 프록시에서 Origin을 지우면 프론트 배포만, 허용 목록에 추가하면 api 재배포가 필요하다 | "허용 목록에 추가", "교착 작업에 포함" 선택. API CORS 허용 목록에 `https://www.j-bank.site`, `https://j-bank.site`를 더한다. 기존 두 출처는 유지하고 와일드카드·Vercel 미리보기 주소는 넣지 않는다. 운영 실험의 전제 수정으로 이 작업 범위에 넣고, 새 브랜치 PR로 먼저 병합·배포한 뒤 운영 실험을 한다 | 해결 |

## 목표

정렬 없는 락 순서가 양방향 동시 이체에서 교착을 만들고, 그 교착이 알림 메일·DB 로그·지표로 드러나며, 락 순서 정렬과 현재 락 모드로 사라진다는 것을 같은 조건의 실험 수치로 남긴다. 결과는 포트폴리오의 수정 전·후 근거로 쓴다.

## 범위

- 이체 락 전략 설정(기본 = 현재 코드, 즉시 이체·OTP 완료 두 경로), 기동 로그
- 로컬 compose api에 전략 환경변수 전달 한 줄(`JBANK_TRANSFER_LOCK_STRATEGY`, 기본 `current`). 운영 compose는 `.env`를 이미 통째로 넘기므로 변경 없음
- 락 전략별 통합 테스트(Testcontainers PostgreSQL 16), 교착 예외 응답 테스트
- 교착 실험용 k6 스크립트, 실행 스크립트, 결과 요약 스크립트
- 로컬 관측 스택 실험(조건 3 × 동시 50·100 × 3회), 운영 실험(조건 3 × 동시 50·100 × 1회), 실험 당일 FDS 배치 수동 실행 관찰
- 실험 절차 runbook, 결과 보고서, 개발일지
- 운영 실험 전제 수정(Q-16): API CORS 허용 목록에 운영 프론트 출처 두 개 추가와 그 테스트

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 락 전략 설정이 없거나 빈 값이거나 `current` | 지금과 똑같이 즉시 이체는 계좌번호, OTP 완료는 계좌 ID 오름차순으로 송금 `FOR NO KEY UPDATE`, 수신 `FOR KEY SHARE`를 잡는다. 기존 테스트가 모두 통과한다. 동시 양방향 이체에서 교착 0건 |
| REQ-02 | `unsorted-for-update` | 즉시 이체·OTP 완료 모두 요청 방향대로 송금 계좌 → 수신 계좌 순서로 둘 다 SQL `FOR UPDATE`를 잡는다. A→B·B→A 동시 이체(OTP 완료는 양방향 대기 이체의 동시 완료)에서 PostgreSQL 교착(SQLSTATE 40P01)이 1건 이상 난다. 교착 외 다른 예외는 실패로 본다. 교착으로 실패한 즉시 이체는 거래·원장·입금 대기·잔액 변경이 하나도 남지 않는다. 교착으로 실패한 OTP 완료는 거래가 대기(PENDING_OTP)로, 지급정지가 그대로 남는다(OTP 코드는 이미 소모돼 다시 시도하면 만료 처리된다). 40P01이 원인인 예외의 API 응답은 500, 오류 코드 COMMON_006이다 |
| REQ-03 | `sorted-for-update` | 즉시 이체는 계좌번호, OTP 완료는 계좌 ID 오름차순으로 둘 다 SQL `FOR UPDATE`를 잡는다. for-update 두 전략은 두 경로 모두 송금·수신 계좌 행을 다른 세션이 `FOR KEY SHARE NOWAIT`로 읽지 못한다(55P03). current는 넷 다 읽힌다. 같은 양방향 동시 이체에서 교착 0건, 실패 0건 |
| REQ-04 | 설정값 검사·기동 로그 | 알 수 없는 값이면 앱이 기동하지 않는다. 기동 때 현재 전략을 INFO로 항상 한 번 남기고, `current`가 아니면 전략 이름을 담은 WARN도 한 번 남긴다. 환경변수 `JBANK_TRANSFER_LOCK_STRATEGY`로 바뀐다 |
| REQ-05 | 모든 전략, 동시 이체 후 입금 대기 반영 완료(미반영 0건 확인 후 측정) | 두 계좌 잔액 합이 실험 전과 같다. 원장 증가 건수 = 완료 거래 증가 수 × 2(송금 차변 + 반영 대변). 잔액 음수 0건. 테스트에서는 성공 호출 수 = 완료 거래 수. 부하 실험에서는 "실패 응답 중 반영 수 = 완료 거래 증가 수 − 2xx 수"가 0 이상, 실패 응답 수 이하다. 거래·원장 건수는 실험 두 계좌의 것만 센다. OTP 테스트에서는 지급정지 합 = 남은 PENDING_OTP 금액 합 |
| REQ-06 | 로컬 관측 스택, 조건별 동시 50·100 부하 | 회차마다 요청 수·2xx·5xx와 응답 `code` 분포·교착 증가량(`pg_stat_database_deadlocks`)·p95/p99·처리량·REQ-05 불변식을 기록한다. 조건 ①에서 `DeadlockDetected` 알림 메일이 mailpit에 오고, Loki에서 `deadlock detected`와 `DETAIL`(두 프로세스의 대기 관계)이 조회된다. 조건 ①의 5xx 수와 교착 증가량을 대조해 기록한다. 조건 ②·③에서는 교착 증가량 0 |
| REQ-07 | 운영, 조건별 동시 50·100 부하 | REQ-06과 같은 값과 최저 가용 메모리를 기록한다. 조건 ①에서 `DeadlockDetected` 메일이 사용자 Gmail로 온다. 다른 알림 메일(5xx 비율, 락 대기 등)도 오면 모두 시각과 함께 기록한다. 측정을 마친 ① 회차가 하나도 없으면 메일 수신 항목은 "미수신(사유)"으로 기록하고 Q-05에 따라 재실행하지 않는다. 서버 무응답이 나면 시각을 기록하고 복구한다. 운영 조회는 `JBANK_TRANSFER_LOCK_STRATEGY` 줄의 존재·값과 컨테이너의 그 변수만 출력하고 `.env` 전체·`docker inspect`는 출력하지 않는다. 실험이 끝나거나 중단되면 운영 `.env`에서 그 줄이 없어지고, api 기동 로그의 전략이 `current`이며, 이체 1건이 정상 완료된다. 정상 종료면 `prod-all`의 마지막 단계가 이 확인을 한다. 남은 회차 없이 중단되면 에이전트가 SSM으로 줄 제거·재생성·기동 로그를 확인하고, 이체 1건은 사용자가 `run-deadlock.sh prod-restore-check`(비밀번호 `read -s`)로 한다 |
| REQ-08 | 결과 보고서 | 요약표는 결과 파일에서 스크립트로 만든다. 세 조건의 수치 표, 교착 로그 원문(계좌번호·개인정보 가림), 알림 메일 시각, 측정 조건, 운영 장애가 아니라 재현 실험이라는 점, 비교 한계를 적는다 |
| REQ-09 | 운영 실험 당일 FDS 배치 수동 실행(Q-14) | 배치 종료 상태, 소요 시간, 실행 중 최저 가용 메모리(1초 표본), 새 의심 거래 건수(배치 전후 DB 개수)를 기록한다. 실패하거나 서버가 멈추면 그것도 결과로 기록하고 복구한다 |
| REQ-10 | API 요청의 `Origin`이 `https://www.j-bank.site` 또는 `https://j-bank.site`(Q-16) | CORS를 통과해 원래 처리 결과를 받는다. 로그인 성공이면 200과 `Access-Control-Allow-Origin`(그 출처), `Access-Control-Allow-Credentials: true`가 붙는다. 기존 허용 출처 `http://localhost:3000`, `https://api.j-bank.site`도 그대로 통과한다. 그 밖의 출처(예: `https://evil.example`, `https://j-bank.vercel.app`)는 지금처럼 403 `Invalid CORS request`다. 운영 반영 뒤 두 출처의 빈 본문 가입 요청이 403이 아니라 400 검증 오류이고, 사용자가 `https://www.j-bank.site`에서 가입·로그인할 수 있다 |

## 완료 기준

1. REQ-01~05: 락 전략별 통합 테스트, 교착 예외 응답 테스트, 설정 테스트와 기존 전체 테스트가 `verify`에서 통과한다.
2. REQ-06: 로컬 실험 18회 결과 파일, 요약표, mailpit 메일·Loki 조회 결과, postgres 로그 백업이 있다.
3. REQ-07: 운영 실험 6회 결과(무응답·시간 초과·측정 미실행 회차는 결과 이름과 사유), 알림 메일 수신(측정을 마친 ① 회차가 없으면 "미수신(사유)"), 설정 복귀 확인 기록이 있다.
4. REQ-08: 보고서 요약표가 요약 스크립트 재실행 결과와 같다.
5. REQ-09: 실험 당일 FDS 배치 관찰 기록이 있다.
6. 비밀번호·토큰·사용자 메일 주소·계좌번호 원문이 커밋 파일과 이미지에 없다.
7. REQ-10: CORS 출처 테스트가 `verify`에서 통과하고, 운영 반영 뒤 출처별 빈 본문 요청 결과(두 운영 프론트 출처 400, 임의 출처 403)와 사용자의 `www.j-bank.site` 가입 성공 여부가 기록돼 있다.

## 하지 않을 일

- 교착 예외 재시도, 40P01 전용 응답 코드 매핑(Q-07). 별도 작업으로 남긴다.
- 멱등성 키 설계 변경(이미 있음).
- 실험 설정 코드 제거(Q-06, 기본 꺼짐 유지).
- 요청 수 상한(Q-12).
- 운영 DB 초기화. 필요하면 사용자가 따로 결정한다.
- FDS 배치 개선. 관찰 결과 문제가 크면 별도 작업으로 남긴다.
- 프론트 프록시의 Origin 헤더 처리 변경(Q-16에서 고르지 않음), CORS 출처를 설정값으로 빼기, 와일드카드·Vercel 미리보기 주소 허용.

## 적용 영역과 상세 기준

- 프론트: 해당 없음. 화면 변경 없음.
- 백엔드·데이터: 적용. 이체 락 경로·트랜잭션 변경.
- AI: 해당 없음. 제품에 AI 기능 없음.
- DevOps: 적용. 새 운영 환경변수, 로컬 compose 한 줄, 운영 설정 변경·복구 절차, 의도한 오류로 알림 확인.
- 보안: 적용. 새 스크립트가 비밀번호·JWT 쿠키·CSRF 토큰을 다루고, 결과물에 운영 로그·메일 캡처가 들어간다. CORS 허용 출처를 넓힌다(REQ-10, SEC-04).
- 성능: 적용. 동시 처리와 락 대기를 부하로 비교한다.
- 전체 흐름: 해당 없음. 사용자 화면 흐름 변경 없음. API 경로는 REQ-06·07 부하가 실제로 지난다.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| BE-01 | 해당 없음 | 금액 계산·상태 전이 규칙 변경 없음 | - | - | - |
| BE-02 | 1, 2 (REQ-02 교착 응답) | SQLState 40P01 원인 예외 → 500·COMMON_006(처리기 테스트). 로컬 조건 ①의 응답 `code` 분포 기록 | `gradlew test`, `perf/run-deadlock.sh` | verify·로컬 실험 | `verify` 로그, `perf/results/deadlock/` |
| BE-03 | 1 (REQ-02·03 락 모드·부분 저장) | 즉시 이체·OTP 완료 × 송금·수신 계좌 네 경우에 대해, 이체 트랜잭션이 끝나기 전 다른 세션의 `FOR KEY SHARE NOWAIT`가 for-update 두 전략에서 55P03, current에서 성공. 교착 실패 이체의 부분 저장 0 | `gradlew test` | verify | `verify` 로그 |
| BE-04 | 1 (REQ-01~03·05 동시 양방향) | ① 40P01 ≥1(최대 5회 반복, 회차별 교착 수 출력), 40P01 외 예외 0. ②·③ 교착 0·실패 0. 모든 전략 불변식 유지 | `gradlew test` | verify | `verify` 로그 |
| BE-05 | 해당 없음 | 스키마 변경 없음 | - | - | - |
| BE-06 | 1 (REQ-02 OTP 부분 성공) | 교착 실패 시 거래 PENDING_OTP·지급정지 유지(테스트). OTP 코드가 먼저 소모되는 것은 기존 동작이라 테스트하지 않고 보고서에 적는다 | `gradlew test` | verify | `verify` 로그 |
| BE-07 | 1, 2, 3 (REQ-05 입금 대기 반영 후 불변식) | 미반영 입금 0 확인 후 잔액 합·원장 건수 일치 | 통합 테스트(`applyAllPendingCredits`), 실험 후 DB 조회 | verify·실험 후 | `verify` 로그, 실험 결과 |
| BE-08 | 해당 없음 | 권한·조회 경로 변경 없음 | - | - | - |
| OPS-01 | 해당 없음 | 빌드 방식 변경 없음 | - | - | - |
| OPS-02 | 해당 없음 | CI 변경 없음 | - | - | - |
| OPS-03 | 1 (REQ-04) | 잘못된 값 기동 실패, 빈 값·미설정은 current, 환경변수 바인딩 동작, 로그에 비밀 없음 | `gradlew test`(ApplicationContextRunner + OutputCapture) | verify | `verify` 로그 |
| OPS-04 | 해당 없음 | 기동·헬스 검사 변경 없음 | - | - | - |
| OPS-05 | 3 (REQ-07 설정 복귀) | 실험 종료·중단 후 `.env` 줄 제거, 기동 로그 `current`, 이체 1건 정상, 중지 스케줄 ENABLED이고 저장본과 State 외 차이 0. 중단 후 재개 때 이 확인을 먼저 한다 | 정상 종료: `prod-all` 마지막 단계(전략 줄 제거 → api 재생성 → 기동 로그 `current` → 받아 둔 비밀번호로 이체 1건). 남은 회차 없이 중단: 에이전트 SSM(줄 제거·재생성·로그·스케줄) + 사용자 `run-deadlock.sh prod-restore-check` | 운영 실험 후 | `perf/results/deadlock/prod/restore.md` |
| OPS-06 | 해당 없음 | 자원 제한 변경 없음. 운영 메모리는 PERF-06으로 기록 | - | - | - |
| OPS-07 | 2, 3 (REQ-06·07 알림) | 조건 ①에서 `DeadlockDetected` 발생·해제 메일 도착(목표는 도착 여부. 탐지·통지 시간 목표는 두지 않고 시각만 기록한다 — 알림 주기가 group_wait 30초·group_interval 5분으로 이미 고정돼 있어 실험이 바꿀 대상이 아님), Loki 교착 로그 조회. 메일·로그에 비밀 없음. 중단 회차 제외 규칙(성능 테스트 통과 기준)과 완료 기준 3의 "미수신(사유)" 규칙을 적용한다 | mailpit API·Loki API(로컬), 사용자 Gmail 확인(운영) | 실험 | 결과 파일 |
| OPS-08 | 해당 없음 | 백업·복원 변경 없음 | - | - | - |
| SEC-01~03 | 해당 없음 | 인증·권한·입력 변경 없음 | - | - | - |
| SEC-04 | 7 (REQ-10 CORS) | 허용 출처 네 개(`http://localhost:3000`, `https://api.j-bank.site`, `https://www.j-bank.site`, `https://j-bank.site`)의 로그인 요청은 200과 그 출처의 `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials: true`. 임의 출처(`https://evil.example`, `https://j-bank.vercel.app`)는 403 `Invalid CORS request`. 운영 반영 뒤 빈 본문 `POST /api/v1/customers`가 두 운영 프론트 출처에서 400 `COMMON_001_VALIDATION_FAILED`, `https://evil.example`에서 403. 쿠키 속성·CSRF 이중제출은 변경 없음 | `gradlew test`(AuthControllerTest), 운영 `curl -H "Origin: <출처>"`(헤더를 인자 하나로 넘김), 사용자 가입 | verify, 병합·배포 후 | `verify` 로그, `perf/results/deadlock/prod/cors-check.md` |
| SEC-05 | 6 | 비밀번호는 `read -s`/환경변수로만 받음(인자·명령 로그 금지), k6 요약의 `setup_data`를 빈 객체로 바꿈. 검사는 결과 리뷰 전(작업 기록 커밋 전)에 하고, 작업 기록 커밋 뒤 push 직전에 같은 명령을 다시 돌려 0건과 빈 stderr를 확인한다. CORS 수정 브랜치(커밋 7·8)와 운영 결과 브랜치(커밋 9~12)도 같은 순서다. 검사 대상은 커밋 대상 파일 목록(`git diff -z --name-only --diff-filter=d origin/main...HEAD` + `git diff -z --name-only --diff-filter=d HEAD`(아직 커밋하지 않은 추적 파일 변경) + `git ls-files -z --others --exclude-standard -- perf/k6/deadlock.js perf/run-deadlock.sh perf/deadlock-summary.py perf/results/deadlock docs .claude/tasks/transfer-deadlock-experiment`. 기존 perf 결과와 `perf/results/ec2-baseline/dryrun/`은 범위 밖)을 `xargs -0 grep -nE`로 넘긴다(한글 파일 이름도 그대로 전달). 패턴은 값에만 걸리게 한다: JWT `eyJ[A-Za-z0-9_-]{20,}\.`, 쿠키 값 `(XSRF-TOKEN|access_token)=[A-Za-z0-9._-]{16,}`, 메일 주소 `[A-Za-z0-9._%+-]+@gmail\.com`, 계좌번호 `110-[0-9]{6}-[0-9]`, 로컬 테스트 비밀번호(값은 변수로 `grep -F -f <(printf '%s\n' "$LOCAL_PW")`처럼 넘기고 기록에는 "로컬 비밀번호(값 생략)"만 적음), 결과 JSON에서 `"setup_data"`가 빈 객체가 아닌 경우(jq). 기대 0건이고 stderr가 비어 있음(검사하지 못한 파일 없음). 검사한 파일 목록도 기록한다. 새 스크립트의 사용 예시에는 계좌번호·비밀번호를 `<계좌번호>` 같은 자리 표시로 적는다. 이미지는 육안 점검 | 위 명령, 이미지 열람 | 결과 리뷰 전, push 직전 재실행 | `logs/secret-check.md` |
| SEC-06~08 | 해당 없음 | 의존성·권한·남용 제한 변경 없음 | - | - | - |
| PERF-01 | 2, 3 조건 고정 | 아래 "성능 테스트"의 고정값을 모든 회차에 적용. 로컬과 운영은 서로 비교하지 않는다 | `perf/run-deadlock.sh` | 실험 | 회차별 `conditions.json` |
| PERF-02 | 해당 없음 | 정상 부하 목표 검증이 아니다. 조건 ①은 일부러 오류를 낸다. 지연·처리량은 PERF-06·07에서 기록만 한다 | - | - | - |
| PERF-03 | 해당 없음 | 급증·회복 시험이 아님 | - | - | - |
| PERF-04 | 해당 없음 | 장시간 시험이 아님(회당 60초) | - | - | - |
| PERF-05 | 해당 없음 | 웹 화면 없음 | - | - | - |
| PERF-06 | 2, 3, 5 부하 중 락·교착·최종 데이터 | 교착 증가량(①≥1, ②·③=0), REQ-05 불변식 위반 0, p95/p99·처리량·`db.lock.wait` 기록, 운영 최저 가용 메모리 기록. 중단 회차 제외 규칙(성능 테스트 통과 기준) 적용 | `perf/run-deadlock.sh` | 실험 | 회차별 결과 |
| PERF-07 | 2, 3 측정 신뢰성 | 모든 반복의 결과가 집계됨(타임아웃은 status 0 실패로 포함), 로컬 3회 범위 기록, 실험 중 다른 부하 없음(운영은 부팅 배치 종료 후). 중단 회차 제외 규칙(성능 테스트 통과 기준) 적용 | `perf/run-deadlock.sh` | 실험 | 회차별 결과 |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| 1 (REQ-01) | 기본 설정에서 기존 전체 테스트 통과, 양방향 동시 이체 교착 0 | `apps/jbank-api/gradlew -p apps/jbank-api test spotlessCheck` | verify |
| 1 (REQ-02) | `unsorted-for-update`, A→B·B→A 동시(스레드 20, 100건, 최대 5회 반복)에서 40P01 ≥1(예외 원인 사슬의 SQLState로 판별, `pg_stat_database_deadlocks`는 쓰지 않음), 40P01 외 예외 0, 실패 이체 부분 저장 없음 | 같은 명령 | verify |
| 1 (REQ-02 응답) | SQLState 40P01을 가진 락 실패 예외 → 500·COMMON_006 | 같은 명령(예외 처리기 테스트) | verify |
| 1 (REQ-02·03 락 모드) | 외부 트랜잭션 안에서 즉시 이체·OTP 완료를 실행해 락을 쥔 채 다른 세션이 송금·수신 계좌 각각에 `FOR KEY SHARE NOWAIT`. for-update 두 전략은 네 경우 모두 55P03, current는 네 경우 모두 성공 | 같은 명령 | verify |
| 1 (REQ-03) | `sorted-for-update`, 같은 부하에서 교착 0·실패 0 | 같은 명령 | verify |
| 1 (REQ-02·03, OTP) | OTP 임계금액을 낮춘 테스트에서 A→B·B→A 인증 대기 이체 각 50건을 동시에 완료. `unsorted-for-update`는 40P01 ≥1(최대 5회 반복)·실패 건은 PENDING_OTP·지급정지 유지, 정렬 전략 둘은 교착 0·전부 완료. 지급정지 합 = 남은 PENDING_OTP 금액 합 | 같은 명령 | verify |
| 1 (REQ-04) | 잘못된 값 컨텍스트 기동 실패, 미설정·빈 값 → current, 환경변수 바인딩, INFO 1회, current가 아니면 WARN 1회 | 같은 명령(ApplicationContextRunner + OutputCaptureExtension) | verify |
| 1 (REQ-05) | 세 전략 모두 반영 후 잔액 합·원장 건수·완료 거래 수 일치 | 같은 명령 | verify |
| 2 (REQ-06) | 로컬 18회 실행, 메일·Loki 확인, postgres 로그 백업 | `perf/run-deadlock.sh local <조건> <VU> <회차>` + mailpit API·Loki API 조회 | 일반 테스트 통과 후 |
| 3 (REQ-07) | 운영 6회 실행(사용자가 `run-deadlock.sh prod-all` 실행·비밀번호 1회 입력), 메일 수신, 설정 복귀 | runbook 절차 + SSM(전략 변수만 출력) | 병합·배포 후 |
| 4 (REQ-08) | 요약 스크립트를 다시 돌린 결과와 보고서 표가 같다 | `python3 perf/deadlock-summary.py` 후 `diff` | 보고서 작성 후 |
| 5 (REQ-09) | 실험 당일 FDS 배치 상태·시간·메모리·건수 기록 | SSM: 표본 수집 시작 → 의심 거래 개수 → `run-batch.sh fdsDetectionJob --run-date <실험일>` → 개수 → 표본 요약 | 운영 실험 직후, 18시 전(또는 Q-15로 연장한 당일) |
| 6 | 커밋 대상 비밀값·메일·계좌번호 0건, 이미지 육안 점검 | SEC-05 패턴 | 결과 리뷰 전, push 직전 재실행 |
| 7 (REQ-10) | 허용 출처 네 개 로그인 200·CORS 응답 헤더, 임의 출처 두 개 403 `Invalid CORS request` | `apps/jbank-api/gradlew -p apps/jbank-api test spotlessCheck`(AuthControllerTest) | verify |
| 7 (REQ-10 운영) | 병합·배포 뒤 빈 본문 가입 요청: 두 운영 프론트 출처 400, `https://evil.example` 403. 사용자가 `www.j-bank.site`에서 실험 고객 가입 | `curl` 출처별 요청, 사용자 확인 | 병합·배포 후, 운영 실험 전 |

통합 테스트 시간 예산: 새 테스트 클래스 합계 180초 이내(`checks.json` 전체 900초 제한 안). 테스트 풀은 기존 Hikari 기본값(10)과 스레드 20을 쓴다. 교착이 이어져 커넥션 대기가 30초를 넘는 예외가 나면 40P01 외 예외로 실패 처리하고, 원인을 기록한 뒤 스레드 수를 풀 크기에 맞추는 수정을 계획 변경으로 다룬다.

## 성능 테스트

- 필요 여부와 이유: 필요. 이체 락 방식에 따른 교착·지연·실패를 비교하는 것이 작업 목적이다.
- 측정 대상과 실행 명령: `POST /api/v1/transfers`, `perf/run-deadlock.sh <local|prod> <조건> <VU> <회차>`(새 k6 스크립트 `perf/k6/deadlock.js`). 같은 고객의 두 계좌로 1,000원 이체, 방향은 `(__VU + __ITER) % 2`로 정해 시작부터 양방향이 섞인다. 반복마다 새 멱등키.
- 고정값(PERF-01):
  - k6 `constant-vus`, 측정 60초, 요청 타임아웃 30초.
  - 회차 순서: 전략 적용(api 재생성) → 미반영 입금 0 확인 → `VACUUM (ANALYZE) accounts` → 워밍업 20초(VU 5, 같은 전략) → 미반영 입금 0 확인 → 기준값 기록(교착 카운터, 거래·원장 건수, 두 계좌 잔액) → 측정 60초 → 10초 대기 → 미반영 입금 0 확인 → 종료값 기록. 기준값은 워밍업이 끝나고 10초 뒤 읽는다(PG 통계 반영 지연). 워밍업 중 교착은 기준값 이전이라 집계에서 빠진다. 거래·원장 건수는 실험 두 계좌로 한정한다.
  - 조건 ① 회차 뒤에는 해제 메일 도착을 확인한 뒤 다음 회차를 시작한다(알림 해제 약 5.5분 + 다음 group_interval, 최대 약 12분, 상한 15분). 상한에 닿으면 "해제 미확인"으로 기록하고 다음 회차로 넘어간다. 대기 중 SSM 호출 하나하나에도 5분 상한을 둔다. 로컬은 mailpit API로 해제 메일을 확인한다. 운영은 스크립트가 Alertmanager API(SSM)에서 알림이 사라진 것을 본 뒤 6분 더 기다리고, 사용자가 나중에 Gmail에서 발생·해제 시각을 확인한다. 메일은 회차 시각으로 귀속한다.
  - 시간 예산: 로컬 약 2시간 30분(① 6회 × 약 15분 + 나머지 12회 × 약 3분 + 재기동), 운영 약 40분(③② 4회 + ① 2회와 그 사이 대기). 운영은 18시 전에 FDS 관찰(Q-14)까지 끝나도록 14시 전에 시작한다.
  - 로컬 순서: 반복마다 조건 순서를 돌린다(1회 ①②③, 2회 ②③①, 3회 ③①②), 각 조건 안에서 VU 50 → 100. 운영은 1회라 ③ → ② → ① 순서이고 순서 효과는 비교 한계로 적는다.
  - 회차마다 시작 시 거래 테이블 행 수를 기록한다.
- 측정 환경: 로컬은 Docker Desktop(compose core+api+observability), k6는 같은 맥에서 실행(부하 발생기와 대상이 자원을 나눠 쓴다는 한계 기록). 운영은 t3.small(관측 스택 포함), k6는 사용자 맥에서 인터넷·Caddy(HTTPS) 경유(네트워크 지연 포함 한계 기록), 부팅 배치 종료 후. 계좌 2개, 각 잔액 1억 원 이상.
- 지표와 통과 기준: 교착 증가량(① ≥1, ②·③ = 0), REQ-05 불변식 위반 0, 모든 반복의 결과가 집계됨(타임아웃은 status 0 실패로 포함). 무응답·시간 초과·측정 미실행 회차는 그 회차의 교착·집계 기준에서 빼고 결과 이름과 사유로 기록한다(Q-05). 측정이 끝난 시간 초과 회차는 따로 기록한 종료값으로 불변식을 판정한다. 측정이 끝난 뒤 무응답이 된 회차도 복구 후 미반영 입금 0을 확인하고 종료값을 SSM으로 기록해 같은 방식으로 불변식을 판정한다. 측정을 마친 ① 회차라도 직후 서버가 멈춰 메일을 못 받았으면 "미수신(사유)"으로 기록한다(재실행 없음). p95/p99·처리량·2xx/5xx·응답 `code` 분포·`db.lock.wait`·운영 최저 가용 메모리는 기록만 한다(PERF-02 해당 없음 근거).
- 변경 전 결과와 측정 조건: 신규 실험이라 이전 결과 없음. 조건 ③이 현재 코드의 기준이다.
- 결과·로그 저장 위치: `perf/results/deadlock/<환경>/<조건>-vu<N>-r<회차>/`(k6 요약, `conditions.json`, 전후 DB 값, postgres 교착 로그 백업 `docker logs --since`), 요약 `perf/results/deadlock/README.md`.

## 추가 맥락

- 설정 이름: `jbank.transfer.lock-strategy`(`current` | `sorted-for-update` | `unsorted-for-update`), 환경변수 `JBANK_TRANSFER_LOCK_STRATEGY`.
- FOR UPDATE는 기존 `@Lock(PESSIMISTIC_WRITE)` 쿼리를 재사용하지 않고 native `for update`로 새로 쓴다(Hibernate 방언에 따라 SQL이 달라질 수 있음). 새 쿼리에도 `@Timed("db.lock.wait")`를 붙여 ②·③의 락 대기를 비교한다.
- 운영은 Hikari 기본 풀(10)·Tomcat 기본 스레드(200)다. VU 100이어도 DB 동시성은 풀 크기로 제한되고 나머지는 커넥션을 기다린다. 결과 해석에 적는다.
- 운영 반영: 코드 변경만이라 병합 후 backend-cd가 api만 재생성한다(postgres 설정 변경 없음).
- 운영 `.env`의 전략 줄 변경(`sed -i`)과 api 재생성은 같은 `runuser -u ec2-user -- flock /var/lib/jbank/deploy.lock ...` 안에서 한다(`boot.sh`·`deploy-ec2.sh`와 같은 잠금, 배포·sync와 겹침 방지, root 소유 파일 생성 방지). 서버 무응답 시 EC2 중지·시작은 에이전트가 AWS CLI로 한다(Q-05 수용 범위).
- 운영 무응답 절차(Q-05·Q-13 안의 운영 기본값):
  - 판정: SSM 단계마다 대기 상한 5분을 둔다. 상한을 넘긴 동안 `https://api.j-bank.site/readyz`가 한 번도 200을 주지 않으면(타임아웃·502 포함) "무응답", 200을 주면 "시간 초과"다. 둘 다 그 회차를 결과와 시각으로 기록하고 종료한다. 측정(k6) 전 단계에서 일어나면 그 회차는 1회로 세고 "측정 미실행"으로 표시하며 재실행하지 않는다(Q-05). "시간 초과" 회차도 1회로 센다. 에이전트가 원인(예: 미반영 입금이 0이 되지 않음)을 SSM으로 확인해 기록하고, 원인이 풀리면 그 회차의 종료값을 SSM으로 따로 기록한 뒤 `--from <다음 회차>`로 재개한다.
  - 스크립트는 그 회차를 결과 "무응답" 또는 "시간 초과"와 시각으로 기록하고 종료한다(남은 회차를 돌지 않음). 직전 ① 회차의 해제 대기 중에 중단되면 끝난 회차 결과는 그대로 두고 해제 상태를 "해제 미확인"으로 기록하며, 재개할 때 해제 확인부터 한다.
  - 복구: 에이전트가 AWS CLI로 EC2 중지·시작 → `systemctl is-active jbank-boot`가 activating이 아니고(inactive 또는 failed) `/var/log/jbank/boot.log`의 마지막 `boot: 시작` 이후에 `끝` 또는 `배치 건너뜀` 줄이 있는지로 부팅 배치 종료 확인(failed이면 실패한 잡을 기록) → 전략 변수·중지 스케줄 상태 확인 → 사용자가 `run-deadlock.sh prod-all --from <다음 회차>`로 남은 회차만 재개(비밀번호 다시 입력). 끝난 회차는 다시 돌리지 않는다.
  - 18시 전에 끝나지 않을 것 같으면 그날 중지 스케줄을 끄고 이어서 끝낸다(Q-15).
- 스크립트는 회차별 진행 로그를 `perf/results/deadlock/prod/progress.log`에 남기고, 에이전트는 그 파일로 진행을 지켜본다.
- 운영 Alertmanager는 호스트 포트가 없어 `docker compose exec -T alertmanager wget -qO- localhost:9093/api/v2/alerts`로 조회한다. 해제된 알림이 목록에 남는 경우를 대비해 Prometheus `ALERTS{alertname="DeadlockDetected"}`도 함께 보고, 대기 상한은 15분이다.
- 운영 postgres 로그 백업은 서버에서 파일로 저장한 뒤(`docker logs --since ... > /tmp/...`) 나눠 받는다(SSM 표준 출력 약 24,000자 잘림).
- FDS 수동 실행 전 마지막 완료 runDate가 실험일 전날인지 확인한다(아니면 따라잡기 공백이 생겨 실행하지 않고 사용자에게 알린다). 수동 실행 뒤 같은 날 생긴 방문자 이체는 검사에서 빠진다는 점을 보고서에 적는다.
- 이 작업은 병합 후 운영 실험·FDS 관찰까지 열려 있다. 그동안 한 폴더 한 작업 원칙에 따라 다른 하네스 작업을 시작하지 않는다.
- 운영 서버는 평일 09~18시만 켜진다. 18시 자동 종료·EC2 중지로 실험이 중단되면 `.env`의 실험 전략이 남아 다음 부팅 때 그 전략으로 뜬다. runbook의 재개 첫 단계는 전략 변수 확인과 중지 스케줄 ENABLED 확인이다.
- `log_lock_waits` 로그가 한꺼번에 쏟아지면 Loki가 일부를 버릴 수 있어 postgres 로그를 회차마다 파일로도 남긴다.

## 커밋 계획

1. `feat(transfer): 이체 락 전략 실험 설정 추가` — 설정·기동 로그·native FOR UPDATE 쿼리, 기본값 현재 코드
2. `test(transfer): 락 전략별 교착·락 모드·설정·교착 응답 테스트`
3. `chore(compose): 로컬 api에 락 전략 환경변수 전달`
4. `feat(perf): 교착 실험 k6·실행·요약 스크립트`
5. `docs: 교착 실험 runbook`
6. (로컬 실험 후) `docs(perf): 로컬 교착 실험 결과`
7. (Q-16, main에서 새 브랜치 `fix/frontend-cors-origin`) `fix(api): 운영 프론트 출처를 CORS 허용 목록에 추가` — 허용 목록 두 출처 추가, 틀린 주석("배포 프론트는 서버 쪽 프록시라 CORS가 생기지 않는다") 정정
8. (같은 브랜치) `test(api): 운영 프론트 출처의 CORS 통과·차단 테스트` — 7까지 기존 테스트 통과 상태 유지. 7·8만 push·PR·병합·배포 후 운영 확인
9. (운영 실험 후, main에서 새 브랜치) `docs(perf): 운영 교착 실험 결과` — 운영 CORS 확인 기록 포함
10. (운영 실험 당일) `docs(perf): 실험 당일 FDS 배치 관찰`
11. `docs(devlog): 교착 실험 개발일지`
12. `chore(harness): transfer-deadlock-experiment 작업 기록`
