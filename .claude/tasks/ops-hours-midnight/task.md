# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 서버 운영시간을 아침 9시부터 밤12시로 조정하자. 개발에 너무 딜레이가 심하다
요청 해석: 운영 EC2(jbank-dev-app)의 자동 운영 시간을 평일 09:00~18:00(KST)에서 평일 09:00~24:00(KST)으로 늘린다. 저녁에도 운영 서버에서 배포 확인·실험을 할 수 있게 하려는 목적이다. 시간을 언급하는 화면 안내·주석·문서도 함께 맞춘다.
조사한 사실:
- 스케줄: `infra/terraform/modules/ec2/schedule.tf`. 시작 `jbank-dev-app-start` `cron(0 9 ? * MON-FRI *)`, 중지 `jbank-dev-app-stop` `cron(0 18 * * ? *)`, 둘 다 Asia/Seoul. 중지는 매일이라 주말에 손으로 켠 경우도 그날 꺼진다. 결정 기록은 `docs/adr/0011-ec2-weekday-hours.md`(승인됨, 비용 월 약 $24 → 약 $11).
- 실제 운영 스케줄 조회(2026-11-23): `jbank-dev-app-stop` = `cron(0 18 * * ? *)`, Asia/Seoul, ENABLED. 코드와 같다.
- 24:00 중지는 cron에서 다음 날 00:00(`cron(0 0 * * ? *)`)이다. 금요일 밤 운영분은 토요일 00:00에 꺼진다. 주말에 손으로 켠 경우는 그날 밤 00:00에 꺼진다. 월요일 00:00 중지는 일요일에 손으로 켠 경우에만 의미가 있다.
- 비용(ADR 0010·0011 수치 기준, 상시 가동 인스턴스 월 약 $19 = 시간당 약 $0.026): 평일 9시간(월 약 195시간) 약 $5 → 평일 15시간(월 약 326시간) 약 $8.5. IPv4·디스크 약 $5.4는 그대로. 합계 월 약 $11 → 약 $14.
- 18시·09~18시를 말하는 곳(교착 실험 runbook 제외):
  - 화면 문구: `apps/frontend/components/server-offline-notice.tsx`(`OFFLINE_MESSAGE` "평일 09:00~18:00(KST)"). 테스트 `server-offline-notice.test.tsx`는 상수를 그대로 비교해 문구 값 자체는 검사하지 않는다.
  - 주석: `apps/frontend/lib/server-status.ts:4`, `apps/frontend/app/api/proxy/[...path]/route.ts:28`, `.github/workflows/backend-cd.yml:61`, `.github/scripts/deploy-ec2.sh:3`, `infra/compose/docker-compose.prod.yml:55`(병합은 18시 이후), `schedule.tf:1-2`.
  - 문서: `README.md:11`(안내), `README.md:106·112`(스케줄·비용), `docs/06_J-Bank_인프라아키텍처.md:63`, `docs/adr/0014-observability-logs-alerts.md:53·56`, `docs/runbook/observability.md:10·76·85`, `docs/assets/diagrams/weekday-ops.svg`("매일 18:00 중지"), `docs/assets/diagrams/architecture.svg`("평일 9~18시").
  - `docs/03_J-Bank_API설계.md`의 18은 API 번호·예시 시각이라 운영 시간과 무관하다. `docs/devlog/`, `.claude/tasks/`는 과거 기록이라 고치지 않는다.
- 부팅 작업(배치 따라잡기)은 "어제까지"를 KST 날짜로 계산하므로 중지 시각이 바뀌어도 동작이 같다. 꺼진 시간 CD 건너뛰기(`deploy-ec2.sh`)·프론트 꺼짐 판정(`/api/server-status`)은 시계가 아니라 실제 상태로 판단해 로직 변경이 필요 없다.
- "PR은 서버가 꺼진 18시 이후 병합"(ADR 0014, observability runbook)은 postgres 재생성을 사용자 없는 시각(다음 부팅)으로 미루려는 규칙이다. 바뀐 뒤 꺼진 시간은 평일 00:00~09:00과 주말이다.
- 교착 실험(다른 작업, 메인 폴더) runbook 4-4는 그날만 연장하려고 `jbank-dev-app-stop` 정의를 저장했다가 State만 DISABLED로 바꾸고, 끝나면 저장본으로 되돌린다. `perf/run-deadlock.sh`는 이 스케줄의 State만 읽는다. 따라서 ① 실험 중(DISABLED)에 이 작업의 terraform apply를 하면 스케줄이 ENABLED로 바뀌고, ② apply 전에 저장한 정의로 실험 후 되돌리면 표현식이 18시로 돌아간다. 이 작업은 그 runbook을 고치지 않고, apply 순서로 피한다(Q-02).
- 병합하면 backend-cd가 돈다(`backend-cd.yml:6-10` 경로 필터에 `infra/compose/**`, `backend-cd.yml`, `deploy-ec2.sh` 포함 — 커밋 5가 이 세 파일 주석을 고침). 인스턴스가 켜져 있으면 새 sha 이미지로 `deploy.sh`가 `up -d api`를 실행해 api가 수십 초 재시작된다. postgres는 정의가 바뀌지 않아 재생성되지 않는다. 꺼져 있으면 배포를 건너뛰고 다음 부팅 때 반영된다.
- infra-cd 자동 apply 게이트: `gh variable list`(2026-11-23)에 `TERRAFORM_CI_READY` 없음(`EC2_DEPLOY_READY=true`만). 병합해도 terraform apply는 자동으로 돌지 않는다.
- 병합하면 Vercel이 프론트 문구를 바로 배포한다. apply가 늦어지면 그 사이 18~24시에 서버는 꺼졌는데 안내는 "09:00~24:00"으로 보인다.
- ADR 0011 결과의 "18:00 중지와 겹친 배포는 실패"(0011:47)와 ADR 0014의 "18시 이후 병합"(0014:53)·"18:00 중지 시 알림 해제 메일 없음"(0014:56)은 중지 시각만 00:00으로 바뀌어 그대로 성립한다. 비용 기준: ADR 0011은 평일 9시간 인스턴스 비용을 약 $6로 적었다(반올림 차이). 이 작업은 시간당 $0.026 × 시간으로 다시 계산한다.
- 이 폴더에는 `apps/frontend/node_modules`와 terraform 초기화(`.terraform`, `terraform.tfvars`)가 없다. verify 전에 `npm --prefix apps/frontend ci`, plan 전에 메인 체크아웃의 `terraform.tfvars`를 복사(커밋 안 함, gitignore 대상)하고 `terraform init`이 필요하다. Docker CLI가 PATH에 없어 verify의 백엔드 테스트(Testcontainers) 전에 Docker Desktop 실행이 필요하다.
- 로컬 AWS 자격증명(jbank-admin)으로 terraform plan·apply와 aws CLI 조회가 가능하다.
질문하지 않은 이유: 병합 시각 규칙 문구, 주석 갱신은 사실을 따르는 것이라 묻지 않는다. 결정이 필요한 항목은 아래 표로 물어 결정했다.

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | 결정 기록 방식: ADR 0011 갱신 vs 새 ADR 0015 | 새 ADR 0015. 0011은 내용을 두고 상태에 "운영 시간은 ADR 0015로 변경" 한 줄만 추가(2026-11-23) | 해결 |
| Q-02 | terraform apply 시점·실행자, 교착 실험과의 순서 | PR 머지 직후 메인(에이전트)이 실행. plan 결과를 사용자에게 보이고 승인받은 뒤 apply. 교착 실험이 진행 중이 아닐 때만(중지 스케줄 State ENABLED·표현식 18시 확인), 실험 날이면 실험이 끝난 뒤로 미룸(2026-11-23) | 해결 |
| Q-03 | 운영 반영 확인 범위 | 적용한 날 18시 이후에도 running이고, 다음 00:00 직후 스케줄러 역할의 StopInstances가 CloudTrail에 있는지까지 확인 후 완료. 그때까지 wait(2026-11-23). 평일 18시 이후·주말에 apply하면 다음 평일로 확인(REQ-02) | 해결 |
| Q-04 | 요일: 주말 포함 매일 09~24시 vs 평일만 | 평일만. 평일 09시 시작은 그대로, 중지는 매일 24시(00:00). 사용자가 메인 세션 대화에서 결정하고, 이 세션을 시작한 메인 세션이 작업 지시에 "사용자 결정: 요일은 지금처럼 평일만(평일 09시 시작, 매일 24시 정지)"으로 전달(2026-11-23) | 해결 |
| Q-05 | 병합 시 backend-cd 재배포(api 수십 초 재시작) 처리 | CD 대상 파일 주석도 고치고, 교착 실험이 돌고 있지 않을 때 병합. api 재시작은 감수. PR 본문에 병합 조건을 적음(2026-11-23) | 해결 |

## 목표

운영 서버가 평일 09:00~24:00(KST)에 켜져 있게 해 저녁 개발·확인 지연을 없앤다. 비용 증가(월 약 $11 → 약 $14)와 이유를 ADR로 남긴다.

## 범위

- Terraform: `modules/ec2/schedule.tf` 중지 표현식과 주석.
- 프론트: 꺼짐 안내 문구, 관련 주석, 문구 검사 테스트.
- 주석: backend-cd, deploy-ec2.sh, docker-compose.prod.yml.
- 문서: ADR 0015(신규), ADR 0011·0014 상태 한 줄(본문은 그대로), observability runbook, 인프라 문서, README(안내·스케줄·비용·ADR 목록), 다이어그램 2개, 개발일지.
- 적용: 교착 실험이 없을 때 사용자가 병합 → 메인이 plan 공유·승인 → apply → 첫 평일 18시 이후 running·다음 00:00 중지 확인.
- 교착 실험 작업에 알림(공유 노트 폴더 노트 + 메인 세션 메시지).

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 월~금 09:00 KST | 시작 스케줄은 바뀌지 않는다(`cron(0 9 ? * MON-FRI *)`, Asia/Seoul, ENABLED). plan에 시작 스케줄 변경이 있거나 apply 후 조회 값이 다르면 실패 |
| REQ-02 | 매일 00:00 KST(전날 24:00) | 인스턴스가 켜져 있으면 꺼진다. 18:00에는 꺼지지 않는다. 검증일은 apply 후 처음 오는 평일(apply가 평일 18시 전이면 그날): 그날 18:05 이후 running이 아니거나, 그 뒤 00:00~00:05 KST에 스케줄러 역할의 StopInstances가 없으면 실패. 주말 수동 기동분 중지는 실제 실행 대신 표현식이 매일(`* * ? *`)인 것으로 확인 |
| REQ-03 | terraform plan·apply | 변경은 `aws_scheduler_schedule.stop`의 `schedule_expression` 제자리 수정 1건뿐(0 add·1 change·0 destroy). 인스턴스 교체·IAM 변경·시작 스케줄 변경·State 변경·이 작업과 무관한 차이가 plan에 있으면 apply하지 않고 사용자에게 묻는다(승인 없이 `-target` 쓰지 않음). apply 후 실제 조회 값이 `cron(0 0 * * ? *)`·Asia/Seoul·ENABLED |
| REQ-04 | apply 순서 | 병합 후 plan을 사용자에게 보이고, 오늘 교착 실험 진행 여부와 함께 승인받는다. 실험 날이면 실험이 끝난 뒤로 미룬다. 승인 뒤 메인이 ① 중지 스케줄을 다시 조회해 ENABLED이고 표현식이 `cron(0 18 * * ? *)`인지 확인(교착 실험 연장 중 아님) ② `terraform plan -out=<scratchpad 경로>`로 새 plan 파일을 만들어 승인받은 plan과 같은지 확인(0 add·1 change·0 destroy, stop `schedule_expression`만, State 변경 없음) ③ 곧바로 `terraform apply <그 파일>` ④ 다시 조회. 승인 전에 만든 plan 파일은 쓰지 않는다. plan 파일은 tfvars 값을 담을 수 있어 저장소 밖(scratchpad)에 두고 apply 후 지운다. ①과 ③ 사이 수 초의 틈은 남는다(한계로 기록). DISABLED거나 변경이 다르면 apply하지 않고 대기 |
| REQ-05 | 서버가 꺼져 있을 때 프론트 안내 | 안내 문구가 "평일 09:00~24:00(KST)"를 말한다. 18:00 문구가 남으면 실패. 안내의 `role="alert"`·표시 조건은 그대로 |
| REQ-06 | 문서·주석 | ADR 0015에 결정(평일 09:00 시작, 매일 00:00 중지), 이유(개발 지연), 비용 계산($0.026 × 195h ≈ $5 → × 326h ≈ $8.5, 합계 약 $11 → 약 $14, ADR 0011의 $6는 반올림 차이), 주말 수동 기동은 그날 밤 00:00 중지, 공휴일 한계 유지, 00:00 중지와 겹친 배포는 실패(다음 부팅이 반영), 병합 시각 규칙 변경(꺼진 시간 = 평일 00~09시·주말), 병합 때 backend-cd 재배포 조건, 교착 실험 연장 절차와 apply 순서 주의, 병합~apply 사이 문구 불일치, 되돌리기(revert 병합 후 apply)가 적힌다. ADR 0011·0014 상태에 0015 안내가 있다. README 비용이 $24 → $14(평일 09~24시)로, ADR 목록에 0015가 있다. 범위 파일에 운영 시간으로서의 18시 표현이 남지 않는다 |

## 커밋 계획

1. `docs`: ADR 0015 운영 시간 평일 09~24시 (ADR 0015 신규, 0011·0014 상태 한 줄)
2. `feat(infra)`: 운영 EC2 자동 중지 시각을 매일 24시로 변경 (`schedule.tf`)
3. `feat(frontend)`: 서버 꺼짐 안내의 운영 시간을 평일 09:00~24:00으로 변경 (안내 문구, server-status·proxy 주석)
4. `test(frontend)`: 꺼짐 안내 문구의 운영 시간 검사
5. `chore`: CI·compose 주석의 운영 시간 갱신 (backend-cd.yml, deploy-ec2.sh, docker-compose.prod.yml)
6. `docs`: 운영 시간 문서·다이어그램 갱신 (observability runbook, 인프라 문서, README, svg 2개)
7. `docs(devlog)`: 개발일지
8. `chore(harness)`: 작업 기록

적용 흐름: 구현 → verify → 결과 리뷰 → PR(본문에 "교착 실험 중이 아닐 때 병합, 병합 시 api 수십 초 재시작") → 사용자 병합 → backend-cd 실행 결과 확인(성공 또는 꺼짐으로 건너뜀, 켜져 있었으면 `/readyz` 200) → plan 공유·실험 여부 확인·승인 → 재조회 → plan 파일 생성·대조 → apply → 재조회 → 첫 평일 18시 이후 running·00:00 중지 확인 → 교착 실험 작업에 알림 → complete. 병합·승인·00시 확인 전에는 wait.

## 완료 기준

- REQ-01~06이 아래 증거로 확인된다.
- verify 통과.
- terraform fmt·validate 통과, plan이 REQ-03 조건 충족.
- 병합 후 apply, 실제 조회 값과 첫 평일 18시 이후 running·00:00 StopInstances(CloudTrail) 확인 기록.
- 교착 실험 작업에 runbook 4-4·141·162행의 18시 표현과 "스케줄 저장본은 이 작업 apply 이후 것이어야 함"을 알렸다: 공유 노트 폴더(`/Users/imjaehyeon/Library/Application Support/Claude/side-session-notes/local_6b437a44-c0e8-4b76-a485-d504aeddf35b/ops-hours-midnight.md`)에 노트, 메인 세션에 보고. progress.md에 기록.

## 하지 않을 일

- 교착 실험 runbook(`docs/runbook/transfer-deadlock-experiment.md`)·`perf/run-deadlock.sh` 수정(다른 작업 소관). 알리기만 한다.
- 공휴일 인식, 인스턴스 유형 변경, 시작 시각·요일 변경.
- 꺼짐 판정·CD 건너뛰기·부팅 작업 로직 변경(시각과 무관하게 동작).
- 과거 기록(devlog, `.claude/tasks/`)과 ADR 0011·0014 본문 수정(상태 한 줄만 추가).

## 적용 영역과 상세 기준

- 프론트: 적용(안내 문구). FE-01 적용(verify). FE-05 적용(기존 alert 테스트 유지 확인). FE-03 해당 없음(API 실패 처리·표시 조건 로직 변경 없음). FE-04 해당 없음("18:00"→"24:00" 글자 수 같아 배치 변화 없음). FE-02(입력 없음)·FE-06(세션 변경 없음)·FE-07(성능 영향 없음) 해당 없음.
- 백엔드·데이터: 해당 없음. 앱 코드·DB 변경 없음. 배치 기준일은 KST 날짜 기반이라 중지 시각과 무관(BE-01 영향 없음, 조사한 사실 참고).
- AI: 해당 없음(제품에 AI 기능 없음).
- DevOps: 적용. OPS-03(설정 값 — 잘못된 표현식·시간대·State를 plan과 실제 조회로 확인). OPS-05(되돌리기: revert 병합 후 apply를 ADR에 기록. 정방향 apply와 같은 제자리 수정 경로라 실제 되돌리기 실행은 REQ-03 확인으로 대신). OPS-07(관측: CloudTrail의 StopInstances 기록으로 실제 동작 확인). OPS-01(빌드 변경 없음)·OPS-02(CI 변경은 주석뿐, 실패 판정 로직 그대로)·OPS-04(기동 로직 변경 없음)·OPS-06(자원 변경 없음)·OPS-08(백업 변경 없음) 해당 없음.
- 보안: SEC-07 적용(권한 변경 없음을 plan으로 확인). SEC-05 적용(비밀값 커밋 없음, tfvars 미커밋). SEC-01·02·03·04·06·08 해당 없음(인증·권한 코드·외부 입력·브라우저 경계·의존성·고비용 경로 변경 없음).
- 성능: 해당 없음(요청 경로 변화 없음).
- 전체 흐름: 해당 없음. 화면 동작은 문구만 바뀌고 E2E-04(꺼짐 시 오표시 없음) 경로는 기존 그대로, E2E 테스트에 이 문구를 쓰는 곳 없음. E2E-01·02·03·05·06 사용자 기능 변경 없음.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| FE-01 | 빌드·타입·테스트 | lint·tsc·jest·build 통과 | `python3 .claude/hooks/workflow.py verify` | 구현 후 | evidence/verify-NNN |
| FE-05 | REQ-05 안내 접근성 유지 | 기존 테스트의 `findByRole("alert")` 통과, 새 테스트가 문구에 "평일 09:00~24:00(KST)" 포함·"18:00" 미포함 확인 | `npm --prefix apps/frontend test -- server-offline-notice` (verify에 포함) | 커밋 4 후 | 테스트 출력, evidence |
| OPS-03 | REQ-01·03 plan | 0 add·1 change·0 destroy, 변경은 stop `schedule_expression` `cron(0 18 * * ? *)` → `cron(0 0 * * ? *)`만 | `terraform -chdir=infra/terraform/envs/dev fmt -check -recursive ../..`, `validate`, `plan -no-color` | 커밋 2 후·병합 후 | review.md(plan 발췌), progress.md |
| OPS-03 | REQ-01·03·04 적용 | apply 직전 중지 State ENABLED·18시 표현식, apply 후 중지 `cron(0 0 * * ? *)`·Asia/Seoul·ENABLED, 시작 `cron(0 9 ? * MON-FRI *)`·Asia/Seoul·ENABLED | `aws scheduler get-schedule --name jbank-dev-app-stop`(전후), `--name jbank-dev-app-start`(후), `terraform plan -out=<scratchpad>/stop.tfplan` → `terraform apply <scratchpad>/stop.tfplan` | 병합 후 | progress.md |
| OPS-07 | REQ-02 실제 동작 | 첫 검증 평일 18:05 이후 `describe-instances` 상태 running, 00:00~00:05 KST에 스케줄러 역할 세션의 StopInstances 1건, 그날 18:00 전후 스케줄러 StopInstances 없음 | `aws ec2 describe-instances`, `aws cloudtrail lookup-events --lookup-attributes AttributeKey=EventName,AttributeValue=StopInstances` | 검증일 18시 이후·다음 00시 이후 | progress.md |
| OPS-05 | 병합 재배포(Q-05) | 병합으로 돈 backend-cd가 성공(배포 또는 꺼짐 건너뜀). 배포했으면 `https://api.j-bank.site/readyz` 200(프론트 서버 상태 경로가 쓰는 주소) | `gh run list --workflow backend-cd.yml -L 1`, `gh run view`, `curl -s -o /dev/null -w '%{http_code}'` | 병합 직후 | progress.md |
| OPS-05 | REQ-06 되돌리기 | ADR 0015에 revert 병합 후 apply 절차 | 문서 검토 | 커밋 1 후 | review.md |
| SEC-07 | 권한 변경 없음 | plan에 `aws_iam_*` 변경 0건 | 위 plan 출력 | 커밋 2 후 | review.md |
| SEC-05 | 비밀값 | 변경 파일에 비밀값 0건, `terraform.tfvars` 미추적 | `git diff origin/main... \| grep -nE 'AKIA\|aws_secret\|PASSWORD=\|SECRET=\|token=\|eyJhbGci'` 0건, `git ls-files \| grep tfvars$` 0건(실행할 때 `\|`는 `|`) | 리뷰 전 | review.md |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| REQ-01 | plan에 start 스케줄 변경 없음, apply 후 start 조회 값 그대로 | `terraform plan`, `aws scheduler get-schedule --name jbank-dev-app-start` | 커밋 2 후·병합 후 |
| REQ-02 | 첫 검증 평일 18시 이후 running, 다음 00:00 스케줄러 StopInstances, 표현식 매일 | `aws ec2 describe-instances`, CloudTrail `lookup-events`, `get-schedule` | 병합·apply 후 |
| REQ-03 | plan 1 change만, apply 후 조회 값 | `terraform plan`, `aws scheduler get-schedule` | 커밋 2 후·병합 후 |
| REQ-04 | 승인 시각·실험 여부 답, apply 직전 State·표현식 재조회, 새 plan 파일 내용이 승인 plan과 일치, plan 파일 삭제 | `aws scheduler get-schedule`, `terraform show <파일>`, apply 출력, progress.md 기록 | 병합 후 |
| REQ-05 | 문구에 24:00 포함·18:00 없음, alert 유지 | jest(`server-offline-notice.test.tsx`) | 커밋 4 후 |
| REQ-06 | 남은 18시 표현 0건(제외 대상 빼고) + ADR 0015·0011·0014 상태·README 비용·ADR 목록 내용 검토 | `git grep -nE '18:00\|18시\|09~18\|9~18' -- . ':!docs/devlog' ':!.claude/tasks' ':!docs/runbook/transfer-deadlock-experiment.md' ':!docs/adr/0011-ec2-weekday-hours.md' ':!docs/adr/0014-observability-logs-alerts.md' ':!docs/adr/0015-*' ':!docs/03_J-Bank_API설계.md' ':!perf' ':!apps/frontend/components/server-offline-notice.test.tsx'` 결과 0건. 표 안의 `\|`는 실행할 때 `|`로 쓴다. 변경 전(커밋 1 전)에 같은 명령을 한 번 실행해 현재 줄들(deploy-ec2.sh:3, backend-cd.yml:61, README:11·106, route.ts:28, server-offline-notice.tsx:7, server-status.ts:4, docs/06:63, svg 2개, observability runbook 10·76·85, compose:55, schedule.tf:1-2)이 잡히는 대조 결과를 progress.md에 남긴다(ADR 0011·0014는 본문 유지, 0015는 이전 값을 설명해야 해서, 테스트 파일은 "18:00 미포함" 검사 문자열이라 제외 — 셋 다 내용 검토로 대신) | 커밋 6 후 |
| 회귀 | 기존 검사 통과 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

## 성능 테스트

- 필요 여부와 이유: 불필요. 요청 경로·처리량·자원 사용 변화 없음(켜져 있는 시간만 늘어남).

## 추가 맥락

- 관련: docs/adr/0011-ec2-weekday-hours.md, .claude/tasks/ec2-weekday-schedule/task.md(원래 스케줄 작업).
