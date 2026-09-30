# 검토 결과

## 검토 범위

PR #8(main 513796b)로 머지된 커밋 446df5b, 06f9f5d, d72d1be, 789bcf5, 0803887, bcac0fd와 적용·실제 환경 검증 기록(progress.md).

## 독립 검증 결과

### 1회차 (2026-09-30, verifier ab5f93306499bfa66)

회차 판정: 수정 필요

> ## 결과 리뷰 1회차: ec2-weekday-schedule
>
> - 검증 대상 snapshot: `e631edd84e983d93fbf6e1bbbab47cf83bd995af85aa44e7f28f87cae693c575`. verify-002 기준이고 HEAD는 bcac0fd(dirty)입니다. 배포된 코드는 main 513796b와 이후 5c21e20입니다.
> - 판정: **수정 필요**. 계획에 적은 SEC-05 증거가 실행·기록되지 않았습니다(중요 1건). 이 한 건만 기록하면 통과 권고할 수 있는 상태입니다.
>
> ### 읽은 자료
> - 기준·명세: docs/testing-policy.md, task.md, plan-review.md(1~4회차), progress.md, state.json
> - 검사 증거: evidence/checks.json, changes.txt, verify-002/check-4.log, check-5.log
> - 인프라·CD: schedule.tf, main.tf, user_data.sh.tpl, run-batch.sh, host/{boot.sh, sync-latest.sh, batch_dates.py, install-host.sh, jbank-boot.service}, tests/{boot-test.sh, cd-skip-test.sh}, .github/scripts/deploy-ec2.sh, backend-cd.yml
> - 프론트: proxy route와 테스트, server-status route와 테스트, lib/server-status와 테스트, backend-offline.ts, api-client.ts, server-offline-notice와 테스트, layout.tsx
> - 문서: ADR 0011, 인프라아키텍처 문서, README
> - 기타: scratchpad의 boot-test.out·cd-skip-test.out, FE-04와 E2E-04 스크린샷 2장
> - 상세 기준 문서(docs/testing/*.md)는 다시 읽지 않았습니다. 계획 리뷰 1~4회차에서 확인한 ID 적용 판단을 그대로 썼습니다.
>
> ### 적용 기준 ID
> SEC-05·07, OPS-01·02·03·04·05·07, BE-01, BE-01(실제 DB), FE-01·03·04·05, E2E-04, 정적 검사
>
> ### 확인하지 못한 범위
> - AWS·호스트·CloudTrail·GitHub run 결과는 읽기만으로 확인할 수 없습니다. progress.md의 요약 기록만 근거로 삼았습니다. 원시 출력(명령 원문, 로그 발췌)은 저장소에 없습니다.
> - boot-test.out(2026-09-29 03:05Z)과 cd-skip-test.out이 최종 커밋의 스크립트로 실행한 결과인지 git으로 대조하지 못했습니다. 다만 머지 후 실제 환경 실행이 최종 코드를 다시 검증했습니다.
> - 모의·실제 검사는 제가 실행하지 않았습니다.
>
> ### 지적
>
> **1. [중요] SEC-05: 계획한 비밀값 검사를 실행한 기록이 없음**
> - 근거: task.md:125는 "`git diff main... | grep -nE ...` 결과 0"을 리뷰 전에 확인하고 review.md에 남기도록 정했습니다. progress.md와 작업 폴더 어디에도 실행 기록이 없습니다. 게다가 브랜치가 이미 main에 머지되어, 지금 `git diff main...`을 실행하면 빈 diff가 나옵니다. 그러면 통과해도 아무것도 검사하지 않은 셈입니다.
> - 참고로 제가 읽기 전용 Grep을 돌린 결과는 다음과 같습니다.
>   - 대상: 변경 파일 전체(host/*, run-batch.sh, tests/*, modules/ec2/*, .github/scripts, backend-cd.yml, 프론트 변경 파일, ADR 0011, 인프라 문서, README)
>   - 패턴: 같은 패턴 `AKIA|aws_secret|PASSWORD=|SECRET=|token=|eyJhbGci`
>   - 결과: 0건. 파일 전체는 추가된 줄을 모두 포함하므로 실제 위험은 낮다고 봅니다.
> - 영향: 합의한 필수 증거가 빠진 상태입니다. 정책상 근거가 없으면 완료하지 않습니다.
> - 필요한 수정: 범위를 명시해 실행하고 결과(0건)와 명령을 review.md에 기록합니다. 예: `git diff fa97aef8147f2228d2a95086092bdce2d05fa7ba..bcac0fd -- . ':!.claude' | grep -nE '...'`
>
> ### 개선 (완료를 막지 않음)
>
> 1. **실제 DB 조회 사례가 FAILED 제외 조건을 구별하지 못함 (BE-01 실제 DB, REQ-06)**
>    - FAILED 기록은 2026-01-01(exec 6) 하나뿐이고, COMPLETED 최댓값 2026-09-28보다 이른 날짜입니다. 그래서 `e.status = 'COMPLETED'` 조건이 없어도 결과는 9/28로 같았을 것입니다.
>    - boot-test의 가짜 psql도 SQL의 의미를 해석하지 않습니다.
>    - 따라서 progress.md:25의 "FAILED·COMPLETED 혼재에도 COMPLETED 최댓값"은 증거가 보여 주는 것보다 강한 표현입니다. 조건이 맞다는 근거는 코드 읽기(boot.sh:37)뿐이고, 코드는 맞습니다. 보고서 표현을 "조회 동작 확인, FAILED 제외 조건은 코드 검토로 확인"으로 낮추길 권합니다.
>    - 이 사례 설계(더 이른 날짜 사용)는 계획 리뷰 3회차 개선 2에서 데이터 공백을 피하려고 합의한 것입니다. 그래서 이 제약 자체는 결함으로 보지 않습니다.
>
> 2. **마지막 완료일 조회가 실패하면 따라잡기 날짜가 건너뛰어질 수 있음 (boot.sh:33-38, 50-57)**
>    - `last_completed`의 psql이 일시적으로 실패하면(docker exec 실패 등) 빈 값이 나옵니다. 이 값은 "기록 없음"으로 해석되어 어제 하나만 실행합니다.
>    - 성공하면 최댓값이 어제로 올라가, 그 사이 날짜의 CTR·FDS 검사가 영구히 빠집니다. 예: 월요일 부팅이면 금·토가 빠집니다.
>    - 발생 조건: api healthy 직후라 확률은 낮습니다. 로그에는 "마지막 완료 기준일: 없음"이 남습니다.
>    - 수정: 조회의 종료 코드를 확인하고, 실패하면 그 잡을 건너뛴 뒤 failed=1로 처리하길 권합니다.
>
> 3. **정적 검사 기록 누락**
>    - `bash -n`은 "커밋별"로 정했지만 기록이 없습니다. terraform fmt·validate는 커밋 1에서만 기록됐습니다.
>    - 모든 변경 셸 스크립트가 모의 검사나 실제 환경에서 실행됐으므로 문법 오류 위험은 사실상 없습니다. 기록만 보완하면 됩니다.
>
> 4. **SEC-07 plan 발췌가 요약뿐임**
>    - review.md에 넣기로 한 plan 발췌 대신 요약만 있습니다. 요약은 progress.md:9의 "4 add, 1 change(배포 역할 정책), 0 destroy"입니다.
>    - 코드(schedule.tf:7-41, main.tf:188-192)는 기준과 일치합니다. 스케줄러는 인스턴스 ARN에 Start·Stop만 허용하고 SourceAccount 조건이 있습니다. 배포 역할에는 `*` 리소스에 Describe 두 개만 추가됐습니다.
>    - REQ-08의 "기존 리소스 변경 없음"과 1 change의 관계, 즉 의도된 배포 역할 정책 변경이라는 점을 review.md에 한 줄로 적으면 됩니다.
>
> 5. **OPS-01 라벨 확인이 간접 증거뿐임**
>    - `docker inspect`로 라벨을 확인한 기록은 없습니다.
>    - 부팅 로그의 "교체 fa97aef → 513796b"와 "513796b → 5c21e20"은 sync-latest.sh가 라벨을 읽은 결과라 간접 증거로 충분합니다.
>
> 6. **E2E-04 스크린샷에서 안내 문구가 잘림**
>    - 두 번째 스크린샷은 스크롤 때문에 안내 띠의 아래 가장자리만 보입니다.
>    - "로그인 실패, 이동 없음"은 확인되지만, "안내 유지"는 첫 스크린샷과 색 띠로만 뒷받침됩니다.
>
> 7. **프론트 안내가 한 번 켜지면 새로고침 전까지 남음**
>    - 안내 상태(offline)는 새로고침하기 전까지 true로 남습니다. 예를 들어 08:59에 연 탭은 09시 이후 API가 성공해도 안내가 계속 보입니다.
>    - REQ-07 범위 밖이라 선택 사항입니다. 성공 응답 때 초기화하는 방식을 고려할 수 있습니다.
>
> 8. **후속 확인 권장**
>    - 운영 Vercel에서 인스턴스가 꺼진 상태의 실제 화면을 한 번 확인하길 권합니다. 계획은 로컬 확인만 요구했습니다.
>    - 주말 09:00에 StartInstances가 없는지(REQ-01 부정 사례) 다음 주 CloudTrail로 확인하길 권합니다. 지금은 표현식 `cron(0 9 ? * MON-FRI *)`로만 확인됩니다.
>    - 개발일지(커밋 계획 7)는 아직 없습니다. 계획대로 complete 뒤에 작성하면 됩니다.
>
> ### REQ별 대조
>
> | REQ | 구현 | 테스트·실제 증거 | 판단 |
> | --- | --- | --- | --- |
> | REQ-01·02 | schedule.tf. 시작은 월~금 09:00, 중지는 매일 18:00, Asia/Seoul | at() 사전 확인. 9/29 18:00:22 Stop, 9/30 09:00:43 Start(CloudTrail, 스케줄러 역할) | 충분. 주말은 표현식으로만 확인 |
> | REQ-03 | deploy-ec2.sh: running이 아니면 경고 후 0, Online 180초 대기, 폴링 10분, set -e로 조회 실패는 실패. concurrency 적용. 잠금 디렉터리 생성이 flock보다 앞섬 | 모의 15/15. 실제: 머지 CD 성공, 수동 중지 뒤 dispatch가 success와 경고 | 충분 |
> | REQ-04 | boot.sh·sync-latest.sh. 잠금 안에서 pull·비교·git·deploy, 교체 뒤 다시 준비 대기 | 모의 27/27(라벨 없음, 같은 값, pull·git 실패, 준비 초과, 경합 2방향). 실제: 같은 revision이면 교체 없음, fa97aef→513796b, 513796b→5c21e20 교체 | 충분 |
> | REQ-05 | 중단 기록 정리 SQL(잡·스텝), 잡별 독립 실행, 오늘 완료한 이자 잡 건너뜀, cron 제거 | 모의. 실제: 같은 날 2회 부팅 모두 실패 0, 이자 건너뜀, cron.d에 0hourly만 남음 | 충분 |
> | REQ-06 | batch_dates.py(KST), 실패한 날에서 멈춤 | 자체 검사(경계·UTC). 모의: 중간 실패 뒤 재시도. 실제: 9/28 → 9/29 순서. 실제 DB 정리 뒤 재실행 성공 | 충분. FAILED 제외 조건은 코드로만 확인(개선 1) |
> | REQ-07 | 연결 오류 6종만 503, 상태 경로는 3초 제한·응답 수신이면 online, 502·503·504와 네트워크 오류일 때만 확인 | jest 29 통과(check-4.log). 로컬 3.1초 offline. 스크린샷 2장 확인. 대비 17.83:1 | 충분 |
> | REQ-08 | 위 SEC-07과 같음 | 요약 기록 | 충분. 발췌는 개선 4 |
> | REQ-09 | ADR 0011:40-51, 인프라 문서:57-58·74, README:65 | 요구한 14개 항목을 모두 확인 | 충분 |
>
> ### 계획과 달라진 부분의 타당성
> - OPS-05 ③의 "18시 이후 CD 수동 실행"은 두 가지로 대체됐습니다.
>   - 9/30 09:00 부팅에서 fa97aef→513796b 교체(④ 충족)
>   - 운영 시간 중 수동 중지 → dispatch 성공과 경고(새 sha 5c21e20) → 재기동 때 5c21e20으로 교체
> - 대체 방식은 "꺼진 동안 새 이미지가 올라오고, 다음 부팅에 반영된다"는 Q-02의 핵심을 새 sha로 끝까지 확인했습니다. 원래 계획(같은 sha 재푸시)보다 오히려 강한 검증이라 타당합니다.
> - 같은 revision이면 교체하지 않는 경로(⑤)는 9/29 17:21·17:23 재부팅에서 확인됐습니다.
> - README를 main에서 재작성한 것은 범위 밖입니다. 운영 시간 안내 문장은 유지되어 있습니다(README.md:65).
>
> ### 검사 명령의 적절성
> - checks.json의 6개 명령은 모두 실제 앱 검사입니다: 하네스 unittest, lint, tsc, jest, gradle test·spotlessCheck, build.
> - gradle은 전부 UP-TO-DATE였습니다. 자바 변경이 없어 입력이 같으므로 타당합니다.
> - 인프라 검사를 verify에 넣지 않은 것은 계획에서 이유를 적어 합의한 사항입니다.
>
> ### 적용 제외 항목
> 계획 리뷰 1~4회차의 판단과 달라진 점이 없고, 모두 타당합니다: FE-02·06·07, BE-02·03·05~08(BE-04는 근거 기록), OPS-06·08, SEC-01~04·06·08, E2E-01~03·05·06, 성능 해당 없음.
>
> ### 이전 지적
> 결과 리뷰는 최초 회차라 이전 지적이 없습니다. 계획 리뷰 4회차의 구현 반영 개선 3건은 모두 반영된 것으로 봅니다.
> - concurrency와 잠금 디렉터리 선생성을 커밋 4에 포함
> - GHCR fa97aef 태그 존재 확인(progress.md:22)
> - jbank-boot가 inactive이고 실행 중인 잡이 없음을 확인한 뒤 실제 DB 정리 SQL 실행(progress.md:25)
>
> ### 관련 파일
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/task.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-weekday-schedule/progress.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/host/boot.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/terraform/modules/ec2/schedule.tf
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.github/scripts/deploy-ec2.sh
>
> (원문 마지막 줄) 최종 판정 문구: 수정 필요

### 1회차 처리 내역

- 중요 1(SEC-05 미실행): 범위를 명시해 실행. `git diff fa97aef8147f2228d2a95086092bdce2d05fa7ba..bcac0fd -- . ':!.claude' | grep -nE '^\+.*(AKIA|aws_secret|PASSWORD=|SECRET=|token=|eyJhbGci)'` → 적중 0(27 files, +937). 머지 전 브랜치 기준(fa97aef = 브랜치 분기 시 main의 #6 머지, bcac0fd = PR #8 head).
- 개선 1: progress 표현을 "조회 동작 확인, FAILED 제외 조건은 코드 검토(boot.sh last_completed의 status='COMPLETED')로 확인"으로 정정.
- 개선 2(조회 실패 시 날짜 누락 가능): 실제 위험이라 후속 작업으로 분리(작업 칩). 이번 범위 밖.
- 개선 3: 정적 검사 재실행 기록 — bash -n 7개(host 3, run-batch, tests 2, deploy-ec2) 통과, terraform fmt -check 통과, validate 통과, dev plan -detailed-exitcode "No changes"(exit 0).
- 개선 4: plan 1 change는 의도된 배포 역할 정책 변경(ec2:DescribeInstances·ssm:DescribeInstanceInformation 추가)이고 인스턴스·기존 리소스 교체·삭제 없음(4 add, 1 change, 0 destroy).
- 개선 5·6: 간접 증거로 충분하다는 판단에 동의, 기록만.
- 개선 7(안내가 새로고침 전까지 남음): 후속 작업으로 분리.
- 개선 8: 주말 미기동(REQ-01 부정 사례)은 다음 토요일 CloudTrail 확인 권장 사항으로 progress에 기록. 운영 Vercel 꺼진 화면 확인도 후속 권장.

## 계획과 다르게 처리한 부분

- OPS-05 ③(18시 이후 CD 수동 실행): 세션 종료로 미실행 → 다음 날 운영 시간 중 수동 중지 후 dispatch(성공+경고, 새 sha 5c21e20) → 재기동 때 5c21e20 교체로 대체. 새 sha로 끝까지 확인해 원래보다 강한 검증.
- 머지 후 README는 사용자 요청으로 main에서 별도 재작성(범위 밖, 운영 시간 안내 문장 유지).

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | infra/terraform/modules/ec2/schedule.tf(start `cron(0 9 ? * MON-FRI *)` Asia/Seoul) | at() 사전 확인, 2026-09-30 09:00:43 StartInstances(스케줄러 역할, CloudTrail) | 통과 |
| REQ-02 | schedule.tf(stop `cron(0 18 * * ? *)`) | at() 사전 확인, 2026-09-29 18:00:22 StopInstances(스케줄러 역할) | 통과 |
| REQ-03 | .github/scripts/deploy-ec2.sh, .github/workflows/backend-cd.yml(concurrency, label, dispatch) | cd-skip-test 15/15, 머지 CD 배포 성공, 수동 중지 후 dispatch success + "stopped 상태라 배포를 건너뜀" 경고(run 36663923093) | 통과 |
| REQ-04 | infra/compose/host/boot.sh, sync-latest.sh | boot-test 27/27, 실제: 같은 revision 교체 없음(9/29 재부팅 2회), fa97aef→513796b(9/30 09시), 513796b→5c21e20(9/30 재기동) | 통과 |
| REQ-05 | boot.sh(정리 SQL, 잡별 실행, 이자 건너뜀), install-host.sh(cron 삭제) | boot-test, 실제: 같은 날 두 번째 부팅 이자 건너뜀·실패 0, /etc/cron.d에 0hourly만 | 통과 |
| REQ-06 | infra/compose/host/batch_dates.py, boot.sh catch_up | batch_dates self-test, boot-test(중간 실패·재시도·기록 없음), 실제: CTR·FDS 9/28 → 9/29 순서 처리, 실제 DB 중단 기록 정리 후 재실행 성공 | 통과 |
| REQ-07 | apps/frontend/app/api/proxy/[...path]/route.ts, app/api/server-status/route.ts, lib/server-status.ts, lib/backend-offline.ts, lib/api-client.ts, components/server-offline-notice.tsx | jest 29/29, 로컬 확인(상태 3.1초 offline, 프록시 503), 모바일 스크린샷, 대비 17.83:1 | 통과 |
| REQ-08 | schedule.tf(scheduler 역할·SourceAccount), modules/ec2/main.tf(배포 역할 Describe 2개) | plan 4 add·1 change·0 destroy, 현재 plan No changes | 통과 |
| REQ-09 | docs/adr/0011-ec2-weekday-hours.md, docs/06_J-Bank_인프라아키텍처.md, README | 문서 항목 대조(verifier 1회차 14개 항목 확인) | 통과 |

## 완료 기준별 근거

- verify 통과(evidence/checks.json).
- SEC-05 적중 0(위 명령), SEC-07 코드·plan 일치.
- 실제 환경 검증은 progress.md에 명령 결과로 기록.

## 성능 테스트 확인

해당 없음(요청 경로 변화는 프록시의 연결 실패 처리뿐).

## 발견한 문제

1회차 중요 1(SEC-05 기록 누락) → 실행·기록.

## 판정과 이유

1회차 수정 필요 → SEC-05 실행·기록 후 재리뷰.

## 확인하지 못한 부분

주말 미기동은 표현식으로만 확인(다음 토요일 CloudTrail로 확인 권장). 운영 Vercel에서 꺼진 상태 화면은 미확인(로컬 확인만).
