# 계획 리뷰

## 1회차

회차 계획 지문: 3022f5da6e0e0308b9cb634026840cd58c1be2e445361214963d6d9f478285db
회차 판정: 수정 필요

### verifier 응답 원문

- 모드: 계획 리뷰 1회차 / 작업 ID: ec2-weekday-schedule / 대상: `.claude/tasks/ec2-weekday-schedule/task.md` (명세 상태 "확정", 2026-09-29 시점 파일)
- 읽은 자료: docs/testing-policy.md, docs/testing/{devops,frontend,backend,security,e2e}.md, task.md, .claude/checks.json, infra/terraform/modules/ec2/{main.tf,user_data.sh.tpl}, envs/dev/main.tf, infra/compose/{deploy.sh,run-batch.sh,docker-compose.prod.yml,Caddyfile}, .github/workflows/{backend-cd,infra-cd}.yml, apps/frontend/{app/api/proxy/[...path]/route.ts,lib/api-client.ts,app/layout.tsx,package.json}, batch 잡 설정(Interest·CTR·FDS·Reconciliation, SingleInstanceJobExecutionListener, application*.yml), ADR 0010(일부)
- 적용 기준 ID: SEC-05·07, OPS-01·03·04·05·07, BE-01, FE-01·03·04·05, E2E-04
- 확인하지 못한 범위: 인스턴스 실제 상태·.env의 IMAGE_TAG 형식·EBS 암호화 키 종류·Vercel 플랜/최대 실행 시간·CD 실패 이력은 읽기만으로 확인할 수 없음. performance.md는 해당 없음이라 읽지 않음.

## 판정: 수정 필요

Q-01~05의 사용자 결정은 원문 요청 해석에 맞게 반영되었고, 임의로 채운 제품 결정은 크지 않음. 다만 부팅 작업·CD·배치 재실행에 대한 운영 설계에 실제 결함으로 이어질 빈틈이 있고, 몇몇 검증 명령이 구체적이지 않음.

## 지적

### 1. [중요] OPS-05·REQ-03·04: 부팅 작업과 CD가 동시에 실행될 수 있고, SSM 준비 전 구간이 정의되지 않음
- 근거: backend-cd는 이미지를 먼저 push한 뒤 SSM으로 `git fetch && git reset --hard origin/main && deploy.sh`를 실행함(backend-cd.yml:62). 계획한 부팅 작업도 "저장소를 main으로 맞추고 deploy.sh"를 실행함(task.md:34,36). 둘 사이에 잠금이 없음.
- 영향: 09:00 직후 main push가 있으면 CD는 `running`을 보고 SSM을 보내고, 동시에 부팅 작업도 같은 sha를 반영하면서 git index.lock 충돌·`.env` 동시 sed·배치 도중 api 재기동이 생길 수 있음. 또 인스턴스가 `running`이어도 SSM 에이전트 등록 전(부팅 후 수십 초)에는 SendCommand가 `InvalidInstanceId`로 실패함. REQ-03 규칙상 이 경우 CD가 실패함.
- 필요한 수정·테스트: 두 경로가 함께 쓰는 잠금(예: `flock /var/lock/jbank-deploy`)을 두어 deploy.sh에서 처리하면 됨. 부팅 직후 SSM 미등록 구간을 "실패로 둘지, 건너뛰기로 볼지" REQ-03에 적어야 함. 참고로 SSM PingStatus 기준 판단도 가능함. `DescribeInstances` 호출 자체가 실패하면 건너뛰지 말고 실패해야 한다는 조건도 REQ-03에 명시해야 함. 이 때문에 OPS-02를 "CI 차단 규칙 변경 없음"으로 제외한 것은 부정확함. 건너뛰기 경로가 새로 생겨 "실패가 성공으로 바뀌지 않음"을 확인할 대상이 생겼음.

### 2. [중요] REQ-04·05, 적용 순서: 머지 전에 호스트에 설치하는 것과 `git reset --hard origin/main`이 충돌함
- 근거: 설치(커밋 2 이후, SSM 1회)를 PR 머지 전에 수행함(task.md:78). 그런데 CD와 부팅 작업은 모두 `/opt/jbank`를 origin/main으로 되돌림.
- 영향: 머지 전에 다른 main push가 있거나 부팅 작업이 reset을 하면 저장소에 부팅 스크립트가 없어짐. systemd 단위가 저장소 경로를 가리키면 다음 부팅(18:00 중지 → 09:00 시작)에서 ExecStart 파일이 없어 실패하고, cron은 이미 제거된 상태라 배치가 멈춤. 또 부팅 스크립트가 실행 도중 자기 파일을 `git reset`으로 바꾸면 bash가 스크립트를 조금씩 읽는 특성 때문에 오동작할 수 있음.
- 필요한 수정: 설치 스크립트가 부팅 스크립트와 unit을 저장소 밖 고정 경로(예: `/usr/local/lib/jbank/`)에 복사하게 할지, 아니면 호스트 적용을 머지 후로 미룰지 정해야 함. unit에 `User=ec2-user`를 지정하는 것도 적어야 함(root로 git을 실행하면 dubious ownership 오류가 남. backend-cd.yml:54 주석 참고).

### 3. [중요] REQ-05·06, BE-01: Spring Batch 재실행 제약 때문에 부팅 배치가 실패하거나 멈출 수 있음
- 근거: 잡에 incrementer가 없음(각 *JobConfig). 그래서 같은 식별 파라미터로 다시 실행하면 COMPLETED 기록은 `JobInstanceAlreadyCompleteException`, 비정상 종료로 STARTED에 남은 기록은 `JobExecutionAlreadyRunningException`이 남. 부팅 러너가 예외로 끝나면 `run-batch.sh`는 0이 아닌 값으로 종료함.
- 영향:
  - (a) 하루에 두 번 부팅하면 `interestMaturityJob runDate=당일`이 반드시 실패함. 수동 기동이나 REQ-04·05 검증용 중지→시작을 같은 날 반복할 때 재현됨. 스크립트가 `set -e`면 이어지는 CTR·대사·FDS도 실행되지 않음.
  - (b) 18:00 중지(수동으로 17시대에 기동한 경우 포함)가 배치 실행 중에 오면 실행 기록이 STARTED로 남음. 그러면 "다음 부팅에서 그 날짜부터 재시도"가 계속 실패해 따라잡기가 영구히 멈춤. 알림도 없음.
  - (c) 중단된 잡의 Redis 락(lease 30분)이 남아 있으면 곧바로 재부팅할 때도 FAILED가 남.
- 필요한 수정·테스트: REQ-05에 잡별 실패 정책을 적어야 함. 한 잡이 실패하면 나머지를 계속 실행할지 여부, 오늘 이미 COMPLETED인 interestMaturity는 건너뛸지 여부, STARTED로 남은 기록의 처리(보고만 하고 멈출지, 수동 복구 절차를 둘지) 같은 것임. REQ-06에서 "중간 실패 시 이후 날짜 미실행·다음 부팅에서 재시도·완료 날짜 재실행 없음"은 현재 BE-01의 날짜 목록 검사에 들어 있지 않음. 모의 run-batch로 하는 검사 사례를 추가해야 함. 참고로 CTR·FDS는 exists 검사, interest는 ACTIVE 필터가 있어 같은 날짜를 다시 실행해도 데이터가 중복되지 않음. 이 근거로 BE-04를 해당 없음으로 처리한 이유를 기록하면 됨.

### 4. [중요] REQ-04·05, OPS-04: 부팅 작업의 실패 사례가 정의되지 않음
- 근거: REQ-04·05에는 정상 동작만 있음. OPS-04 기준은 "느린 기동·의존성 장애 재현"을 요구함.
- 영향: api가 끝내 준비되지 않거나(.env 문제, 잘못된 새 이미지) GHCR pull이 실패할 때 동작이 정해져 있지 않음. Type=oneshot은 기본 시작 제한 시간이 없어서, 무한 대기하거나 배치가 전혀 돌지 않는 상태가 로그 없이 이어질 수 있음.
- 필요한 수정·테스트: 준비 대기 상한과 초과 시 동작(로그를 남기고 종료할지, 배치를 건너뛸지)을 정해야 함. pull이나 inspect가 실패하면 현재 이미지를 유지한 채 배치를 계속하도록 해야 함. 이미지 교체 후에는 api readiness를 다시 기다린 뒤 배치를 실행해야 함(Flyway 마이그레이션 순서 때문). 각 사례는 OPS-03·04의 로컬 모의 검사에 넣어야 함.

### 5. [중요] REQ-07, FE-03, E2E-04: 5초 제한이 연결 단계만이 아니라 요청 전체에 걸릴 위험
- 근거: 계획은 "연결 대기를 5초로 제한"이라고 적었지만(task.md:38), 일반적인 구현인 `AbortSignal.timeout`은 응답 본문을 포함한 요청 전체를 끊음. Node fetch의 연결 전용 timeout을 쓰려면 undici dispatcher가 필요한데, 현재 의존성에 없음.
- 영향: 백엔드가 살아 있는데 5초보다 느린 응답(콜드 스타트 직후, 대량 조회)이 `SERVER_OFFLINE`으로 잘못 표시됨. 특히 POST 이체가 서버에서 실제로 처리되었는데 화면에는 "서버 꺼짐"이 뜨면 사용자가 다시 제출함. api-client는 제출할 때마다 새 Idempotency-Key를 만들기 때문에(api-client.ts:29) 중복 이체가 생길 수 있음. REQ-07의 "백엔드가 응답하는 동안 안내 없음"과도 어긋남.
- 필요한 수정·테스트: 두 가지 중 정해야 함. (가) 연결 단계에만 제한을 둠. (나) 오류 원인(ECONNREFUSED, 연결 timeout, ENOTFOUND)일 때만 SERVER_OFFLINE으로 바꾸고, 응답 대기 timeout은 적용하지 않거나 다른 코드로 보냄. FE-03 jest에 "연결 후 응답 지연(>5초) POST는 SERVER_OFFLINE이 아님" 사례를 추가해야 함.

### 6. [중요] BE-01·OPS-03: 검증 명령과 통과 기준이 구체적이지 않고 실행 환경이 다름
- 근거: "날짜 계산 자체 검사 스크립트", "부팅 작업 로컬 모의 실행(docker 없이 함수 단위)"에 실제 명령과 파일 위치가 없음. 호스트는 AL2023(GNU date, 시스템 시간대 UTC)인데 로컬은 macOS(BSD date)라 `date -d` 같은 계산이 로컬에서 다르게 동작하거나 실패함.
- 영향: 검사를 통과해도 운영 환경의 동작을 보장하지 못함. BE-01의 시간대 경계에 KST/UTC 사례가 없음. 예를 들어 09:00 KST는 UTC 00:00이고, 08:30 KST에 수동 기동하면 UTC 기준으로는 전날이라 "어제·당일"이 하루 밀림.
- 필요한 수정: 명령을 명시해야 함. 예를 들면 `bash infra/compose/test-boot.sh`를 linux 컨테이너나 python3 등 이식 가능한 구현으로 실행하는 방식임. 사례도 추가해야 함: KST 기준 계산(TZ가 UTC인 호스트), 마지막 완료일이 오늘이거나 미래인 경우(cron 시절 runDate=당일 기록), 레거시 FAILED가 섞인 경우.

### 개선 (완료를 막지 않음)
- SEC-07: 스케줄러 역할의 trust policy에 `aws:SourceAccount`·`aws:SourceArn` 조건을 두면 confused deputy를 막을 수 있음. 루트 볼륨이 고객 관리 KMS 키로 암호화되어 있다면 StartInstances에 kms 권한이 필요할 수 있음. 실제 09:00 시작을 다음 날에야 처음 확인하게 되므로, apply 직후 일회성 `at()` 스케줄로 시작·중지를 미리 확인하는 것을 권함.
- REQ-09 문서에 추가할 내용:
  - Grafana도 함께 꺼짐. Prometheus 지표에 매일 공백이 생김.
  - 09:00 직후 api가 준비되기 전에는 caddy 502가 나서 안내 대신 일반 오류가 보임(REQ-07 정의에 따른 결과).
  - 수동으로 IMAGE_TAG를 고정해 롤백해도 다음 부팅 때 :latest로 덮어써짐.
  - 전환일 한계: cron 시절 runDate=D는 00~02시만 검사했고, 따라잡기는 D+1부터 시작함.
  - 18:00 중지와 겹친 CD는 실패함.
- 공휴일에도 켜지는 것은 비용 영향이 작지만, 사용자에게 한 줄로 확인받거나 가정으로 명시하는 것이 좋음.
- SEC-05 "패턴 검사"의 도구와 명령을 명시하는 것을 권함. 호스트 스크립트는 `bash -n`/shellcheck, Terraform은 `fmt -check`·`validate`로 검사하도록 적용 기준 표에 추가하는 것을 권함. verify에는 셸이나 Terraform 검사가 포함되어 있지 않음.
- FE-05의 "대비 충분"은 jest로 확인할 수 없음. 대비 측정 방법(도구나 수동 측정값)을 적을 것.
- testing-policy는 상세 항목 ID마다 적용 여부와 이유를 요구함. E2E-01~03·05·06과 SEC 나머지 ID의 해당 없음 이유가 "나머지 해당 없음"으로 뭉뚱그려져 있음.
- `.env`의 IMAGE_TAG 형식(전체 sha인지)이 라벨 값과 다르면 부팅할 때마다 재배포됨. 적용 전에 확인할 것.

## 적용 제외 항목 타당성
- FE-02·06·07, 성능 해당 없음: 타당함. 단, 5번의 timeout 설계가 바뀌면 FE-07도 다시 확인해야 함.
- BE-02·03·05~08: 자바 코드 변경이 없으므로 타당함. BE-04는 위 3번의 근거로 이유를 기록하면 됨.
- OPS-02: 부정확함(1번 참고). OPS-06·08: 타당함.
- E2E-04를 Playwright가 아닌 로컬 브라우저로 확인하는 것: 백엔드가 떠 있어야 하는 스위트라는 근거는 타당함. 다만 증거(스크린샷 경로)를 progress.md에 남길 것.

## 이전 지적
- 없음(1회차).

(원문 마지막 줄) 최종 판정 문구: 수정 필요

### 처리 내역

- 중요 1: deploy.sh 전체 flock 잠금, CD는 running이면 SSM Online 최대 3분 대기 후 배포(미등록·조회 실패는 실패), ssm:DescribeInstanceInformation 권한 추가. OPS-02 적용으로 변경하고 모의 검사(cd-skip-test.sh) 추가.
- 중요 2: 설치 스크립트가 부팅 스크립트를 `/usr/local/lib/jbank/`로 복사, 단위 `User=ec2-user`, CD가 매 배포 때 설치 스크립트 실행. 적용 순서: terraform apply(머지 전) → 운영 시간 중 머지(CD가 호스트 설치) → 확인. 운영 시간 밖 머지 시 수동 설치.
- 중요 3: 부팅 직후 STARTED·STARTING 실행을 FAILED로 정리, 잡별 독립 실행, 오늘 완료한 이자 잡 건너뜀, 따라잡기 실패 시 그 잡만 멈춤. BE-04 해당 없음 근거 기록. 같은 날 두 번 부팅 검사 추가.
- 중요 4: api 준비 대기 10분 상한(초과 시 배치 건너뛰고 실패), pull·라벨 실패 시 현재 이미지 유지, 교체 후 재대기, TimeoutStartSec 60분. 모의 검사 사례로 추가.
- 중요 5: 프록시 요청 전체 제한 제거, 연결 단계 오류만 SERVER_OFFLINE. 상태 경로(3초 제한 health GET)로 offline 판정. FE-03에 5초 초과 POST 사례 추가.
- 중요 6: 날짜 계산을 python3(zoneinfo, Asia/Seoul)로, `batch_dates.py --self-test`와 amazonlinux:2023 컨테이너 모의 검사 명령 명시. KST/UTC·오늘·미래 완료일·월·연 경계 사례 추가.
- 개선: 스케줄러 신뢰 정책 SourceAccount, KMS 불필요 근거(AWS 관리 키), 일회성 at() 스케줄 사전 확인, 문서 항목(Grafana 공백·502 구간·IMAGE_TAG 덮어씀·전환일·18시 CD 실패), SEC-05 명령, bash -n·terraform fmt/validate, FE-05 대비 측정 방법, E2E·SEC·BE ID별 해당 없음 이유, IMAGE_TAG 전체 sha 확인. 공휴일은 범위 제외로 사용자 보고에서 한 줄 확인.

## 2회차

회차 계획 지문: d110a02e3063d3b829c76d777fe4f0a9ac9eda98d4a26cdbe21d18b185151d68
회차 판정: 수정 필요

### verifier 응답 원문

- 모드: 계획 리뷰 2회차(재리뷰 1회차)
- 작업 ID: ec2-weekday-schedule
- 검증 대상: `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/task.md`(1회차 지적을 반영한 판, 2026-09-29 시점)

## 읽은 자료
- 기준 문서: docs/testing-policy.md, docs/testing/{devops,security,frontend,backend,e2e}.md
- 작업 문서: task.md, plan-review.md(1회차 원문과 처리 내역), .claude/checks.json
- 인프라: infra/terraform/modules/ec2/{main.tf,user_data.sh.tpl}, infra/compose/{deploy.sh,run-batch.sh,docker-compose.prod.yml,Caddyfile}, .github/workflows/backend-cd.yml
- 프론트: apps/frontend/app/api/proxy/[...path]/route.ts, apps/frontend/lib/api-client.ts
- 백엔드: SecurityConfig(`/actuator/health/**`가 공개 경로임을 확인), batch JobConfig 4개, SingleInstanceJobExecutionListener, build.gradle.kts(Boot 3.5.16, Spring Batch 5.x)

## 적용 기준 ID
SEC-05·07, OPS-01·02·03·04·05·07, BE-01, FE-01·03·04·05, E2E-04

## 확인하지 못한 범위
- 실제 AWS 상태, Vercel 함수 최대 실행 시간, 호스트의 fs.protected_regular 설정은 읽기만으로 확인할 수 없습니다.
- Spring Batch 5.x의 `isRunning` 판정이 상태 기반인지는 라이브러리 소스를 보지 않았습니다. 기억에 의존한 판단입니다.

## 판정: 수정 필요

1회차 중요 1~6은 설계 수준에서 모두 반영되었습니다. 다만 새로 들어간 잠금·설치 설계에서 실제 결함 경로가 생겼습니다. 기준표와 REQ가 어긋나는 곳이 1건 있습니다. 실제 환경 검증 두 가지는 지금 적힌 방법으로는 증거가 나올 수 없습니다.

## 지적

### A. [중요] OPS-05·REQ-03·04: 잠금 범위와 설치 동작이 정해지지 않아 구버전 역배포, 교착, 실행 중 스크립트 덮어쓰기가 생길 수 있음
- 근거
  - 잠금은 `deploy.sh` 전체에만 걸립니다(task.md:40). CD의 `git fetch && git reset --hard`는 deploy.sh 밖, SSM 명령 안에서 실행됩니다(backend-cd.yml:62). 즉 잠금 밖입니다.
  - 부팅 작업의 `:latest` pull과 라벨 비교가 잠금 안인지 밖인지 적혀 있지 않습니다(task.md:38,45).
  - CD는 매 배포 때 설치 스크립트를 root로 실행해 `/usr/local/lib/jbank/`의 부팅 스크립트를 교체합니다(task.md:41).
  - CD의 결과 폴링은 최대 5분입니다(backend-cd.yml:66, 60회 x 5초).
- 영향
  - (a) 역배포: 부팅 작업이 잠금 밖에서 `:latest` 라벨 X를 읽습니다. 그 사이 CD가 Y를 올리고 잠금을 잡아 Y를 배포합니다. 이후 부팅 작업이 잠금을 얻어 X를 배포하면 api가 구버전 X로 돌아갑니다. 이는 REQ-04(최신 main 반영)와 사용자 결정 Q-02에 위배됩니다.
  - (b) 교착: 부팅 작업이 잠금을 쥔 채 deploy.sh를 호출하면, deploy.sh가 같은 파일을 다시 flock합니다. 이 경우 새 파일 설명자로 잠그게 되어 자기 자신을 기다리며 멈춥니다. 결국 TimeoutStartSec 60분에서 강제 종료되고 배치가 돌지 않습니다.
  - (c) git 충돌: CD의 잠금 밖 `git reset`이 부팅 작업의 잠금 안 `git reset`과 겹치면 index.lock 충돌이 납니다. 부팅 작업 쪽에서 git이 실패했을 때의 정책도 없습니다(2단계는 pull·라벨 실패만 정의).
  - (d) 실행 중 스크립트 덮어쓰기: 부팅 작업이 배치를 도는 시각은 평일 09시대입니다. 이때 머지 CD가 설치 스크립트로 부팅 스크립트를 제자리 덮어쓰기(cp)하면, 1회차 지적 2에서 피하려던 "bash가 실행 중 파일을 조금씩 읽다 오동작" 문제가 다시 생깁니다. 적용 순서 ②(운영 시간 중 머지)에서 실제로 겹칠 수 있습니다.
  - (e) 부팅 작업 재실행: 설치 스크립트가 `systemctl enable --now` 또는 `restart`를 쓰면, RemainAfterExit가 없는 oneshot은 CD 배포 때마다 부팅 작업 전체(이미지 반영과 배치)를 다시 실행합니다.
  - (f) CD 거짓 실패: CD가 부팅 작업의 잠금을 5분 넘게 기다리면 배포는 결국 되는데 CD는 실패로 끝납니다.
- 필요한 수정
  - 잠금 범위를 명세에 적어야 합니다. `:latest` pull·라벨 비교, git 갱신, 배포를 하나의 잠금 안에 넣고, 준비 대기와 배치는 잠금 밖에 둡니다.
  - CD의 git 갱신도 같은 잠금 안에서 하도록 바꿉니다(예: 잠금을 잡는 래퍼 하나를 두고 CD와 부팅 작업이 모두 그것을 호출).
  - 중첩 호출 방식을 정합니다. 잠금을 쥔 쪽이 잠금 없는 내부 경로를 부르는 방식 등입니다.
  - 설치는 원자 교체(임시 파일을 만든 뒤 mv)로 합니다. 단위는 `enable`만 하고 기동하지 않는다고 명시합니다.
  - 잠금 최대 보유 시간과 CD 폴링 한도의 관계를 적습니다.
- 필요한 테스트: boot-test나 cd-skip-test에 "부팅 작업이 비교하는 도중 CD 배포가 끼어드는" 사례를 넣습니다. 최종 IMAGE_TAG가 더 새로운 sha여야 하고 교착이 없어야 합니다.

### B. [중요] SEC-07 대 REQ-08: 통과 기준이 서로 어긋남
- 근거
  - REQ-08과 기술 선택(task.md:39,77): 배포 역할에 `ec2:DescribeInstances`와 `ssm:DescribeInstanceInformation`을 추가합니다.
  - SEC-07 행(task.md:116): 기대 결과가 "배포 역할 추가 = ec2:DescribeInstances만"입니다.
- 영향: 결과 리뷰에서 plan 발췌가 어느 쪽 기준으로도 불일치가 됩니다.
- 필요한 수정: SEC-07 기대 결과에 `ssm:DescribeInstanceInformation`(리소스 `*`, 읽기 전용)을 넣습니다.

### C. [중요] OPS-05·REQ-04: 실제 검증 방법으로는 "다음 부팅에 교체" 증거가 나오지 않음
- 근거: OPS-05의 실제 검증은 "18시 이후 `gh workflow run backend-cd --ref main` → 다음 09시 부팅 로그"입니다(task.md:122). 그런데 머지는 운영 시간 중에 하고 CD가 그 sha를 바로 배포합니다(task.md:42). 그래서 18시 이후 수동 실행은 같은 sha를 다시 올리게 되고, 다음 부팅은 "같은 sha → 교체 안 함" 경로만 탑니다.
- 영향: 사용자 결정 Q-02의 핵심인 교체 경로(라벨이 다르면 deploy)가 실제 환경에서 한 번도 확인되지 않습니다. 결과 리뷰에서 증거가 빠지게 됩니다.
- 필요한 수정: 꺼진 시간에 main에 새 커밋이 있게 만드는 절차를 적습니다. 예를 들어 운영 시간 밖에 devlog나 하네스 기록 커밋을 main에 머지한 뒤 workflow_dispatch를 실행합니다(workflow_dispatch에는 paths 필터가 없음). 기대 결과도 함께 적습니다: CD는 성공+경고, 다음 부팅에서 IMAGE_TAG가 새 sha로 바뀜. 같은 sha 경로는 별도 사례로 둡니다.

### D. [중요] REQ-05·06, BE-01: 중단 기록 정리와 마지막 완료일 조회 SQL을 실제 DB로 확인하지 않음(1회차 지적 3(b) 수정의 재검증 부족)
- 근거
  - "STARTED·STARTING → FAILED로 정리 → 다음 실행에서 같은 날짜 재시작"과 "마지막 COMPLETED runDate 조회"는 가짜 psql로만 검사합니다(task.md:120,124).
  - 실제 부팅 검증(task.md:138)은 "같은 날 두 번 부팅"뿐이라 STARTED 기록을 만들지 않습니다.
- 영향: 정리 SQL이 Spring Batch 5의 실행 중 판정과 맞지 않으면 문제가 생깁니다. 예로 BATCH_JOB_EXECUTION의 STATUS만 바꾸고 END_TIME이나 STEP_EXECUTION 상태를 두는 경우가 있습니다. 그러면 1회차 3(b)의 "따라잡기 영구 정지"가 그대로 남고, 모의 검사는 이를 잡지 못합니다. 레거시 FAILED가 섞인 상태에서 마지막 완료일을 고르는 조회도 같은 이유로 검증되지 않습니다.
- 필요한 수정·테스트: 실제 PostgreSQL에서 검사하는 사례를 1개 추가합니다(로컬 compose나 머지 후 인스턴스).
  - 잡 1회 실행 → 해당 실행을 STARTED(END_TIME NULL)로 조작 → 정리 SQL 실행 → 같은 파라미터로 잡 재실행이 성공하는지 확인합니다.
  - COMPLETED와 FAILED가 섞인 기록에서 마지막 완료일 조회 결과가 맞는지 확인합니다.
  - 정리 SQL이 바꾸는 컬럼(STATUS, EXIT_CODE/EXIT_MESSAGE, END_TIME, 스텝 실행)을 명세에 적습니다.

## 개선 (완료를 막지 않음)
- 일반 테스트 방법 표(task.md:139-142)의 실행 시점이 커밋 계획과 맞지 않습니다.
  - REQ-06: 적힌 시점은 "커밋 2 후"인데 날짜 계산은 커밋 3입니다.
  - REQ-07: 적힌 시점은 "커밋 4 후"인데 프론트는 커밋 5입니다.
  - REQ-09: 적힌 시점은 "커밋 5 후"인데 문서는 커밋 6입니다.
- `run-batch.sh`는 지금 runDate=오늘만 넘길 수 있습니다(run-batch.sh:12-13). 임의 날짜 인자 추가와 cron 관련 주석 정리를 범위에 명시하길 권합니다.
- 새 검사(`batch_dates.py --self-test`, `tests/boot-test.sh`, `tests/cd-skip-test.sh`)가 checks.json에 없어서 verify 증거가 되지 않습니다. 추가를 검토하길 권합니다. 추가하면 README 동기화도 필요합니다.
- 잠금 파일 위치 `/tmp/jbank-deploy.lock`: 누군가 root로 deploy.sh를 수동 실행해 파일을 만들면, sticky `/tmp`와 protected_regular 설정 때문에 ec2-user가 그 파일을 열지 못할 수 있습니다. ec2-user 소유 디렉터리(예: `/var/lock/jbank`를 설치 스크립트가 생성)를 권합니다.
- 상태 경로의 online 판정: `/actuator/health`는 redis 등 구성 요소가 DOWN이면 503을 줍니다. 이때 서버가 떠 있어도 "꺼짐" 안내가 나갈 수 있으니, 판정 기준(HTTP 응답 수신 여부 또는 200 여부)을 명시하길 권합니다.
- 부팅 작업 실패(단위 failed)나 따라잡기가 반복 실패할 때 알림이 없고 로그만 남습니다. OPS-07 "알림" 항목은 로그로 대체한 것이므로 문서에 한계로 적길 권합니다.

## 적용 제외 항목의 이유와 타당성
- FE-02·06·07, 성능 해당 없음: 타당합니다. 요청 전체 시간 제한을 없앴으므로 FE-07 재확인도 필요 없습니다.
- BE-02·03·05~08: 타당합니다. BE-04는 중복 방지 근거(CTR 존재 검사, FDS 신규 적재, 이자 ACTIVE 필터)가 코드 조사와 맞아 타당합니다. 단, 동시 실행 방지 근거 중 "배포 잠금"은 A를 고친 뒤 유효합니다.
- OPS-06·08: 타당합니다.
- SEC-01~04·06·08: 타당합니다. 상태 경로는 고정 URL이고 `/actuator/health/**`는 이미 공개 경로입니다.
- E2E-01·02·03·05·06 제외와 E2E-04를 로컬 브라우저로 확인하는 것: 타당합니다.
- 공휴일 범위 제외: 비용 영향이 작고 명시되어 있어 타당합니다. 사용자 보고에서 한 줄로 확인받으면 됩니다.

## 이전 지적별 처리
- 중요 1(동시 배포, SSM 준비 전 구간, OPS-02): 해결. 잠금 추가, Online 3분 대기, 조회 실패 시 실패 처리, OPS-02 적용과 모의 검사가 반영되었습니다. 단, 잠금 범위에서 새 결함이 생겼습니다(지적 A).
- 중요 2(머지 전 설치와 git reset 충돌): 해결. 저장소 밖 경로 복사, `User=ec2-user`, 적용 순서가 반영되었습니다. 단, CD 설치가 실행 중인 스크립트를 덮어쓸 수 있습니다(지적 A-d·e).
- 중요 3(Batch 재실행 제약): 설계는 해결되었습니다(정리, 잡별 독립 실행, 이자 잡 건너뜀, 실패 시 그 잡만 정지, BE-04 근거). 실제 DB 검증은 미흡합니다(지적 D).
- 중요 4(부팅 실패 사례): 해결. 10분 상한, pull 실패 시 유지, 교체 후 재대기, TimeoutStartSec, 모의 사례가 반영되었습니다.
- 중요 5(요청 전체 시간 제한): 해결. 연결 단계 오류만 503으로 바꾸고, 상태 경로는 3초 제한 GET, 5초 초과 POST 사례를 추가했습니다.
- 중요 6(명령·환경·경계 사례): 해결. python3 zoneinfo, 구체 명령, amazonlinux:2023 컨테이너, KST/UTC·오늘·미래·월·연 경계 사례가 반영되었습니다. 레거시 FAILED 혼재 사례는 SQL 쪽이라 D에 포함했습니다.
- 개선 항목: 대부분 반영되었습니다(SourceAccount, KMS 근거, at() 사전 확인, REQ-09 문서 항목, SEC-05 명령, bash -n과 terraform fmt/validate, FE-05 대비 측정, ID별 제외 이유, IMAGE_TAG 형식 확인). SourceArn 조건은 넣지 않았지만 선택 사항이라 문제없습니다.

(원문 마지막 줄) 최종 판정 문구: 수정 필요

### 처리 내역

- 중요 A: 잠금은 호출하는 쪽이 `/var/lib/jbank/deploy.lock`으로 잡고 deploy.sh 안에서는 안 잡음(중첩 교착 없음). CD는 git 갱신+배포를, 부팅은 `:latest` pull·라벨 비교·git 갱신·배포를 각각 한 잠금 안에서. 준비 대기·배치는 잠금 밖. 설치는 원자 교체(임시 파일→mv), 단위는 enable만(기동 안 함). CD 폴링 10분. 부팅 쪽 git 실패 시 유지·계속. 경합 모의 사례(실제 flock 두 프로세스) 추가.
- 중요 B: SEC-07 기대 결과에 ssm:DescribeInstanceInformation(리소스 *, 읽기) 포함, REQ-08과 문구 일치.
- 중요 C: 교체 경로 실제 검증 절차를 "머지 CD로 X 배포 → 18시 전 이전 sha(fa97aef)로 되돌림 → 중지 후 CD 수동 실행(성공+경고) → 다음 09시 부팅에서 fa97aef→X 교체 → 같은 날 재부팅은 교체 없음"으로 명시.
- 중요 D: 정리 SQL 대상 컬럼(JOB·STEP 실행의 STATUS·EXIT_CODE·EXIT_MESSAGE·END_TIME)과 마지막 완료일 조회 조인을 명시. 실제 PostgreSQL 사례(과거 날짜 FDS 실행 → STARTED 조작 → 정리 → 재실행 성공, COMPLETED·FAILED 혼재 조회)를 BE-01(실제 DB) 행으로 추가.
- 개선: 일반 테스트 표 실행 시점을 커밋 번호에 맞춤. run-batch.sh 임의 기준일 인자와 주석 정리를 커밋 2로 분리. 새 인프라 검사는 verify에 넣지 않고 직접 실행 출력을 남기는 이유 기록. 잠금 위치 /var/lib/jbank(ec2-user 소유). 상태 경로 online 판정 = HTTP 응답 수신(502·503도 online), 09시 직후 502 구간 한계 문서화. 부팅 작업 알림 없음 한계 문서화.

## 3회차

회차 계획 지문: b6bc033b7381292aff2142ba336e77554bc50523664a3ee444c317f483dfce18
회차 판정: 수정 필요

### verifier 응답 원문

- 모드: 계획 리뷰 3회차(재리뷰 2회차, 마지막 허용 회차)
- 작업 ID: ec2-weekday-schedule
- 검증 대상: `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/task.md`(2회차 지적 반영판, 2026-09-29 시점)

## 읽은 자료
- 기준 문서: docs/testing-policy.md. 상세 기준(devops·security·frontend·backend·e2e)은 1·2회차에 확인한 내용을 그대로 적용했습니다.
- 작업 문서: task.md, plan-review.md(1·2회차 원문과 처리 내역)
- 코드
  - .github/workflows/backend-cd.yml
  - infra/compose/{deploy.sh, run-batch.sh, docker-compose.prod.yml}
  - infra/terraform/modules/ec2/user_data.sh.tpl
  - apps/jbank-api/.../batch/lock/SingleInstanceJobExecutionListener.java
  - JobBuilder 4개(incrementer 없음 확인)

## 적용 기준 ID
SEC-05·07, OPS-01·02·03·04·05·07, BE-01, FE-01·03·04·05, E2E-04

## 확인하지 못한 범위
- 읽기만으로는 실제 AWS·호스트 상태를 확인할 수 없습니다. 대상은 `/var/lib/jbank` 존재 여부, flock 버전, GHCR 태그 목록입니다.
- Spring Batch 5.x의 실행 중 판정 방식(END_TIME 기준인지 상태 기준인지)은 라이브러리 소스를 보지 않고 판단했습니다. 정리 SQL이 STATUS와 END_TIME을 모두 바꾸므로 어느 방식이든 풀립니다. 실제 동작은 BE-01(실제 DB) 사례에서 확인됩니다.

## 판정: 수정 필요
2회차 중요 A~D는 모두 설계와 검증 절차에 반영되었습니다. 다만 A를 고치면서 새 잠금 경로가 생겼고, 이 때문에 적용 순서 ②(머지 CD가 호스트 설치)가 첫 실행에서 반드시 실패하는 결함이 새로 생겼습니다(지적 E). 이 결함은 제품 결정 없이 명세 한 줄로 고칠 수 있습니다. 그래도 미해결 중요 지적이라 통과 권고하지 않습니다.

## 지적

### E. [중요] OPS-05·REQ-03, 적용 순서 ②: 첫 머지 CD가 잠금 파일 디렉터리가 없어 실패함 (계획을 막는 결함, 수정은 한 줄)
- 근거
  - task.md:41에 따르면 CD의 SSM 명령은 `runuser -u ec2-user -- flock /var/lib/jbank/deploy.lock ...` 형태입니다.
  - 그런데 `/var/lib/jbank`를 만드는 것은 설치 스크립트이고(task.md:46), 설치 스크립트는 "배포(잠금 안) 뒤" 실행됩니다(task.md:46).
  - 기존 인스턴스에는 이 디렉터리가 없습니다. user_data.sh.tpl은 `/var/log/jbank`만 만듭니다.
  - ec2-user는 root 소유인 `/var/lib` 아래에 디렉터리를 만들 수 없습니다.
  - flock(1)은 잠금 파일을 열지 못하면 명령을 실행하지 않고 0이 아닌 값으로 끝납니다.
- 재현 경로: 적용 순서 ②에서 운영 시간 중에 머지하면 새 backend-cd가 돕니다.
  1. SSM 명령의 flock이 `No such file or directory`로 실패합니다.
  2. SSM 상태가 Failed가 되어 CD가 실패합니다.
  3. 그래서 배포도, 설치 스크립트도 실행되지 않습니다.
  4. jbank-boot 단위가 없어서 그날 18:00 중지 → 다음 날 09:00 부팅 때 배치가 돌지 않습니다.
  5. cron은 남아 있지만 인스턴스가 꺼진 새벽 시간이라 실행되지 않습니다.
  - 이 문제는 OPS-05 ①(머지 CD 배포) 실제 확인에서 곧바로 드러납니다. 다만 수정하려면 추가 커밋·PR·재배포가 필요합니다.
  - 모의 검사(cd-skip-test.sh, 가짜 aws)로는 잡히지 않습니다.
- 필요한 수정
  - CD의 SSM 명령에서 root 권한으로 먼저 `install -d -o ec2-user -g ec2-user /var/lib/jbank`를 실행한 뒤 flock을 잡도록 명세에 적습니다. 대안으로 잠금 경로를 이미 있는 `/var/log/jbank`처럼 ec2-user 소유 디렉터리로 두는 방법도 있습니다.
  - 적용 순서 ②와 범위의 "기존 인스턴스에 설치 스크립트 1회 실행"(task.md:69)의 관계를 한 문장으로 정리합니다. CD가 설치하는지, 수동으로 설치하는지입니다.
  - cd-skip-test.sh의 기대 SSM 명령 문자열 검사에 디렉터리 생성이 flock보다 먼저 오는지를 포함합니다.

### 개선 (완료를 막지 않음, 구현 단계에서 처리 가능)
1. OPS-05 실제 검증 ②의 `IMAGE_TAG=<이전 sha fa97aef>`는 전체 40자 sha로 적어야 합니다. GHCR 태그는 `${{ github.sha }}`(전체 sha)이고 `.env`도 전체 sha 형식입니다(backend-cd.yml:40, task.md:20). 짧은 sha를 쓰면 pull이 실패합니다.
2. BE-01(실제 DB) 사례의 "과거 날짜"에는 조건이 필요합니다. 두 가지를 모두 만족하는 날짜를 명시하세요.
   - 조건 1: FDS 실행 기록이 없는 날짜여야 합니다. 이미 COMPLETED인 날짜로 첫 실행을 하면 `JobInstanceAlreadyCompleteException`으로 바로 실패합니다.
   - 조건 2: 현재 FDS의 마지막 COMPLETED runDate보다 이른 날짜여야 합니다. 더 늦은 날짜를 고르면 마지막 완료일이 앞당겨지고, 그 사이 날짜의 따라잡기가 영구히 빠집니다(FDS 검사 공백).
   - 예: 서비스 이전의 날짜.
   - 그 날짜에 거래가 있으면 FDS 결과가 새로 적재된다는 점도 기록하세요.
3. REQ-05 본문은 "STARTED·STARTING"이고, 기술 선택 3단계는 "STARTING·STARTED·STOPPING"입니다(task.md:51, 80). 둘을 같은 목록으로 맞추세요.
4. "일반 테스트 방법" 표 중간에 설명 문단(task.md:150)이 끼어 있습니다. 그래서 REQ-09·회귀 행(151-152)이 표에서 떨어져 나갑니다. 문단을 표 아래로 옮기세요.
5. REQ-03의 "최종 이미지는 둘 중 최신 revision"은 CD 대 부팅 경합에 대해서는 잠금으로 보장됩니다. 그러나 CD 두 개가 겹치는 경우는 보장되지 않습니다. backend-cd에 `concurrency`가 없어서 먼저 시작한 실행이 나중에 `:latest`를 덮을 수 있습니다. 이는 기존에도 있던 문제입니다. `concurrency: {group: backend-cd}` 한 줄을 추가하거나, 한계로 문서에 적으세요.
6. 새 인스턴스는 user_data가 설치 스크립트를 부르지만 `.env`를 수동으로 채우기 전까지 api가 뜨지 않습니다. 그래서 첫 재부팅에서 jbank-boot가 10분 대기 후 failed가 됩니다. 의도된 동작이라면 문서에 한 줄 남기세요.

## 적용 제외 항목의 이유와 타당성
- 2회차 판단에서 바뀐 점이 없습니다. FE-02·06·07, BE-02·03·05~08, OPS-06·08, SEC-01~04·06·08, E2E-01~03·05·06, 성능 해당 없음 모두 타당합니다.
- BE-04 해당 없음(중복 방지 근거 + Redis 락 + 배포 잠금)도 타당합니다. 지적 A가 해결되어 "배포 잠금" 근거가 유효해졌습니다.
- ledgerReconciliationJob은 식별 파라미터가 없어서 COMPLETED 뒤 재실행이 허용되는 것으로 보입니다(Spring Batch의 식별 파라미터 0개 예외). 이 부분은 라이브러리 기억에 의존한 판단입니다. 같은 날 두 번 부팅하는 실제 검증(REQ-04·05 행)에서 확인됩니다.
- Redis 락 실패 시 beforeJob이 예외를 던집니다. 그러면 FAILED로 끝나 재시작이 가능하다는 task.md:52의 가정은 리스너 코드(SingleInstanceJobExecutionListener.java:45-47)와 일치합니다.

## 이전 지적별 처리
- A(잠금 범위·교착·git 충돌·덮어쓰기·재실행·거짓 실패): 해결되었습니다. 근거는 다음과 같습니다(task.md:40-46).
  - 호출 쪽 잠금과 deploy.sh 내부 무잠금으로 교착이 없습니다.
  - CD는 git 갱신과 배포를, 부팅은 pull·비교·git·배포를 각각 한 잠금 안에서 합니다.
  - 준비 대기와 배치는 잠금 밖입니다.
  - 설치는 원자 교체(mv)이고, 단위는 enable만 합니다.
  - CD 결과 폴링은 10분입니다.
  - 부팅 쪽 git 실패 시 현재 상태를 유지하고 계속합니다.
  - 실제 flock으로 두 프로세스를 경합시키는 모의 사례가 있습니다.
  - 단, 이 수정으로 새 결함 E가 생겼습니다.
- B(SEC-07 대 REQ-08 불일치): 해결되었습니다(task.md:83, 122 문구 일치).
- C(교체 경로 실제 증거): 해결되었습니다. 이전 sha로 되돌린 뒤 중지 → CD 수동 실행(성공+경고) → 다음 부팅에서 교체 → 같은 날 재부팅에서 교체 없음 순서로, 교체 경로와 같은 sha 경로가 모두 확인됩니다. 단, 개선 1(전체 sha)을 반영해야 합니다.
- D(정리 SQL·마지막 완료일 조회 실제 DB 검증): 해결되었습니다. 변경 컬럼(JOB·STEP 실행의 STATUS·EXIT_CODE·EXIT_MESSAGE·END_TIME)과 조회 조인이 명시되었고, 실제 PostgreSQL 사례가 추가되었습니다(task.md:51, 53, 130). 단, 개선 2(날짜 선택 조건)를 반영해야 합니다.
- 2회차 개선 항목: 모두 반영되었습니다. 대상은 실행 시점 정정, run-batch.sh 인자 분리, verify 미포함 이유, 잠금 위치 변경, online 판정 기준, 알림 없음 한계입니다. 잠금 위치를 `/var/lib/jbank`로 바꾼 것이 E의 원인입니다.

## 마지막 회차 남은 문제와 선택지
- 남은 문제는 E 하나이며, 제품 결정은 필요 없습니다.
- 영향: 머지 당일 CD가 실패하고, 설치가 안 되어 다음 날부터 배치가 멈춥니다. 복구할 수 있고 데이터 유실은 없지만, 추가 PR이 필요합니다.
- 선택지
  - (1) task.md에 SSM 명령의 디렉터리 선생성 한 줄을 반영하고, 사용자 허락을 받아 계획 리뷰를 1회 늘려 확인합니다.
  - (2) 사용자 동의 아래 E를 반영한 뒤 계획을 확정하고, 결과 리뷰에서 반영 여부와 머지 CD 성공 증거를 확인합니다.
  - (3) 잠금 경로를 이미 있는 ec2-user 소유 디렉터리(`/var/log/jbank` 등)로 바꿔 선행 조건을 없앱니다.
- 개선 1~6은 구현 단계에서 처리해도 됩니다.

(원문 마지막 줄) 최종 판정 문구: 수정 필요

### 처리 내역

- 중요 E: CD SSM 명령이 root로 `install -d -o ec2-user -g ec2-user /var/lib/jbank`를 먼저 실행한 뒤 flock(명세 반영). 범위의 "설치 1회"를 "머지 CD가 설치, 운영 시간 밖 머지면 수동 1회"로 정리. cd-skip-test에 명령 순서 검사 추가.
- 개선 1: 되돌릴 sha를 전체 40자로. 개선 2: BE-01(실제 DB) 날짜 조건(기록 없음·마지막 완료일보다 이른 날짜, 예 2026-01-01). 개선 3: REQ-05 상태 목록을 STARTING·STARTED·STOPPING으로 통일. 개선 4: 설명 문단을 표 밖으로 이동. 개선 5: backend-cd `concurrency` 추가. 개선 6: 새 인스턴스 첫 부팅 failed를 문서 항목에 추가.
- 계획 리뷰 한도(최초 1 + 재리뷰 2) 도달. 반영판 확인을 위한 1회 연장은 사용자 허락 대기.

## 4회차 (사용자 1회 추가 허락, 2026-09-29)

대상 계획 지문: ad4117ffabe5c31cc30aeb60673a03222cf0e20ba7cddb2f7bffce60faacd729
최종 판정: 통과 권고

### verifier 응답 원문

계획 리뷰 4회차 결과는 통과 권고이고, 보고서는 메인에 넘겼습니다. 3회차 지적 E와 개선 1~6은 모두 반영됐고, 반영하면서 계획을 막는 새 모순이나 결함은 찾지 못했습니다.

**반영 확인**
- **E(첫 머지 CD가 잠금 디렉터리가 없어 실패)**: root SSM 명령이 `install -d -o ec2-user -g ec2-user /var/lib/jbank`를 flock보다 먼저 실행합니다(task.md:42). 범위와 적용 순서도 "머지 CD가 설치하고, 운영 시간 밖에 머지하면 수동으로 1회 설치"로 맞췄습니다. cd-skip-test가 명령 순서도 검사합니다(task.md:146).
- **개선 1**: 되돌릴 sha `fa97aef8147f2228d2a95086092bdce2d05fa7ba`가 `.git/refs/heads/main` 값과 같습니다.
- **개선 2**: BE-01(실제 DB)의 날짜 조건(실행 기록 없음, 마지막 완료일보다 이른 날짜, 예 2026-01-01)이 들어갔습니다.
- **개선 3~6**: 상태 목록을 STARTING·STARTED·STOPPING으로 통일했고, 설명 문단을 표 밖으로 옮겼습니다. backend-cd에 `concurrency`를 추가했고, 새 인스턴스 첫 부팅이 failed가 되는 점을 문서 항목에 넣었습니다.
- **Q-06(공휴일)**: 질문 표, 기술 선택, 하지 않을 일이 모두 "공휴일 인식 제외"로 일치합니다. 하루 약 $0.24도 계산이 맞습니다.

**구현 단계에서 처리할 개선 3건 (완료를 막지 않음)**
1. 커밋 4 설명에 `concurrency`와 잠금 디렉터리 선생성이 빠져 있습니다. 같은 커밋에 넣고 설명에도 적어야 합니다.
2. OPS-05 ②에서 이전 이미지로 되돌리려면 GHCR에 `:fa97aef…` 태그가 있어야 합니다. 실행 전에 인스턴스 `.env`의 IMAGE_TAG나 GHCR 태그 목록으로 있는지 확인하고, 없으면 실제로 있는 이전 sha로 바꿔 기록해야 합니다.
3. BE-01(실제 DB)의 정리 SQL은 실행 중인 기록을 모두 FAILED로 바꿉니다. 부팅 작업의 따라잡기가 돌고 있을 때 실행하면 살아 있는 실행까지 FAILED가 됩니다. 전환 후 첫 부팅은 약 12일치를 따라잡아 오래 걸릴 수 있습니다. `systemctl is-active jbank-boot`가 inactive인지 먼저 확인하는 절차를 progress.md에 남겨야 합니다.

**확인하지 못한 범위**
- GHCR 태그가 실제로 있는지는 파일만 읽어서 알 수 없습니다. task.md:20의 "api 이미지 fa97aef"는 메인이 조사한 결과입니다.
- Q-06 사용자 답변은 원문 대화가 없어 task.md 기록만 근거로 삼았습니다.
- AWS와 호스트의 실제 상태는 확인하지 못했습니다.

적용 제외 항목의 판단은 3회차와 같고 모두 타당합니다. 1~3회차 지적은 모두 해결됐고, 새 차단·중요 지적은 없습니다.

관련 파일:
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/task.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/plan-review.md
- /Users/imjaehyeon/Documents/[01] - Project/j-bank/.github/workflows/backend-cd.yml

(원문 마지막 줄) 최종 판정 문구: 통과 권고

### 처리 내역

- 개선 1~3은 명세를 바꾸지 않는 구현 세부라 progress.md에 적고 구현에서 반영한다(커밋 4 설명에 concurrency·잠금 디렉터리 선생성 포함, OPS-05 ② 전 GHCR 태그 존재 확인, BE-01 실제 DB 정리 SQL은 jbank-boot inactive·실행 중 잡 없음 확인 후).
