# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 1번으로 하자, 평일 낮에만 켜줘
(직전 대화의 "1번" = 운영 EC2를 필요할 때만 켜서 비용을 줄이는 안. 앞선 요청 "이거 항상 띄워야하면 돈이 나가는데 더 저렴이 없어?")
요청 해석: 운영 EC2(jbank-dev-app)를 평일 정해진 시간에만 자동으로 켜고 끈다. 꺼진 시간에 생기는 부작용(배포 실패, 새벽 배치 미실행, 프론트 오류 화면)도 사용자 결정대로 함께 처리한다.
조사한 사실:
- 운영은 EC2 t3.small 1대 + Docker Compose(api·caddy·postgres·redis·prometheus·grafana), 컨테이너 `restart: unless-stopped`라 인스턴스를 끄고 켜도 스택이 다시 올라온다(2026-09-29 시작 후 확인).
- 비용: 인스턴스 월 약 $19, 공인 IPv4 약 $3.6, gp3 20GB 약 $1.8(IPv4·디스크는 꺼도 과금).
- backend-cd: main push(apps/jbank-api·infra/compose 경로) 때 이미지를 `:<sha>`·`:latest`로 GHCR에 올리고 SSM으로 `deploy.sh`(IMAGE_TAG를 인스턴스 `.env`에 고정)를 실행. 인스턴스가 꺼져 있으면 SSM이 `InvalidInstanceId`로 실패(2026-09-17 수동 중지 이후 3회 실패, 원인 확인).
- 배포 역할 권한: ssm:SendCommand(해당 인스턴스·AWS-RunShellScript), GetCommandInvocation. 인스턴스 상태 조회 권한 없음. OIDC sub는 main 브랜치만 허용.
- 배치: 인스턴스 `/etc/cron.d/jbank`(user_data 설치, `ignore_changes = [user_data]`)가 매일 01:00 interestMaturityJob(runDate=당일), 02:00 ctrDetectionJob(runDate=당일), 03:00 ledgerReconciliationJob, 04:00 fdsDetectionJob(runDate=당일)을 `infra/compose/run-batch.sh`로 실행.
  - interestMaturity는 "만기 < runDate+1"을 모두 처리해 밀린 날짜도 따라잡는다. 대사는 날짜 인자 없음.
  - CTR·FDS는 runDate 하루(00:00~24:00) 거래만 검사한다. 지금도 새벽 실행 + 당일 날짜라 그날 새벽분만 보고 있다(기존 한계).
  - Spring Batch 실행 기록(BATCH_JOB_EXECUTION·_PARAMS)이 postgres에 남아 마지막 완료 runDate를 조회할 수 있다.
- 프론트(Vercel)는 모든 API를 `/api/proxy/[...path]`(서버 fetch)로 백엔드에 전달. 백엔드 연결 실패 시 예외 처리가 없어 500/504가 그대로 나간다. Vercel 함수 기본 최대 실행 시간이 짧아 꺼진 IP로의 연결 대기가 504로 끝날 수 있다.
- infra-cd는 `TERRAFORM_CI_READY`가 켜져야 돈다. dev 스택은 로컬에서 원격 상태(S3)로 plan/apply 한다(이전 작업에서 plan 실행 확인).
- 현재 시각 기준 인스턴스는 running(2026-09-29 10:51 KST 시작), api 이미지 fa97aef. 인스턴스 `.env`의 IMAGE_TAG는 전체 40자 sha 형식.
- 배치 잡에 incrementer가 없어 같은 파라미터 재실행은 COMPLETED면 `JobInstanceAlreadyCompleteException`, STARTED로 남은 기록이 있으면 `JobExecutionAlreadyRunningException`으로 실패하고, FAILED는 같은 파라미터로 재시작된다. 잡 실행은 Redis 락(lease 30분, SingleInstanceJobExecutionListener)으로 단일화. redis:7은 이미지의 `/data` 익명 볼륨이라 재시작 뒤에도 스냅샷 시점의 락이 남을 수 있다(최대 30분).
- 같은 날짜 재실행 시 데이터 중복: CTR은 존재 검사, FDS는 신규 적재만, 이자는 ACTIVE 계약만 처리해 중복이 생기지 않는다.
- 루트 볼륨은 `encrypted = true`(KMS 키 지정 없음 = AWS 관리 키)라 StartInstances에 KMS 권한이 필요 없다.
- 호스트는 AL2023(시스템 시간대 UTC), python3 기본 설치. 로컬은 macOS(BSD date).
질문하지 않은 이유: 아래 표의 항목은 모두 사용자에게 물어 결정했다. 기술 선택은 근거와 함께 적는다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 운영 시간 | 월~금 09:00~18:00 KST | 해결 |
| Q-02 | 꺼진 시간 main push의 배포 | 배포를 건너뛰고(성공+경고), 다음에 켜질 때 main의 최신 이미지로 자동 반영 | 해결 |
| Q-03 | 꺼진 시간 프론트 화면 | 운영 시간 안내 표시 | 해결 |
| Q-04 | 새벽 배치 4개 | 켜진 직후 실행으로 옮김 | 해결 |
| Q-05 | CTR·FDS 검사 날짜 | 전날까지 빠진 날짜를 모두(마지막 완료 기준일 다음 날 ~ 어제, 하루씩) | 해결 |
| Q-06 | 공휴일에도 평일이면 켜짐(하루 약 $0.24) | 괜찮음(2026-09-29 사용자 확인) | 해결 |

기술 선택(근거):
- 예약: EventBridge Scheduler(시간대 Asia/Seoul 지원, 무료 한도 내). 시작은 월~금 09:00, 중지는 **매일** 18:00 — 주말·저녁에 수동으로 켠 경우도 그날 18:00에 꺼져 요금 폭주를 막는다. 스케줄러 역할은 이 인스턴스의 ec2:StartInstances·StopInstances만 허용.
- 공휴일: 스케줄러는 공휴일을 모른다. 공휴일에도 켜진다(하루 약 $0.24). 사용자 확인(Q-06)으로 공휴일 인식은 범위 제외.
- 최신 이미지 반영: CD가 이미지에 `org.opencontainers.image.revision=<sha>` 라벨을 붙인다. 부팅 후 호스트가 `:latest`를 받아 라벨의 sha가 `.env`의 IMAGE_TAG와 다르면 저장소를 main으로 맞추고 그 sha로 `deploy.sh`를 실행한다. 라벨이 없는 이미지(이 변경 전 빌드)는 교체하지 않는다. 새 AWS 리소스·권한 없이 GHCR만 쓴다.
- 배포 건너뛰기 판단: CD가 `ec2:DescribeInstances`로 상태를 본다. running이면 SSM PingStatus가 Online이 될 때까지 최대 3분 기다린 뒤 배포한다(부팅 직후 SSM 미등록 구간). running이 아니면 배포 단계를 건너뛰고 경고를 남긴 뒤 성공 종료한다. 상태 조회 호출 자체가 실패하거나 3분 안에 Online이 안 되면 실패한다(실패를 성공으로 바꾸지 않음). 배포 역할에 ec2:DescribeInstances·ssm:DescribeInstanceInformation(둘 다 리소스 지정 불가 읽기 액션) 추가. 검증용 `workflow_dispatch` 추가(OIDC sub가 main만 허용).
- 동시 배포 방지(잠금 범위): 잠금 파일 `/var/lib/jbank/deploy.lock`(ec2-user 소유, 재부팅에도 유지되는 경로). 잠금은 **호출하는 쪽**이 잡고 `deploy.sh` 안에서는 잡지 않는다(중첩 교착 방지).
  - CD: SSM 명령(root)이 먼저 `install -d -o ec2-user -g ec2-user /var/lib/jbank`로 잠금 디렉터리를 보장한 뒤(기존 인스턴스에는 없음 — 첫 머지 CD 실패 방지) `runuser -u ec2-user -- flock <잠금> bash -c "git fetch·reset origin/main && IMAGE_TAG=<sha> bash infra/compose/deploy.sh"` — 저장소 갱신과 배포가 한 잠금 안.
  - backend-cd에 `concurrency: {group: backend-cd}`를 두어 CD 두 개가 겹쳐 먼저 시작한 실행이 나중에 `:latest`를 덮는 일을 막는다.
  - 부팅: `flock <잠금> /usr/local/lib/jbank/sync-latest.sh` — `:latest` pull·라벨 비교·저장소 갱신·`deploy.sh`가 한 잠금 안. 그래서 비교 도중 CD가 끼어들 수 없고, 먼저 잡은 쪽이 끝난 뒤 다른 쪽이 최신 상태를 다시 본다(부팅이 X를 보고 있는 동안 CD가 Y를 올렸다면 CD가 잠금을 기다렸다가 Y를 배포, 반대면 부팅이 Y를 보고 같다고 판단).
  - api 준비 대기와 배치는 잠금 밖. 잠금 보유는 이미지 pull + api 재기동(수 분)이라 CD 결과 폴링 한도를 10분으로 늘린다.
  - 부팅 쪽 git fetch·reset 실패 시 현재 저장소·이미지를 유지하고 계속(로그).
  - 수동 배포도 같은 flock 형태로 하도록 문서화.
- 호스트 설치: 설치 스크립트(root)가 `/var/lib/jbank`(ec2-user 소유)를 만들고, 부팅 스크립트·이미지 반영 스크립트·날짜 계산 스크립트를 저장소 밖 `/usr/local/lib/jbank/`에 **원자 교체**(임시 파일 작성 후 mv — 실행 중인 bash는 옛 파일을 계속 읽음)로 설치하고, systemd 단위(`jbank-boot.service`, `User=ec2-user`, oneshot, `After/Requires=docker.service`, `TimeoutStartSec` 60분)를 `daemon-reload` + `enable`만 한다(**기동·재시작하지 않음** — 배포 때 부팅 작업이 다시 돌지 않게). `/etc/cron.d/jbank`를 지운다. CD의 SSM 명령이 배포(잠금 안) 뒤 잠금 밖에서 설치 스크립트를 root로 실행해 항상 최신 스크립트가 깔린다. 새 인스턴스는 user_data가 같은 설치 스크립트를 부른다.
- 적용 순서: ① terraform apply(스케줄러·권한, 머지 전 — 머지 때 도는 새 CD가 상태 조회 권한을 필요로 함) ② PR 머지는 운영 시간(평일 09~18시)에 — 새 CD가 배포와 함께 호스트 설치를 한다. 운영 시간 밖 머지면 다음 부팅 전에 설치 스크립트를 SSM으로 수동 실행한다. ③ 설치 확인. ①~② 사이에는 18:00에 꺼지면 cron 배치가 돌지 않는다(최대 하루, 다음 부팅의 따라잡기가 CTR·FDS를 채움).
- 부팅 작업 순서와 실패 정책:
  1. api 준비 대기(최대 10분, `docker compose ps` healthy). 초과하면 로그를 남기고 배치를 건너뛴 채 실패 종료(단위 failed).
  2. 이미지 반영: `:latest` pull·라벨 조회가 실패하면 현재 이미지를 유지하고 계속. 교체했으면 api 준비를 다시 기다린다(Flyway 마이그레이션 후 배치).
  3. 중단 기록 정리: 부팅 직후라 실행 중인 잡이 있을 수 없으므로 `BATCH_JOB_EXECUTION`에서 STATUS가 STARTING·STARTED·STOPPING인 행을 STATUS='FAILED', EXIT_CODE='FAILED', EXIT_MESSAGE='abandoned at boot', END_TIME=now()로, 그 실행의 `BATCH_STEP_EXECUTION` 중 같은 상태인 행도 같은 값으로 바꾼다(Spring Batch의 실행 중 판정이 상태·종료 시각 어느 쪽이든 풀리게) → 다음 실행에서 같은 파라미터가 재시작된다.
  4. 배치: 잡마다 독립 실행(한 잡 실패가 다른 잡을 막지 않음). interestMaturityJob은 오늘 runDate가 이미 COMPLETED면 건너뛴다. CTR·FDS는 따라잡기 목록을 하루씩 실행하고 실패한 날에서 그 잡만 멈춘다. 결과는 잡·날짜별로 로그에 남는다. Redis 락이 남아 실패한 경우도 FAILED로 남아 다음 부팅에 재시도된다.
- 따라잡기 날짜 계산: python3(zoneinfo, Asia/Seoul 고정, 로컬·호스트 동일 동작). 입력은 잡의 마지막 COMPLETED runDate(없음 가능)와 현재 시각, 출력은 KST 기준 (마지막 완료일+1) ~ 어제. 마지막 완료일이 오늘 이후면 빈 목록. 마지막 완료일 조회: `BATCH_JOB_INSTANCE`(JOB_NAME) ⋈ `BATCH_JOB_EXECUTION`(STATUS='COMPLETED') ⋈ `BATCH_JOB_EXECUTION_PARAMS`(PARAMETER_NAME='runDate')의 PARAMETER_VALUE 최댓값(FAILED 기록은 무시).
- `run-batch.sh`에 임의 기준일 인자(`--run-date YYYY-MM-DD`, 값 생략 시 기존처럼 오늘 KST)를 더하고 cron 관련 주석을 정리한다.
- 프론트 안내: 프록시는 연결 단계 오류(ECONNREFUSED·ETIMEDOUT·EHOSTUNREACH·ENETUNREACH·ENOTFOUND·UND_ERR_CONNECT_TIMEOUT)만 `503 {"code":"SERVER_OFFLINE"}`로 바꾸고 요청 전체 시간 제한은 두지 않는다(느린 정상 응답·이미 처리된 POST를 꺼짐으로 오판하지 않기 위해). 별도 서버 상태 경로가 백엔드 `/actuator/health`를 3초 제한으로 호출해 `{"online":bool}`을 준다(GET·멱등이라 시간 제한 안전). online 판정은 **HTTP 응답을 받았는지**다(caddy 502·health 503도 응답이므로 online — 구성 요소 일부 DOWN을 "꺼짐"으로 안내하지 않는다). 연결 실패·3초 초과만 offline. 화면은 첫 진입 때, 그리고 API가 네트워크 오류·502·503·504로 실패할 때 상태 경로를 확인해 offline이면 안내를 보인다. 안내 문구: "지금은 서버가 꺼져 있습니다. 데모 서버는 평일 09:00~18:00(KST)에만 운영합니다." 시계가 아니라 실제 연결 여부로 판단한다. 09:00 직후 api 준비 전 caddy 502 구간은 online으로 보므로 안내 대신 기존 오류 처리가 보인다(수십 초~수 분, 문서에 한계로 기록).
- 스케줄러 역할 신뢰 정책에 `aws:SourceAccount` 조건을 둔다.
- 기존 CTR 판별의 시간대(`ZoneId.systemDefault()`) 문제는 별도 과제(이미 분리)라 이 작업에서 고치지 않는다.

## 목표

운영 서버 비용을 월 약 $24에서 약 $11(인스턴스 약 $6 + IPv4·디스크 약 $5.4)로 줄이면서, 꺼진 시간에도 배포·배치·화면이 깨지지 않게 한다.

## 범위

- Terraform(modules/ec2, envs/dev): 스케줄러 2개·스케줄러 역할, 배포 역할 권한 추가, user_data 설치 스크립트 호출.
- 호스트 스크립트(infra/compose): 부팅 작업(이미지 반영·배치 따라잡기), 설치 스크립트, systemd 단위, cron 제거.
- backend-cd: 이미지 revision 라벨, 꺼져 있으면 배포 건너뛰기, workflow_dispatch.
- 프론트: 프록시 연결 실패 처리, 서버 상태 확인 경로, 안내 표시.
- 적용: dev apply(머지 전). 기존 인스턴스의 호스트 설치는 머지 CD가 한다(운영 시간 밖 머지면 SSM으로 설치 스크립트 수동 1회).
- 문서: ADR, infra·README 운영 시간, 개발일지.

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 월~금 09:00 KST | 인스턴스가 꺼져 있으면 켜진다. 토·일 09:00에는 켜지지 않는다 |
| REQ-02 | 매일 18:00 KST | 인스턴스가 켜져 있으면 꺼진다(주말 수동 기동 포함) |
| REQ-03 | backend-cd 실행 | 인스턴스가 running이 아니면 이미지만 올리고 배포를 건너뛰며 성공 + "인스턴스 중지 상태라 배포 건너뜀" 경고. running이면 SSM Online을 최대 3분 기다려 배포하고, 배포 실패·상태 조회 실패·3분 내 미등록은 실패로 끝난다. CD와 부팅 작업이 겹쳐도 한쪽이 끝난 뒤 다른 쪽이 실행되고, 최종 이미지는 둘 중 최신 revision이며 교착이 없다 |
| REQ-04 | 인스턴스 부팅 | api 준비 후 `:latest` 이미지의 revision이 현재 IMAGE_TAG와 다르면 저장소를 main으로 맞추고 그 revision으로 api를 재기동한 뒤 다시 준비를 기다린다. 같거나 라벨이 없거나 pull·조회가 실패하면 교체하지 않고 계속한다. api가 10분 안에 준비되지 않으면 배치를 건너뛰고 실패로 남는다. 모든 판단이 호스트 로그에 남는다 |
| REQ-05 | 인스턴스 부팅 | 중단 기록(STARTING·STARTED·STOPPING)을 FAILED로 정리한 뒤 interestMaturityJob(runDate=당일, 오늘 이미 완료면 건너뜀), ctrDetectionJob(따라잡기), ledgerReconciliationJob, fdsDetectionJob(따라잡기) 순으로 실행한다. 한 잡의 실패가 다른 잡 실행을 막지 않는다. 같은 날 두 번째 부팅에서도 실패 없이 끝난다. 새벽 cron은 더 이상 없다 |
| REQ-06 | CTR·FDS 따라잡기 | 기준일은 마지막 COMPLETED runDate 다음 날부터 어제까지 하루씩(기록 없으면 어제). 월요일 부팅이면 금·토·일. 중간 날짜가 실패하면 이후 날짜는 실행하지 않고 다음 부팅에서 그 날짜부터 재시도한다. 이미 완료한 날짜는 다시 실행하지 않는다 |
| REQ-07 | 백엔드 연결 불가 시 프론트 | 프록시는 연결 단계 오류만 503 `SERVER_OFFLINE`으로 응답한다. 서버 상태 경로는 3초 안에 offline을 알려 주고, 첫 화면 진입 또는 API가 네트워크 오류·502·503·504로 실패하면 운영 시간 안내가 보인다. 백엔드가 응답하는 동안(4xx·5xx 포함, 5초 넘는 느린 응답 포함)에는 안내가 나오지 않고 기존 오류 처리가 그대로 동작한다 |
| REQ-08 | 권한 | 스케줄러 역할은 이 인스턴스의 ec2:StartInstances·StopInstances만(신뢰 정책 SourceAccount 조건), 배포 역할에는 ec2:DescribeInstances·ssm:DescribeInstanceInformation(리소스 `*`, 읽기 전용)만 추가된다. 인스턴스 교체·기존 리소스 변경이 plan에 없다 |
| REQ-09 | 문서 | ADR·infra 문서에 운영 시간, 새 인스턴스는 .env를 채우기 전 첫 부팅 작업이 준비 대기 초과로 failed가 되는 점, 비용, 꺼진 시간 동작(배포·배치·화면), 적용·머지 순서, 수동 배포 시 잠금 사용법, 수동 기동·공휴일·Grafana 지표 공백·09시 직후 502 구간·수동 IMAGE_TAG 고정이 다음 부팅에 덮이는 점·18:00과 겹친 CD 실패·전환일 CTR/FDS 한계·부팅 작업 실패 알림 없음(로그만)이 적힌다 |

## 커밋 계획

1. `feat(infra)`: 운영 EC2를 평일 09시에 켜고 매일 18시에 끄는 스케줄러와 배포 역할 상태 조회 권한
2. `feat(infra)`: 배치 실행 스크립트에 임의 기준일 인자 추가
3. `feat(infra)`: 부팅 시 최신 이미지 반영과 배치 따라잡기 호스트 작업(날짜 계산·검사, 이미지 반영·부팅 스크립트, 설치 스크립트·systemd, cron 제거, user_data 연결)
4. `feat(ci)`: 이미지 revision 라벨, 잠금 안에서 배포, 인스턴스 중지 시 배포 건너뛰기·SSM 대기, 호스트 설치 실행, 폴링 10분, 수동 실행 트리거
5. `feat(frontend)`: 백엔드 연결 불가 시 운영 시간 안내
6. `docs`: ADR·infra 문서 운영 시간
7. `docs(devlog)`: 개발일지
8. `chore(harness)`: 작업 기록

적용: terraform apply는 PR 머지 전(기술 선택의 적용 순서). 호스트 설치·CD·부팅 동작은 머지 후 main에서 동작하므로, 구현·verify·PR 뒤 머지를 기다렸다가(wait) 머지 후 검증을 하고 결과 리뷰·complete를 한다.

## 완료 기준

- REQ-01~09가 아래 증거로 확인된다.
- verify 통과.
- 적용 후 실제 18:00 중지·다음 평일 09:00 시작, 부팅 작업 결과, CD 건너뛰기 경로가 확인된다(머지 후 항목은 PR 머지 후 확인하고 기록).

## 하지 않을 일

- 공휴일 인식, 인스턴스 유형·클라우드 변경, 약정 구매.
- CTR 판별 시간대 수정(별도 과제).
- 배치 로직(자바 코드) 변경.

## 적용 영역과 상세 기준

- 프론트: 적용(프록시·안내 표시). FE-01·03·04·05. FE-02(입력 없음)·06(세션 저장 변경 없음)·07(성능 영향 없음) 해당 없음.
- 백엔드·데이터: 앱 코드 변경 없음. 배치 실행 시점·기준일 계산이 바뀌어 데이터 처리 범위에 영향 → BE-01(기준일·시간대 경계) 적용. BE-04: 같은 날짜 재실행 시 CTR 존재 검사·FDS 신규 적재·이자 ACTIVE 필터로 중복이 없고, 동시 실행은 Redis 락과 배포 잠금으로 막혀 새 검사 없음(근거 기록). BE-02·03·05·06·07·08: API·DB 스키마·외부 연동·캐시·권한 코드 변경 없음.
- DevOps: 적용. OPS-01(이미지 revision 식별), OPS-02(건너뛰기 경로가 실제 실패를 성공으로 바꾸지 않음), OPS-03(라벨 없음·같은 sha·pull 실패 처리), OPS-04(부팅 후 준비 대기·상한), OPS-05(배포 건너뛰기·다음 부팅 반영·동시 배포 잠금), OPS-07(부팅 작업·CD 경고 로그). OPS-06(자원 한도·확장 변경 없음)·OPS-08(백업·복원 변경 없음) 해당 없음.
- 보안: SEC-07(스케줄러·배포 역할 최소 권한), SEC-05(비밀값 커밋 없음). SEC-01·02(인증·권한 코드 변경 없음), SEC-03(새 외부 입력 없음 — 상태 경로는 고정 URL만 호출), SEC-04(쿠키·CORS 변경 없음), SEC-06(새 의존성 없음), SEC-08(새 고비용 경로 없음 — 상태 경로는 3초 제한 GET 1회) 해당 없음.
- 성능: 해당 없음(요청 경로에 연결 실패 처리만 추가, 처리량·지연 변화 없음).
- 전체 흐름: E2E-04(일부 서비스 장애 시 성공으로 오표시하지 않음)를 로컬 브라우저 확인으로 적용. Playwright 스위트는 백엔드가 떠 있는 전제라 새 시나리오를 넣지 않는다. E2E-01·02·03·05·06: 사용자 기능 흐름 변경 없음, 기존 흐름은 백엔드가 켜진 동안 그대로.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| SEC-07 | REQ-08 권한 | 스케줄러 역할 = 해당 인스턴스 ec2:StartInstances·StopInstances만(SourceAccount 조건), 배포 역할 추가 = ec2:DescribeInstances·ssm:DescribeInstanceInformation(리소스 `*`, 읽기)만 | `terraform -chdir=infra/terraform/envs/dev plan` 출력 검토 | 커밋 1 후 | review.md(plan 발췌) |
| SEC-05 | 비밀값 | 변경 파일에 비밀값 0건 | `git diff main... \| grep -nE 'AKIA\|aws_secret\|PASSWORD=\|SECRET=\|token=\|eyJhbGci'` 결과 0 | 리뷰 전 | review.md |
| OPS-02 | REQ-03 실패 유지 | 상태 조회 실패·SSM 미등록·배포 실패가 성공으로 바뀌지 않음 | 워크플로 배포 단계 스크립트를 가짜 aws 명령으로 사례별 실행(`bash infra/compose/tests/cd-skip-test.sh`) | 커밋 4 후 | 검사 출력 |
| OPS-01 | REQ-04 식별 | 새 이미지에 revision 라벨, 부팅 로그에 반영 sha | `docker inspect` 라벨, 호스트 로그 | 머지 후 | progress.md |
| OPS-03 | REQ-04 라벨 없음·같은 sha·pull 실패 | 교체하지 않고 계속 | 부팅 스크립트를 가짜 docker·psql·run-batch로 사례별 실행(`bash infra/compose/tests/boot-test.sh`, Linux 컨테이너 `docker run --rm -v $PWD:/w -w /w amazonlinux:2023 bash infra/compose/tests/boot-test.sh`) + 실제 부팅 | 커밋 3 후·머지 후 | 검사 출력, progress.md |
| OPS-04 | REQ-04·05 준비 대기 | api healthy 전 배치 미실행, 10분 초과 시 배치 건너뛰고 실패, 교체 후 재대기 | 같은 모의 검사 + 실제 부팅 로그 시각 | 커밋 3 후·머지 후 | 검사 출력, progress.md |
| OPS-05 | REQ-03·04 | 중지 중 CD 성공+경고. 교체 경로: 현재 태그 ≠ 최신 revision이면 다음 부팅에 최신으로 교체. 같은 태그면 교체 안 함. 동시 배포: 부팅 비교 도중 CD가 끼어드는 사례에서 최종 태그가 더 새로운 sha이고 교착 없음 | 모의: `boot-test.sh`(실제 flock으로 두 프로세스 경합). 실제: ① 머지 CD 배포(최신 X) ② 18시 전 `flock … IMAGE_TAG=fa97aef8147f2228d2a95086092bdce2d05fa7ba deploy.sh`(전체 40자 sha)로 이전 이미지로 되돌림 ③ 18시 중지 후 `gh workflow run backend-cd --ref main` → 성공+경고 ④ 다음 09시 부팅 로그에서 fa97aef → X 교체 ⑤ 같은 날 수동 재부팅에서 교체 없음 | 커밋 4 후·머지 후 | 검사 출력, progress.md |
| OPS-07 | 로그 | 부팅 작업 단계별 로그(`journalctl -u jbank-boot`, `/var/log/jbank/boot.log`), CD 경고 | 위 실행 | 적용·머지 후 | progress.md |
| BE-01(실제 DB) | REQ-05·06 중단 기록 정리·마지막 완료일 조회 | FDS 실행 기록이 없고 현재 FDS 마지막 COMPLETED runDate보다 이른 날짜(예: 서비스 시작 전 2026-01-01 — 마지막 완료일을 앞당기지 않아 따라잡기에 영향 없음, 그날 거래가 없으면 적재 0건)로 fdsDetectionJob 1회 실행 → 그 실행과 스텝을 STARTED·END_TIME NULL로 조작 → 정리 SQL → 같은 파라미터 재실행 성공. COMPLETED·FAILED가 섞인 기록에서 마지막 완료일 조회가 COMPLETED 최댓값 | 머지 후 인스턴스 PostgreSQL에서 SSM으로 SQL·run-batch 실행(운영 데이터지만 데모이고 FDS 재실행은 중복을 만들지 않음) | 머지 후 | progress.md |
| BE-01 | REQ-05·06 기준일·재실행 | 날짜 목록: 기록 없음→어제, 목요일 완료+월요일 부팅→금·토·일, 어제 완료→없음, 오늘·미래 완료(cron 시절 당일 기록)→없음, 월·연 경계, UTC 00:30(=KST 09:30) 기준 KST 날짜 사용. 실행: 중간 날짜 실패 시 그 잡의 이후 날짜 미실행·다른 잡 계속, 오늘 이미 완료한 이자 잡 건너뜀, 중단 기록 FAILED 정리 | `python3 infra/compose/batch_dates.py --self-test`, `bash infra/compose/tests/boot-test.sh`(위 컨테이너 포함) | 커밋 3 후 | 검사 출력 |
| FE-01 | 빌드·타입 | lint·tsc·test·build 통과 | verify | 구현 후 | evidence |
| FE-03 | REQ-07 실패 | 연결 단계 오류 → 503 SERVER_OFFLINE, 연결 후 5초 넘게 걸린 응답(POST 포함)과 백엔드 4xx·5xx는 그대로 전달, 상태 경로 3초 제한·offline 판정, 화면은 네트워크 오류·502·503·504에서만 상태 확인 | jest(`npm --prefix apps/frontend test`) | 커밋 5 후 | 테스트 출력 |
| FE-04 | 안내 화면 | 모바일 폭(375px)에서 안내가 잘리지 않고 읽힘 | 로컬 dev 서버(BACKEND_API_URL=도달 불가 주소) 스크린샷 | 커밋 5 후 | progress.md(스크린샷 경로) |
| FE-05 | 접근성 | 안내가 `role="alert"`로 읽히고 글자·배경 대비 4.5:1 이상 | jest 렌더 검사 + 브라우저에서 계산 색으로 대비 비율 계산(스크립트) | 커밋 5 후 | 테스트 출력, progress.md |
| E2E-04 | 부분 장애 | 백엔드 없이 로그인 시도 시 성공으로 표시되지 않고 안내 표시 | 로컬 브라우저 확인 | 커밋 5 후 | progress.md(스크린샷 경로) |
| 정적 검사 | 셸·Terraform | 문법·형식 통과 | `bash -n` 변경 셸 전체, `terraform -chdir=infra/terraform/envs/dev fmt -check -recursive ../..`·`validate` | 커밋별 | 검사 출력 |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| REQ-01·02 | apply 후 스케줄 2개 존재(표현식·시간대 확인). 같은 역할로 일회성 `at()` 스케줄을 만들어 몇 분 뒤 중지·시작이 실제로 되는지 미리 확인 후 삭제. 이후 실제 18:00 StopInstances와 다음 평일 09:00 StartInstances가 스케줄러 역할로 CloudTrail에 기록 | `aws scheduler get-schedule`, `aws scheduler create-schedule --schedule-expression 'at(...)'`, CloudTrail `lookup-events` | 적용 후·다음 날 |
| REQ-03 | 모의: 상태별(stopped→건너뜀 성공, running+Online→배포, running+미등록 3분→실패, 조회 실패→실패, 배포 실패→실패), SSM 명령 문자열에서 잠금 디렉터리 생성이 flock보다 앞섬. 실제: 머지 CD(running)로 배포, 18시 이후 수동 실행 → 성공+경고 | `bash infra/compose/tests/cd-skip-test.sh`, `gh workflow run backend-cd --ref main`, `gh run view` | 커밋 4 후·머지 후 |
| REQ-04·05 | 모의 검사(위). 실제: 설치 후 수동 중지→시작을 같은 날 두 번 → 두 번 모두 단위 성공, 로그에 이미지 판단·배치 순서·시각, 두 번째는 이자 잡 건너뜀, cron 파일 없음 | SSM으로 `/var/log/jbank/boot.log`·`systemctl status jbank-boot`·`ls /etc/cron.d` | 머지 후 |
| REQ-06 | 날짜 계산 사례 검사 + 실제 부팅에서 CTR·FDS 기준일 목록(마지막 완료일 이후) + 실제 DB 정리·조회 사례 | `batch_dates.py --self-test`, 배치 로그·BATCH_JOB_EXECUTION_PARAMS 조회 | 커밋 3 후·머지 후 |
| REQ-07 | jest 단위 + 로컬 브라우저 | `npm --prefix apps/frontend test`, 스크린샷 | 커밋 5 후 |
| REQ-08 | plan에 인스턴스 교체 없음, 권한 문서 검토 | `terraform plan` | 커밋 1 후 |
| REQ-09 | 문서 내용 | 리뷰 | 커밋 6 후 |
| 회귀 | 기존 검사 통과 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

새 셸·파이썬 검사(`batch_dates.py --self-test`, `tests/boot-test.sh`, `tests/cd-skip-test.sh`)는 인프라 호스트 스크립트 검사라 verify(`.claude/checks.json`, 앱 검사)에 넣지 않고, 작업 중 직접 실행해 출력을 progress.md에 남긴다.

## 성능 테스트

- 필요 여부와 이유: 불필요. 요청 경로 변화는 프록시의 연결 실패 처리뿐이고 처리량·지연·자원 사용이 바뀌지 않는다.

## 추가 맥락

- 관련: docs/adr/0010-ec2-single-instance.md, infra/terraform/modules/ec2/, infra/compose/, .github/workflows/backend-cd.yml, apps/frontend/app/api/proxy.
- 머지 후 검증 항목이 있어 PR 머지 전까지는 wait로 멈출 수 있다.
