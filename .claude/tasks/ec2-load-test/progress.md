# 진행 기록

- 2026-09-27: 브랜치 `perf/ec2-load-test`(main 6c40ee2 기준) 생성, 작업 ec2-load-test 생성.
- 사용자 결정: Q-01 작업 2개로 분할(이 작업은 기준선까지), Q-02 새 perf 환경+시드, Q-03 측정할 때만 생성, Q-04 S1·S2·S3·S5, Q-05 PG 데이터 복사본 복원, Q-06 측정 조건 제안값 확정(부하가 잘 안 생기면 다음에 올림).
- 확인한 사실: 운영 크레딧 unlimited, 운영 plan No changes(종료 코드 0), GHCR `490ed10` 태그 manifest 200.
- 계획 리뷰: 1회차 수정 필요(차단 2·중요 11) → 2회차 수정 필요(중요 3) → 3회차 통과 권고(지문 b76be8d8…). 한도 도달.

## 구현 메모 (3회차 제안, 명세 변경 없이 따름)

- 기준 불일치 목록·전체 차변/대변 합은 대사 로그를 파싱하지 않고 대사와 같은 조건의 비교 SQL로 저장한다.
- 예열은 별도 k6 실행으로 하고, 종료를 확인한 뒤 측정 경계(ID 최댓값)를 기록한다.
- 복원 후 로그인·핫 계좌 조회 확인은 준비 단계의 복원 검증 1회로만 한다. 회차 시작 조건에는 넣지 않는다.
- Redis는 PG 복원 대상이 아니므로 회차 시작 조건의 스택 재기동 때 redis 컨테이너도 새로 띄워(볼륨 없음) 비운 상태로 시작한다.

## 범위 밖 발견

- 운영 backend-cd가 2026-09-27 PR #5 병합 후 SSM 배포 단계에서 `InvalidInstanceId`로 실패. 이미지 빌드·푸시는 성공, 운영 반영 안 됨. 별도 작업 대상.

## 구현 진행

- 2026-09-27 사용자 승인("진행") → start(implementing).
- 커밋 1 완료(6a8d733): perf Terraform 모듈·환경. `terraform validate`·`fmt -check` 통과, `plan` 12개 추가(공개 인바운드 없음) 확인. apply는 아직 안 함.
- 작성 완료·미커밋: `infra/compose/perf/`(대상·부하 발생기 Compose, Caddyfile, exporter 쿼리, 대시보드), `perf/prepare-accounts.py`, `perf/k6/lib/ec2.js`·`s1-mixed.js`·`s2-hot-account.js`·`s3-spike.js`, `perf/ec2/`(target.sh·loadgen.sh·k6_monitor.py·extract_slow_sql.py·verify_login.py), `perf/run-ec2.py`·`run-ec2.sh`.
- 확인: 대상 Compose `config -q` 통과, 셸·파이썬 문법 통과, `k6 inspect`로 S1(40단계·maxVUs 2000)·S2(1분 뒤 시작·병행 21분)·S3(탐침 5분20초·스파이크 3단계)·constant 모드 모양 확인.
- 설계 메모: 결과 수집은 SSM 출력 분할(base64 20,000자 단위)로 해서 추가 IAM·SSH가 없다. Grafana 보기만 SSM 포트 포워딩(session-manager-plugin) 필요.
- 막힘: 로컬 Docker에서 이미지 pull이 멈춘다(`docker version`은 응답, busybox pull도 진행 안 됨, credsStore=desktop). 로컬 지표 스모크(커밋 2·3 검증)를 못 하고 있다.

- 사용자 조치: Docker Desktop 재시작(pull 정상화), 브랜치 push 허락, session-manager-plugin 설치.
- 로컬 스모크 결과:
  - `/actuator/prometheus`가 인증 필요(401). 관리 포트 분리로도 401. 앱 코드 무변경을 지키려고 대상에 metrics-proxy(perf 전용 계정 로그인 후 대리 수집, :9091)를 추가하고 SG 포트를 8080→9091로 변경(커밋 1 amend). 운영과의 차이로 문서화할 것. 운영 Prometheus도 같은 이유로 api 지표를 못 받고 있을 가능성 → 범위 밖 발견으로 보고.
  - 프록시 경유 지표: tomcat_threads_busy_threads(mbeanregistry 설정으로 노출 확인), Hikari, JVM, http 히스토그램 확인. node/postgres exporter 지표 확인. cAdvisor 컨테이너 지표와 node 루트 파일시스템은 Mac Docker Desktop에서 안 나와 EC2 드라이런에서 확인(OPS-07 회차별 검사로도 잡힘). node-exporter rslave 마운트는 Mac에서만 override.
  - 준비 스크립트(발신 3·로그인 2·핫 1)와 복원 검증 스크립트 통과. 이체 201 확인.
  - k6 스모크: VU마다 로그인하던 구조라 재로그인 181/382건 → setup 전원 로그인 + VU별 고정 고객 + 12분 재로그인으로 변경 후 재로그인 0건. 단계 구간 시작은 setup 종료(SCENARIO_START)로 변경.
  - 배치 3종 × 서로 다른 perfRun 2회 연속 모두 COMPLETED.
  - loadgen Compose config·promtool check config·대시보드 JSON 통과.
- 커밋 2~6 완료(938624a, 364ca41, dd80ee2, dfa9a61, cb67fff), 브랜치 push 완료.

- 2026-09-27 23:13 perf 환경 up(REQ-01 기록: 같은 AZ 2d, t3.small unlimited·c7i.large, SSM Online, 0.0.0.0/0 인바운드 0건, 역할 정책 SSM만).
- setup: 수집 대상 5개 up, cAdvisor·루트 FS 지표 EC2에서 확인.
- prepare 1차 실패: caddy가 IP 접속(SNI 없음)에 인증서를 못 골라 TLS internal error → default_sni 수정(3e40f70), 시드 재실행 건너뛰기(d6612ac), setup에서 caddy 재시작(fac858c). 2차 통과: 시드 6분(거래 1천만), 발신 200·로그인 20·핫 1, 기준 불일치 10만(시드 계좌 전부)·기준 대변 200억, DB 2.3GB, 복사본은 XFS reflink라 즉시·추가 디스크 없음, 복원 검증 통과.
- 앱 종료로 첫 드라이런 로컬 프로세스가 끊김(원격 일부는 진행됨) → 이후 긴 실행은 nohup으로 분리.
- 드라이런에서 발견·수정: 백그라운드 k6 로그 경쟁으로 옛 SCENARIO_START 기록(4f66e65, 회차 디렉터리 비우고 시작), 대사 로그 37MB(시드 WARN 10만 줄 → 건수만), 이벤트 카운터 빈 시계열(4489d9a, or vector(0)), 드라이런 폴더 분리. 재실행 결과 empty_series=0, 요청 200건 초당 10, 정합성 일치, missing=none.
- 첫 드라이런 결과 폴더 `perf/results/ec2-baseline/dryrun/`의 파일(대사 로그 37MB 포함)은 버그 상태 산출물이라 커밋하지 않는다. 유효한 드라이런은 `dryrun/20260927-235347/`.
- 2026-09-28 00:0x 기준선 시작: S1 r1~3 → S2 r1~3 → S3 r1~3 연속(nohup, 로그 scratchpad/baseline.log).

- s1-r1(1차): 초당 100건에서 p95 438ms로 무너짐, 최대 지속 50. 150건에서 CPU 98%·Tomcat 200·Hikari 대기 189·타임아웃 5,885. 정합성 일치. 그 뒤 정합성 확인용 대사 잡에서 대상이 메모리 스래싱으로 멈춤(가용 807→127MB, 메이저 폴트 169/s, api 컨테이너 약 1GB, SSM ConnectionLost, 00:12~). 가설 E의 실제 재현. 원자료 `s1/incident-r1-memory/`(스래싱 구간까지 query_range 연장)에 보존. 대상 재부팅(01:03)으로 복구.
- 수정(9c0a52e): 측정 후 대사 전에 api·proxy·caddy 정지, 배치 30분 timeout, S5 부하 중 배치 사고 시 기록·재부팅 후 수집 계속, `recover` 명령.
- 2026-09-28 01:09 기준선 재시작(S1 r1부터).

- 기준선 완료(2026-09-28 04:55): S1 r1~3(재측정), S2 r1~4(r1~3 변동폭 두 단계 → r4 추가), S3 r1~3, S5 r1~3(--rate 35). 모든 회차 missing=none, 부하 발생기 CPU 최대 21%(무효 회차 없음), 정합성 유지.
- down: state 비어 있음. 태그 조회 API가 7건을 보였으나 직접 조회 결과 모두 NotFound(반영 지연), 인스턴스 terminated, IAM NoSuchEntity. 운영 plan 종료 코드 0(작업 전과 같음).
- 결과: summary.md·bottleneck-analysis.md 작성. 가설 A·B·D·E 확인, C 부분 확인. "실패 응답인데 반영" 이체 발견(S1 1,640·S3 2,685 중앙값).
- k6 요약에 setup 로그인 토큰이 직렬화되던 문제 발견 → 결과 31개에서 제거, loadgen.sh에서 자동 제거(45893e5).
- 커밋: 3f88d43 perf(results), 67e0d30 docs(perf). docs/testing-policy.md 성능 행 수정은 승인된 테스트 기준 지문을 바꿔 verify가 거부 → 되돌림(명세 밖 변경).
- verify 통과.
- 범위 밖 발견(작업 칩): 운영 backend-cd SSM 실패, 운영 /actuator/prometheus 401, CTR 배치 UTC 기준일.

- 결과 리뷰: 1/3은 review-begin 후 verify 재실행으로 verifier 호출 없이 소비(실수). 2/3 verifier 수정 필요(중요 4: 실패 응답 반영 수치 혼입·REQ-10(4) 미구현, 포화 시 api 지표 공백(S1 후반·S3 스파이크 전 구간 확인), 배치 처리 건수 누락, S5 12분 동시 재로그인 포화(725~735s 확인)). review fail 등록. 남은 결과 리뷰 1회.
- 사용자 결정 Q-07: 보완 후 S1·S2·S3·S5 재측정, 계획 리뷰 추가 허락. task.md 갱신 → 계획 리뷰 4회차 수정 필요(중요 A·B·C) → 반영 → 5회차 추가 허락받음.
- 5회차 verifier 호출 중 사용자가 중단(2026-09-28).

- 사용자 "이어서 진행" → 계획 리뷰 5회차 수정 필요(중요 2: 결측 기준 REQ 문구 불일치, 추가 측정 상한) → 반영 → 6회차 추가 허락 → 6회차 통과 권고(지문 642b736f…) → start.
- 6회차 제안 구현 메모(명세 무변경): 경계 확인 실패 회차는 회차 수에 넣지 않고 1회 재시도 후 실패 시 사용자 확인, gaps.txt는 모든 회차에 생성, 회차 environment.md에 부하 발생기 유형 기록.
- 커밋 11 151cccc(관리 포트 9095 분리, readiness·헬스체크·프록시 이동), 12 91dd857(프록시 백그라운드 재로그인·기존 쿠키 유지·경계에서 재시작·/metrics 200 확인), 13 891fd69(Tomcat 본 커넥터 한정·up 패널), 14 f158174(재로그인 9~12분 분산), 15 b8e7cd2(실패 멱등키 기록·DB 대조·핫 계좌 k6 금액 비교), 16 c9956c3(gaps.txt 결측 검사, batch-counts.txt, S5 배치 밖 지표·분당 relogin, 경계 재시도, 발생기 유형 기록, 배치 종료 시각 측정 위치 수정).
- 로컬 사전 검증: config -q 통과, 관리 포트 readiness(컨테이너 안 wget)·healthy, 8080 health는 500(이동됨), 프록시 수집 200, Tomcat 지표 본 커넥터 1개만 노출, 계정 잠금(423)으로 로그인 차단해도 프록시 40초간 200 유지, k6 relogin_after_minutes 9.01~12.0(측정 중 재로그인 0), 실패 키 54 = 체크 실패 54, 대조 SQL이 상태 0·실제 완료 1건 탐지, 배치 3종 × perfRun 2회 모두 COMPLETED·완료 메시지 추출.
- 1차 측정 배치 수행 시간은 로그 후처리(대사 WARN 필터) 뒤에 종료 시각을 재 약간 부풀었을 수 있음(대사 잡만 해당) — 재측정에서 수정됨.
- 1차 결과를 `perf/results/ec2-baseline/first-pass/`로 git mv(커밋 17에 포함 예정). 브랜치 push.

- 재측정(2026-09-28): up 중 부하 발생기 SSM 미등록(프로필 반영 전 에이전트 기동 추정) → 재부팅으로 해결. 드라이런에서 분당 relogin 쿼리 400(20초 측정) → a2f21f7 수정. 커밋 479eefc는 phase=waiting으로 편집이 막힌 상태에서 만들어져 수정 없이 first-pass 이동(git mv)만 담김 — 메시지와 내용 불일치, 사용자에게 보고(강제 push 여부 결정 대기).
- 재측정 결과: S1 r1~3 붕괴 100·최대 50(3/3), S2 80/100/80(최대 60/80/60, 변동 한 단계 이내), S3 회복 75/100/145초(3/3 미회복), S5 35rps 무너짐 없음·배치 밖 포화 없음·재로그인 분산 확인. 모든 회차 gaps invalid=no, missing=none, 발생기 CPU ≤27%. 멱등키 대조: 타임아웃 실패 체크 사실상 전부 반영, 502는 0건 반영.
- 자기 검증 판정 방향 수정·regen-integrity(b1b2c1c), 재측정 원자료(0759923), 문서 갱신(bd5e671). down: 잔여 0(태그 조회 지연 7건 직접 확인 NotFound), 운영 plan 종료 코드 0. verify-003 통과(snapshot 08c9bb65…).

- 결과 리뷰 3/3: verifier 통과 권고(차단·중요 0, 개선 6). review.md 작성, review pass.
- 남은 개선(후속): 요약에 드롭 수 열, S2 r3 문구, S5 메모리 열 이름, first-pass 문서 머리말 결함 표시, 후속 측정 전 정합성 스냅샷 순서(stop-api 먼저), 479eefc 메시지 정정 여부(사용자 결정).

다음 행동: complete → 개발일지·작업 기록 커밋 → 사용자 보고(479eefc 처리 결정 포함).

(이전 다음 행동: 결과 리뷰(review-begin → verifier) → review.md → complete → 개발일지·작업 기록 커밋.)

(이전 다음 행동: 기준선 완료 확인 → S1 변동폭 확인(한 단계 초과 시 추가 1회) → S5 요청률(S1 최대 지속 가능 요청률 최솟값의 70%) 계산 → S5 r1~3 → down → 문서·개발일지.

(이전 다음 행동: 사용자가 Docker Desktop을 확인·재시작하면 로컬 스모크(지표 존재, 준비 스크립트, k6 스모크, 배치 2회 연속) → 커밋 2~6 → 브랜치 push(사용자 허락 필요) → up/setup/prepare/dry-run.
