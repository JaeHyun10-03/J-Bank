# 검토 결과

## 검토 범위

결과 리뷰 2회차(병합·apply·운영 확인 뒤). 시작 커밋 65d15e0부터 a429f29까지(구현·문서 마지막 커밋은 4d4003f, a429f29는 작업 기록). 대상 snapshot: 059786e39370ad632a81e8b2ab57e7867384139f56ac0fafeeb00b3b8a781317 (verify-003과 같은 값, 작업 폴더는 snapshot에서 제외됨). 최신 검사는 verify-004.

1회차 응답 원문과 처리는 evidence/review-1.md에 보존되어 있다.

## 독립 검증 결과

verifier 2회차(2026-11-26) 응답 원문:

> ## 결과 리뷰 2회차(재리뷰 1/2): ops-hours-midnight
>
> **결론: 통과 권고입니다.** 차단·중요 지적은 없습니다. 1회차의 중요 지적 3건은 모두 해소됐고, 남은 것은 개선 7건입니다.
>
> 이 판정에는 전제가 하나 있습니다. 지적 1의 "메인 세션 보고"가 이미 전달됐어야 합니다. 아직이면 완료 기준 5가 미충족이므로, complete 전에 보고하고 기록해야 합니다. 작업 폴더는 snapshot에서 제외되므로 이 기록 때문에 재검사가 필요하지는 않습니다.
>
> ### 검증 대상과 읽은 범위
> - snapshot은 `059786e39370ad632a81e8b2ab57e7867384139f56ac0fafeeb00b3b8a781317`입니다. evidence/checks.json, verify-004/checks.json, verify-003/checks.json, state.json의 review_pending, 2회차 verifier_calls가 모두 같습니다.
> - checks.json의 HEAD는 a429f29, dirty false, unchanged_during_checks true, skipped_checks 빈 값입니다. 계획 지문 `acacc9a6…`은 state.json과 plan-review 3회차가 같습니다.
> - 읽은 자료:
>   - docs/testing-policy.md, docs/testing/devops.md·frontend.md·security.md
>   - task.md, progress.md, plan-review.md, state.json, review.md 초안, evidence/review-1.md, review-1.json, review.json
>   - verify-004 로그: 1번은 결과 줄, 2·4·5·6번은 전문, 그리고 changes.txt
>   - verify-001/check-5.log, `.claude/checks.json`
>   - 원문: schedule.tf, server-offline-notice.tsx·test.tsx, ADR 0011 전문, ADR 0015 전문, observability runbook 전문, 개발일지 전문
>   - 부분: ADR 0014 머리말, README 100~117행, docker-compose.prod.yml 45~66행
>   - 검색으로만 확인: docs/06, svg 2개, server-status.ts, route.ts, backend-cd.yml, deploy-ec2.sh, docs/devlog/README.md
>   - 알림 노트
> - 적용 기준은 FE-01, FE-05, OPS-03, OPS-05, OPS-07, SEC-05, SEC-07입니다.
> - 명령·테스트·AWS·GitHub 조회는 직접 실행하지 않았습니다.
>
> ### 지적
>
> **1. [개선, complete 전 처리] 완료 기준 5의 "메인 세션에 보고"가 전달됐는지 불분명하고, review.md에 이 기준의 근거가 없습니다**
> - 근거: task.md:80은 공유 노트, 메인 세션 보고, progress.md 기록을 모두 요구합니다. 노트는 실제로 있고 필요한 내용(4-4, 141행, 162행, 저장본은 apply 이후 것)이 다 있습니다. 그런데 progress.md:24는 "마무리 보고로 전달"이라고만 적어, 이미 전달한 것인지 작업 끝에 전달할 예정인지 알 수 없습니다. review.md에서 "알림", "메인 세션", "노트"를 검색하면 0건입니다.
> - 영향: 작습니다. 핵심 내용은 노트로 이미 남아 있습니다. 다만 아직 보고하지 않았다면 합의한 완료 기준이 미충족입니다.
> - 필요한 수정: 전달했으면 시각을 progress.md에 적습니다. 아니면 complete 전에 보고하고 적습니다. review.md "완료 기준별 근거"에 이 기준의 줄을 넣습니다.
>
> **2. [개선] 승인 시각이 분 단위로만 남아 있습니다** (REQ-04)
> - 근거: task.md:118은 "승인 시각·실험 여부 답"을 근거로 정했습니다. progress.md:21에는 사용자 답 두 개가 있지만 시각은 "13:50"뿐입니다. plan 실행 13:50:15와 직전 재조회 13:50:52 사이는 37초이고, 그 안에 plan 실행·공유·답변 두 개가 들어갑니다. 순서는 모순되지 않지만 촘촘해서 파일만으로는 뒷받침되지 않습니다.
> - 영향: 승인 절차를 나중에 추적하기 어렵습니다. 실제 변경 내용에는 영향이 없습니다.
> - 필요한 수정: 세션 기록에서 답변 시각을 찾아 progress.md에 적습니다.
>
> **3. [개선] apply 출력과 새 plan 파일 대조가 요약으로만 남아 있습니다** (REQ-04, OPS-03)
> - 근거: task.md:118은 `terraform show <파일>` 대조와 apply 출력을 요구하고 기록 위치를 progress.md로 정했습니다. progress.md:21은 요약이고, review.md:92는 전문이 없다고 스스로 밝힙니다.
> - 중요로 보지 않은 이유: 같은 결과를 보여 주는 다른 값이 있습니다.
>   - 병합 뒤 plan 원문(review.md:73-90)
>   - 중지 스케줄 LastModificationDate 13:51:01.206
>   - apply 전후 조회 값
>   - 시작 스케줄 LastModificationDate가 9월 29일 그대로인 점
> - 필요한 수정: 세션 기록에 원문이 남아 있으면 옮깁니다. 없으면 지금의 한계 표기를 유지합니다.
>
> **4. [개선] 병합 뒤 fmt·validate 기록이 없습니다** (OPS-03)
> - 근거: task.md:103의 시점은 "커밋 2 후·병합 후"입니다. fmt·validate는 병합 전(review.md:54-55)에만 있고, 병합 뒤 블록(73-90행)에는 plan만 있습니다.
> - 영향: 없다고 봅니다. 1회차 이후 tf 변경이 없고 병합 뒤 plan이 정상 계산됐습니다. 완료 기준(task.md:78)은 병합 전 결과로 충족됩니다.
> - 필요한 수정: 병합 뒤에는 plan만 실행했다고 한 줄 적습니다.
>
> **5. [개선] 재개 시각이 검사 기록과 맞지 않습니다**
> - 근거: progress.md:28은 "00:24 KST 재개(start…)"인데, verify-004 시작은 15:23:54Z(00:23:54 KST)입니다. start가 verify보다 먼저여야 합니다.
> - 필요한 수정: "00:23"으로 고칩니다.
>
> **6. [개선] 검증이 끝난 뒤에 남을 알림 노트·개발일지 문구가 있습니다**
> - 근거: 알림 노트 11행은 아직 "검증 중… 손으로 바꾸면 알려 주기"이고, 개발일지 35행은 "운영 적용은 병합 뒤에 한다"에서 끝납니다.
> - 영향: 거짓은 아니지만, 노트를 읽는 쪽은 아직 건드리면 안 된다고 볼 수 있습니다.
> - 필요한 수정: 노트에 확인 완료 시각을 적습니다. 개발일지는 snapshot 대상이라 지금 고치면 재검사가 필요하니 완료 뒤 별도 커밋을 권합니다.
>
> **7. [개선] SEC-05 검색식이 검색 명령 문자열 자체를 잡습니다**
> - 근거: task.md:109의 기준은 0건인데 review.md:140은 1건입니다. 작업 폴더를 직접 검색하니 task.md:109와 review.md:140 두 줄이고, 둘 다 검색식·설명 문자열입니다. 실제 비밀값은 없습니다. review.md를 커밋하면 2건이 됩니다.
> - 필요한 수정: review.md에 "커밋 뒤에는 review.md의 설명 줄도 잡힌다"고 덧붙입니다. 판정에는 영향이 없습니다.
>
> ### REQ별 판정
>
> | REQ | 확인한 근거 | 판정 |
> | --- | --- | --- |
> | REQ-01 | schedule.tf:43-57 변경 없음. 두 plan 모두 start 변경 없음. 18:23과 00:25 조회가 `cron(0 9 ? * MON-FRI *)`·Asia/Seoul·ENABLED, LastModificationDate 2026-09-29로 같음 | 충족 |
> | REQ-02 | 아래 대조 참고 | 충족 |
> | REQ-03 | 병합 뒤 plan 원문 0 add·1 change·0 destroy, stop `schedule_expression`만. apply 뒤 세 번의 조회 기록(13:51, 18:23, 00:25)이 모두 `cron(0 0 * * ? *)`·ENABLED, 시간대도 Asia/Seoul로 같음 | 충족 |
> | REQ-04 | progress.md:19-23의 순서(재배포 확인 → 조회 → plan 공유 → 승인·실험 여부 → 재조회 → 새 plan 대조 → apply → 재조회 → 파일 삭제)가 명세와 같음. LastModificationDate가 apply 시각과 일치. 승인과 파일 대조는 메인 기록뿐(지적 2, 3) | 충족(한계 있음) |
> | REQ-05 | server-offline-notice.tsx:7 문구, role="alert"와 조건(18-20행) 유지. 테스트 12-15행. verify-004 jest 6 suites / 32 passed | 충족 |
> | REQ-06 | 직접 검색에서 제외 대상 밖 18시 표현 0건(.github는 따로 검색). ADR 0015 내용, ADR 0011:6, 0014:6, README:106·112·114, docs/06:63, runbook 10·76·85, compose:55, svg 2개 확인 | 충족 |
>
> **REQ-02 대조**
> - 검증일 2026-11-25는 수요일이고 apply가 13:51이라 "그날"이 맞습니다.
> - 18:23 조회: running, LaunchTime 00:00:43Z, 중지 사유 빈 값, `/readyz` 200.
> - 00:25 재조회(11-23부터): StopInstances는 11-23과 11-24의 09:00:22Z(18:00:22 KST), 그리고 11-25 15:00:22Z(11-26 00:00:22 KST) 3건뿐입니다. 11-25 18시 전후는 0건입니다. 변경 전 이틀의 18시 중지가 같은 조회에 잡혀, 0건이 빈 조회 때문이 아님을 보여 줍니다.
> - 00:00:22 중지는 00:00~00:05 안의 1건입니다. 역할 `jbank-dev-scheduler`는 schedule.tf:24의 이름 규칙과 맞고, errorCode가 없으며, 인스턴스 중지 사유 시각 15:00:22 GMT와 같습니다.
> - StartInstances는 매일 00:00:43Z 1건씩이고 LaunchTime이 00:25에도 11-25 00:00:43Z입니다. 따라서 그사이 껐다 켠 적이 없습니다.
> - 중지 스케줄 LastModificationDate가 00:25에도 13:51:01이라, 검증 중 DISABLED 전환이나 저장본 복원이 없었습니다. 교착 실험 때문에 검증이 흐려지지 않았습니다.
> - 주말 수동 기동분은 명세대로 표현식(`* * ? *`)으로 확인됐습니다.
>
> ### 기준 ID별 판정
> - FE-01: 충족. lint·tsc·jest·build exit 0, 정적 페이지 25/25. 경고는 이 작업과 무관한 `app/products/**`의 `<img>`뿐입니다.
> - FE-05: 충족. 기존 alert 테스트 유지와 새 문구 검사가 있습니다. 문구만 바뀌어 수동 키보드 확인 대상은 없습니다.
> - OPS-03: 충족. plan 원문 두 개와 적용 전후 조회가 있습니다(지적 3, 4는 개선).
> - OPS-05: 충족. run 45549089991 success, headSha 3996237, 13:46:46~13:49:44 KST이고 progress.md의 13:49와 맞습니다. `/readyz` 502(13:47)는 배포 중, 200(13:50)은 배포 종료 뒤라 순서가 맞고 최초 502도 기록에 남아 있습니다. 되돌리기는 ADR 0015:40에 있습니다.
> - OPS-07: 충족(REQ-02 대조).
> - SEC-05: 충족. 작업 폴더를 직접 검색해 비밀값 0건, tfvars 파일 없음을 확인했습니다. 나머지 변경 파일의 검색 결과는 메인 기록이며, 읽은 부분에서는 비밀값을 보지 못했습니다. 인스턴스 ID는 이전 작업 기록에도 있던 값이고 비밀이 아닙니다.
> - SEC-07: 충족. plan 변경이 스케줄 1건뿐이고 schedule.tf의 IAM 블록은 그대로입니다.
>
> 적용 제외 이유(task.md:91-97)는 구현 결과와도 맞아 타당합니다.
>
> ### 완료 기준 5개
> 1. REQ-01~06 확인: 충족.
> 2. verify 통과: 충족(verify-004). `.claude/checks.json`의 6개 명령은 실제 검사이고 실행된 argv와 같습니다. 백엔드는 UP-TO-DATE이고 실제 실행은 verify-001(3분 30초, 10 executed)입니다. changes.txt에 백엔드 앱 파일이 없어 정당합니다.
> 3. fmt·validate·plan: 충족(지적 4).
> 4. 병합 후 apply, 조회, 18시 이후 running, 00:00 StopInstances 기록: 충족.
> 5. 교착 실험 알림: 노트와 progress.md 기록은 확인했습니다. 메인 세션 보고는 파일로 확인할 수 없습니다(지적 1).
>
> ### 이전 지적 처리 확인
> - 1 [중요] ADR 비용 산술: 해결. ADR 0015:25가 제안대로 고쳐졌습니다. 0.026×195+5.4≈10.5, 0.026×326+5.4≈13.9, 증가분 약 3.4로 계산이 맞습니다.
> - 2 [중요] 병합 후 필수 확인: 해결. 1회차의 6개 항목 중 1~4는 값으로 확인됐고 6(증거 보존·재요청)도 처리됐습니다. 5(알림)는 지적 1만 남았습니다.
> - 3 [중요] plan 원문: 해결. review.md:48-69와 73-90에 명령, 시각, 커밋, 변경 블록, Plan 줄이 있습니다.
> - 4: 해결(progress.md:14, 28).
> - 5: 사실로 남김. 타당합니다.
> - 6: 해결(observability.md:10).
> - 7: 해결. 병합 뒤 재조회와 infra-cd skipped 기록이 있고, infra-cd.yml:30, 63의 게이트와 맞습니다.
> - 8: 범위 밖으로 유지. 타당합니다.
> - 계획 리뷰 3회차 개선 4건: 1(재배포 확인 기록), 2(apply 직후 노트), 3(plan 파일 삭제), 4(ADR 0015:39) 반영 확인.
>
> ### 범위와 기록
> - changes.txt의 18개 파일은 계획 범위와 같습니다(devlog README 1줄은 개발일지 목록). 교착 실험 runbook과 perf 변경은 없습니다.
> - 기록에서 실제와 다른 문구는 지적 5의 시각 하나입니다. 결과를 부풀린 문구는 찾지 못했습니다.
> - review.md의 "대기 중" 세 곳(13, 41, 154행)은 review pass 전에 채워야 합니다.
>
> ### 확인하지 못한 범위
> **메인 기록뿐인 주장**
> - AWS 조회 값, CloudTrail 이벤트, terraform plan·apply 결과, gh run·variable 결과, `/readyz` 응답. 서로 다른 시점의 기록끼리는 모순이 없지만 제가 다시 조회하지 않았습니다.
> - 사용자의 승인과 "오늘은 없음" 답변, 커밋 계획 승인
> - 직전 재조회의 정확한 값, 새 plan 파일 대조, plan 파일 삭제
> - 메인 세션 보고, api 컨테이너 재생성과 postgres 유지
> - PR #20·#19 병합 시각과 PR 본문(Q-05 조건 문구), origin/main 2d825b3의 내용
> - 병합 뒤 plan을 실행한 작업 트리(3996237)
>
> **읽기만으로 확인할 수 없는 것**
> - 커밋별 diff와 커밋 메시지(attribution 0건 포함)
> - check 로그의 해시값, review-1.md의 해시 일치
>
> **아직 일어나지 않았거나 명세상 대신 확인한 것**
> - 11-26 09:00 자동 시작(REQ-01 기준은 조회 값이라 완료를 막지 않음)
> - 주말 00:00 실제 중지, 실제 되돌리기 실행
>
> 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | infra/terraform/modules/ec2/schedule.tf(start 변경 없음) | 병합 뒤 plan에 start 변경 없음, apply 뒤 get-schedule start `cron(0 9 ? * MON-FRI *)` Asia/Seoul ENABLED(LastModificationDate 2026-09-29) | 통과 |
| REQ-02 | schedule.tf stop `cron(0 0 * * ? *)` | 2026-11-25 18:23 running·18시 전후 StopInstances 0건, 2026-11-26 00:00:22 jbank-dev-scheduler StopInstances 1건(아래 원문), 표현식 매일 | 통과 |
| REQ-03 | schedule.tf | 병합 뒤 plan 0 add·1 change·0 destroy, apply 뒤 get-schedule stop `cron(0 0 * * ? *)` Asia/Seoul ENABLED | 통과 |
| REQ-04 | ADR 0015 적용 절차 | progress.md 13:50~13:51 기록(plan 공유, 승인, 직전 재조회, 새 plan 대조, apply, 재조회, plan 파일 삭제), stop LastModificationDate 13:51:01 | 통과 |
| REQ-05 | server-offline-notice.tsx, 주석 2곳 | server-offline-notice.test.tsx 신규 검사, verify-004 jest 32개 통과 | 통과 |
| REQ-06 | ADR 0015, 0011·0014 상태, README, runbook, docs/06, svg, 주석 | 검색 0건(2026-11-26 재실행), 1회차 verifier 내용 검토, 지적 1·6 수정(cb4e6b4, 4d4003f) | 통과 |

## 지적별 처리

1회차 지적:

- 1 [중요] ADR 0015 비용 합계: cb4e6b4에서 제안한 문장으로 고침.
- 2 [중요] 병합 후 필수 확인: 병합 재배포·apply·18시 이후·00시 중지를 모두 확인함(아래 운영 확인).
- 3 [중요] plan 원문: 구현 뒤 plan과 병합 뒤 plan을 아래에 보존함.
- 4 [개선] progress.md에 verify-002 백엔드가 UP-TO-DATE라고 적음. verify-003·004의 백엔드도 UP-TO-DATE이고 실제 실행은 verify-001이다(이후 백엔드 코드 변경 없음).
- 5 [개선] 커밋 2~6마다 따로 확인한 기록은 없음(사실로 남김).
- 6 [개선] 4d4003f에서 observability runbook에 병합 전 stopped 확인을 넣음.
- 7 [개선] 병합 뒤 `gh variable list` 재조회: `EC2_DEPLOY_READY`만 있음. infra-cd는 skipped.
- 8 [개선, 범위 밖] docs/06 비용표는 고치지 않음.

2회차 지적:

verifier 지적은 모두 개선이고, complete 전에 다음과 같이 처리했다.

- 1 교착 실험 알림: 2026-11-25 13:51:32 KST 마무리 보고(wrap_up 요약)에 "교착 실험 작업용 노트(ops-hours-midnight.md)를 공유 폴더에 남김: 스케줄 저장본은 apply 이후 것만 써야 함"을 넣어 이 세션을 시작한 메인 세션에 자동 전달되게 했다(수신 자체는 이 세션에서 볼 수 없음). 실제 반영은 main의 60882a0(11-25 23:18, runbook을 24시 중지에 맞춤)에서 확인했다. 아래 "교착 실험 알림" 참고.
- 2 승인 시각: 세션 기록 기준 질문 13:50:28, 답변 13:50:40 KST. progress.md에 적었다.
- 3 apply 출력: 세션 기록에서 원문을 꺼내 아래에 붙였다(계정 ID·공인 IP가 든 Outputs 블록만 뺌).
- 4 병합 뒤 fmt·validate: 실행하지 않았고 plan만 실행했다. plan 실행 때 이 브랜치와 병합된 main의 infra/terraform 차이가 없음을 git diff로 확인했다(출력 없음).
- 5 재개 시각: 00:23으로 고쳤다.
- 6 알림 노트에 확인 완료를 적었다. 개발일지는 완료 기록과 함께 별도 커밋으로 고친다.
- 7 SEC-05: 아래 검색 항목에 덧붙였다.

## 완료 기준별 근거

### terraform plan

명령은 모두 `-chdir=infra/terraform/envs/dev`. `init -plugin-dir=<메인 체크아웃 provider> -backend-config=bucket=<상태 버킷>`, `fmt -check -recursive ../..`, `validate`, `plan -input=false -var-file=<메인 체크아웃 terraform.tfvars>`. 구현 뒤(병합 전):

```
2026-11-25 12:54:31 KST
5b22ea1dd137535a7e6b968a50fb4d9dd3c9ad7d
Terraform v1.15.8
├── provider[registry.terraform.io/hashicorp/aws] ~> 5.0
fmt: exit 0
validate: Success! The configuration is valid.
Terraform will perform the following actions:

  # module.ec2.aws_scheduler_schedule.stop will be updated in-place
  ~ resource "aws_scheduler_schedule" "stop" {
        id                           = "default/jbank-dev-app-stop"
        name                         = "jbank-dev-app-stop"
      ~ schedule_expression          = "cron(0 18 * * ? *)" -> "cron(0 0 * * ? *)"
        # (9 unchanged attributes hidden)

        # (2 unchanged blocks hidden)
    }

Plan: 0 to add, 1 to change, 0 to destroy.
```

병합 뒤(apply 승인에 쓴 plan):

```
2026-11-25 13:50:15 KST
39962374ebcd8cf1618e02de91e9c0420d2ddae3
Terraform v1.15.8
Terraform will perform the following actions:

  # module.ec2.aws_scheduler_schedule.stop will be updated in-place
  ~ resource "aws_scheduler_schedule" "stop" {
        id                           = "default/jbank-dev-app-stop"
        name                         = "jbank-dev-app-stop"
      ~ schedule_expression          = "cron(0 18 * * ? *)" -> "cron(0 0 * * ? *)"
        # (9 unchanged attributes hidden)

        # (2 unchanged blocks hidden)
    }

Plan: 0 to add, 1 to change, 0 to destroy.
```

apply 실행 출력(2026-11-25, 세션 기록에서 옮기고 Outputs 블록만 뺌. 실행 스크립트가 Refreshing 줄과 빈 줄은 처음부터 걸렀다. 새 plan 파일 대조는 `terraform show -json`으로 변경 1건·달라진 속성이 schedule_expression뿐인지 검사한 결과):

```
2026-11-25 13:50:52 KST
직전 stop: cron(0 18 * * ? *)	Asia/Seoul	ENABLED
새 plan 대조 일치: {'schedule_expression': ('cron(0 18 * * ? *)', 'cron(0 0 * * ? *)')} state ENABLED
Warning: Deprecated Parameter
The parameter "dynamodb_table" is deprecated. Use parameter "use_lockfile"
instead.
module.ec2.aws_scheduler_schedule.stop: Modifying... [id=default/jbank-dev-app-stop]
module.ec2.aws_scheduler_schedule.stop: Modifications complete after 0s [id=default/jbank-dev-app-stop]
Apply complete! Resources: 0 added, 1 changed, 0 destroyed.
apply exit 0
2026-11-25 13:51:01 KST
이후 stop: cron(0 0 * * ? *)	Asia/Seoul	ENABLED
이후 start: cron(0 9 ? * MON-FRI *)	Asia/Seoul	ENABLED
plan 파일 삭제됨
```

병합 뒤에는 fmt·validate를 다시 실행하지 않았다(tf 파일이 병합 전과 같음).

### 교착 실험 알림

- 공유 노트 `ops-hours-midnight.md`: 2026-11-25 13:51 KST 작성. runbook 4-4·141행·162행, 저장본은 apply 이후 것.
- 메인 세션 보고: 13:51:32 KST 마무리 보고에 포함(자동 전달).
- 반영 확인: main의 docs/runbook/transfer-deadlock-experiment.md가 60882a0(11-25 23:18)에서 "매일 24시에 꺼진다(ADR 0015)", "저장본은 이 작업 직전에 새로 받는다"로 바뀌었다.

### 병합 재배포(OPS-05)

backend-cd run 45549089991: conclusion success, headSha 3996237, 2026-11-25 13:46:46~13:49:44 KST, job build-and-deploy success(2026-11-26 재조회). `/readyz` 배포 중 502(13:47), 배포 뒤 200(13:50).

### 18시 이후(REQ-02, OPS-07), 2026-11-25 18:23:10~18:23:13 KST 조회

조회 출력에서 값만 옮겨 적음(JSON 형식은 줄임).

```
State running, LaunchTime 2026-11-25T00:00:43+00:00, StateTransitionReason ""
/readyz {"status":"UP"} http=200
jbank-dev-app-stop cron(0 0 * * ? *) Asia/Seoul ENABLED, LastModificationDate 2026-11-25T13:51:01.206000+09:00
jbank-dev-app-start cron(0 9 ? * MON-FRI *) Asia/Seoul ENABLED, LastModificationDate 2026-09-29T12:11:48.696000+09:00
CloudTrail StopInstances(2026-11-25T00:00:00Z 이후): []
CloudTrail StartInstances(2026-11-25T00:00:00Z 이후): 2026-11-25T09:00:43+09:00 i-0b71df18394cf8010 1건
```

### 00시 중지(REQ-02, OPS-07)

첫 조회는 2026-11-26 00:23:09 KST(progress.md), 아래는 리뷰용 재조회다. CloudTrail 시각은 UTC(2026-11-25T15:00:22Z = 11-26 00:00:22 KST). 11-23·11-24의 09:00:22Z(18:00:22 KST) 중지는 변경 전 동작이고 11-25에는 없다.

```
2026-11-26 00:25:10 KST 재조회
$ aws cloudtrail lookup-events --region ap-northeast-2 --lookup-attributes AttributeKey=EventName,AttributeValue=<StopInstances|StartInstances> --start-time 2026-11-23T00:00:00Z  (CloudTrailEvent에서 뽑은 필드)
StopInstances 2026-11-25T15:00:22Z c74519de-0c3f-4872-b5fe-1e9080546975 AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 running -> stopping errorCode=None
StopInstances 2026-11-24T09:00:22Z ebd7e54e-c8e8-4e8d-b77e-31a500d8942f AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 running -> stopping errorCode=None
StopInstances 2026-11-23T09:00:22Z ee024721-9823-4040-ab3f-d68757894160 AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 running -> stopping errorCode=None
StartInstances 2026-11-25T00:00:43Z ec315e9a-0f78-4704-9b7f-248cc10db6c8 AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 stopped -> pending errorCode=None
StartInstances 2026-11-24T00:00:43Z 13853012-b429-4969-ab95-1ea514c05d35 AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 stopped -> pending errorCode=None
StartInstances 2026-11-23T00:00:43Z ba0658a0-a0c4-4bbe-9dff-24229b8b820e AssumedRole jbank-dev-scheduler i-0b71df18394cf8010 stopped -> pending errorCode=None
$ aws ec2 describe-instances --instance-ids i-0b71df18394cf8010
[
    {
        "State": "stopped",
        "Launch": "2026-11-25T00:00:43+00:00",
        "Reason": "User initiated (2026-11-25 15:00:22 GMT)"
    }
]
$ aws scheduler get-schedule
jbank-dev-app-stop	cron(0 0 * * ? *)	Asia/Seoul	ENABLED	2026-11-25T13:51:01.206000+09:00
jbank-dev-app-start	cron(0 9 ? * MON-FRI *)	Asia/Seoul	ENABLED	2026-09-29T12:11:48.696000+09:00
```

### SEC-05, SEC-07, REQ-06 검색(2026-11-26)

- REQ-06 `git grep` 명령 0건.
- `git diff 65d15e0..HEAD` 비밀값 검색은 1건: task.md의 검색 명령 문자열 자체다. 작업 기록 폴더를 빼면 0건, 실제 값 형태(AKIA 뒤 16자 등) 0건. `git ls-files`의 tfvars 0건. (1회차의 0건은 작업 폴더를 커밋하기 전이었다. 이 보고서를 커밋하면 검색식을 설명하는 줄과 verifier 응답의 줄도 잡힌다.)
- 병합 뒤 plan에 aws_iam 변경 0건.
- 커밋 메시지(65d15e0..HEAD) attribution 검색 0건.

## 성능 테스트 확인

불필요. 요청 경로·처리량·자원 사용 변화가 없고 켜져 있는 시간만 늘어난다.

## 발견한 문제

2회차 지적과 처리는 위 "지적별 처리"와 같다.

## 판정과 이유

통과. 차단·중요 지적 없음. 1회차 중요 지적 3건이 해소됐고 2회차 개선 7건은 위와 같이 처리했다. REQ-01~06이 구현과 실행 증거로 확인됐다.

## 확인하지 못한 부분

- 다음 평일(11-26) 09:00 시작은 apply 뒤 아직 일어나지 않았다. 시작 스케줄은 바꾸지 않았고 조회 값·LastModificationDate로 확인했다(REQ-01 기준).
- 주말 수동 기동분의 00:00 중지는 명세대로 실제 실행 대신 표현식으로 확인했다.
- 직전 재조회와 apply 사이 수 초의 틈은 한계로 남는다(ADR 0015).
- origin/main은 이 브랜치 뒤로 2d825b3까지 나아갔다(교착 실험 등 다른 작업). 이 작업의 schedule.tf는 main에서도 `cron(0 0 * * ? *)`다. 나머지는 이 브랜치 기준으로 검사했다.
