# 검토 결과

## 검토 범위

결과 리뷰 1회차(병합·apply 전). 시작 커밋 65d15e0부터 5b22ea1까지. 대상 snapshot: ec0b20615b776f7059dcf38d7377f04ec04837da902d0510c115516376e9c51c

## 독립 검증 결과

verifier 1회차(2026-11-25) 응답 원문:

> ## 결과 리뷰 1회차(최초): ops-hours-midnight
>
> **결론**
> - 병합 전 판정: 조건부 가능. 코드와 설정에는 결함이 없습니다. ADR 0015의 비용 문장 하나(지적 1)를 고치고 verify를 다시 통과시킨 뒤 PR을 만들면 됩니다.
> - 완료(complete) 판정: 불가. REQ-02·REQ-04, REQ-01·03의 적용 후 부분, 병합 뒤 확인 항목이 아직 실행되지 않았습니다.
>
> **검증 대상과 읽은 범위**
> - 모드는 결과 리뷰, 작업 ID는 ops-hours-midnight입니다.
> - 대상 snapshot은 `ec0b20615b776f7059dcf38d7377f04ec04837da902d0510c115516376e9c51c`입니다. HEAD 5b22ea1, dirty false이고 state.json의 review_pending과 같습니다.
> - 승인된 계획 지문은 state.json과 plan-review 3회차가 `acacc9a6…`로 같습니다.
> - 읽은 자료:
>   - `docs/testing-policy.md`, `docs/testing/devops.md`·`frontend.md`·`security.md`
>   - task.md·progress.md·plan-review.md·state.json
>   - evidence의 checks.json 3개와 로그
>   - 변경 파일 18개(architecture.svg는 글자만 검색, 나머지는 해당 부분 원문)
>   - 교착 실험 runbook 112~171행, `infra-cd.yml` 게이트 행, worktree의 HEAD reflog, Gradle 결과 XML
> - 기준 ID는 FE-01, FE-05, OPS-03, OPS-05, OPS-07, SEC-05, SEC-07입니다.
> - 명령·테스트는 직접 실행하지 않았습니다.
>
> ### 지적
>
> **1. [중요] ADR 0015의 비용 합계 설명이 계산과 맞지 않습니다** (REQ-06)
> - 근거: `docs/adr/0015-ec2-weekday-until-midnight.md:25`가 9시간 인스턴스를 "약 $5", IPv4·디스크를 "약 $5.4"로 적고 "합계는 반올림하면 같은 약 $11이다"라고 합니다.
>   - 실제로는 0.026 × 195 ≈ 5.07이고, 5.07 + 5.4 = 10.47입니다. 반올림하면 $10입니다.
>   - 약 $11은 ADR 0011:43의 어림값 $6 + $5.4 = 11.4에서만 나옵니다. 변경 후의 8.5 + 5.4 ≈ $14는 맞습니다.
> - 영향: 운영에는 영향이 없습니다. 다만 결정 기록에 거짓 산술이 남고, 변경 전후(11 → 14)가 서로 다른 기준으로 비교돼 증가폭이 $3으로 보입니다. 같은 기준으로는 약 $3.4입니다.
> - 원인: task.md REQ-06의 "합계 약 $11 … $6는 반올림 차이"에서 왔고, 계획 리뷰에서 제가 걸러내지 못했습니다.
> - 필요한 수정: 마지막 문장만 고칩니다. 예: "약 $11은 ADR 0011의 어림값(인스턴스 약 $6)을 그대로 쓴 것이고, 시간당 요금으로 다시 계산한 변경 전 합계는 약 $10.5다(증가분 약 $3.4)." "약 $11 → 약 $14" 표기는 유지돼 REQ-06과 충돌하지 않으므로 task.md는 고칠 필요가 없습니다.
> - 후속: 별도 `docs` 커밋 → verify 재실행. 이것만을 위한 병합 전 재리뷰는 요구하지 않습니다. 남은 재리뷰 2회를 아끼고 병합 후 최종 리뷰에서 확인합니다.
>
> **2. [중요, 완료 차단·병합 비차단] 병합 후 필수 확인이 미실행입니다**
> - 근거: task.md:55, 57, 79-80, 104-106과 progress.md:14.
> - 남은 항목은 아래 "병합 후 확인 필요"에 있습니다. 시점상 당연하며 결함은 아니지만 통과로 볼 수 없습니다.
>
> **3. [중요, 완료 차단·병합 비차단] plan 출력 원문이 어디에도 없습니다** (OPS-03, SEC-07)
> - 근거: task.md:103, 108은 증거 위치를 `review.md(plan 발췌)`로 정했습니다. 지금은 progress.md:10의 요약 문장뿐이고 review.md는 아직 없습니다(Glob 확인).
>   - "0 add·1 change·0 destroy, IAM 변경 0건"을 원문과 대조할 수 없습니다.
>   - fmt·validate 결과와 SEC-05 검색 결과(progress.md:9)도 요약뿐입니다.
> - 필요한 수정: 리뷰 보고서(review.md)를 쓸 때 다음을 붙입니다.
>   - 실행한 명령(`-var-file`·`-plugin-dir` 포함, 비밀값 제외)
>   - terraform 버전과 시각
>   - plan의 변경 블록과 `Plan:` 줄
>   - 병합 후 새 plan도 같은 형식
> - 병합을 막지 않는 이유: schedule.tf 원문으로 변경 범위가 확인되고, apply 전에 plan 공유·승인이 다시 있습니다.
>
> **4. [개선] verify-002의 백엔드 테스트는 다시 돌지 않았습니다**
> - 근거: `verify-002/check-5.log`는 `:test UP-TO-DATE`, 1.3초입니다. 실제 실행은 verify-001(`:test` 실행, 3분 30초, 10 executed)입니다.
> - 두 verify 사이의 변경은 devlog 파일 2개뿐이라(`changes.txt` 대조 + reflog) Gradle 캐시 사용은 정당합니다.
> - progress.md:12의 "verify-002 통과"에 "백엔드는 verify-001 실행분, 002는 UP-TO-DATE"라고 덧붙이면 정확합니다.
>
> **5. [개선] 커밋별 중간 확인 시점이 계획과 다릅니다**
> - 근거: reflog에서 커밋 2~6이 연속 2초(1795578154~55) 안에 만들어졌습니다. task.md의 "커밋 2 후 plan", "커밋 4 후 jest"는 기록상 전체 커밋 뒤에 한 번에 확인됐습니다.
> - 최종 내용이 같고 중간 커밋이 빌드를 깰 변경이 아니어서(문구·주석·표현식) 실해는 없습니다. 중간 커밋별 빌드 확인 기록은 없습니다.
>
> **6. [개선] "평일 00:00~09:00 병합"의 끝 경계가 위험합니다**
> - 근거: `docs/runbook/observability.md:10,76`, `docker-compose.prod.yml:55`, ADR 0015:29. 09:00 직전에 병합하면 이미지 빌드가 끝날 때 인스턴스가 이미 켜져, `deploy-ec2.sh:12-17`이 건너뛰지 않고 배포합니다. 부팅 배치 직후 postgres가 재생성될 수 있습니다.
> - 제안: "09시 전에 backend-cd가 끝날 수 있게(예: 08:30 이전)" 또는 "병합 전 인스턴스가 stopped인지 확인" 한 줄. 옛 "18시 이후" 규칙에도 있던 빈틈이라 필수는 아닙니다.
>
> **7. [개선] 병합 직전에 `TERRAFORM_CI_READY`를 한 번 더 조회하기를 권합니다**
> - 근거: ADR 0015:34의 "자동 apply는 꺼져 있다"는 2026-11-23 조회 기록에 의존합니다(task.md:22). 게이트 자체는 `infra-cd.yml:30,63`에서 확인했습니다.
>
> **8. [개선, 범위 밖] `docs/06_J-Bank_인프라아키텍처.md:149,154` 비용표**
> - EC2 ~$15, 합계 ~$20으로 상시 가동 시절 값입니다. 이번 변경 이전부터 어긋나 있었고 REQ에 없으니 후속으로 두면 됩니다.
>
> ### REQ별 구현·테스트·증거
>
> | REQ | 구현(원문 확인) | 테스트·증거 | 판정 |
> | --- | --- | --- | --- |
> | REQ-01 | `schedule.tf:43-57` `cron(0 9 ? * MON-FRI *)`·Asia/Seoul 유지. state 속성 없음(기본 ENABLED) | plan은 progress.md:10 요약뿐 | 코드 충족. apply 후 조회는 병합 후 확인 필요 |
> | REQ-02 | `schedule.tf:61` `cron(0 0 * * ? *)`(매일), Asia/Seoul | 없음 | 병합 후 확인 필요 |
> | REQ-03 | `schedule.tf`는 +3/−3(changes.txt): 주석 2줄 + 표현식 1줄. 다른 tf 파일 변경 없음 | plan 요약 기록뿐(지적 3) | 코드 충족. 원문 보존과 apply 후 조회 필요 |
> | REQ-04 | ADR 0015:34-40에 순서·한계 기록 | 없음 | 병합 후 확인 필요 |
> | REQ-05 | `server-offline-notice.tsx:7` 문구. `role="alert"`·조건(18-20행) 그대로. `server-status.ts:4`·`route.ts:28`은 주석뿐 | `server-offline-notice.test.tsx:12-15` 신규, jest 32개 통과 | 충족 |
> | REQ-06 | 아래 항목별 확인 | 독립 grep으로 범위 파일 잔존 0건 | 지적 1 외 충족 |
>
> **REQ-06 항목별 확인**
> - ADR 0015
>   - 결정:13-16, 이유:9, 비용:25(지적 1), 주말 수동 기동:16, 공휴일:26
>   - 겹친 배포:27, 병합 시각:29, backend-cd 조건:34, 문구 불일치:35
>   - 교착 실험 연장과 저장본 주의:36-38, 수 초 틈:39(계획 리뷰 3회차 개선 4 반영), 되돌리기:40
>   - 부가 수치(326h → 456h ≈ 1.4배, 하루 $0.24 → $0.39)는 계산이 맞습니다.
> - ADR 0011:6, ADR 0014:6 상태 줄 있음. changes.txt상 각 +1줄이라 본문 변경 없음.
> - README:11, 106, 112($24 → $14, 평일 09~24시), 114(ADR 목록) 반영.
> - docs/06:63, observability runbook 10·76·85, compose:55, backend-cd:61, deploy-ec2:3 반영. 로직 변경 없음.
> - svg: weekday-ops "매일 24:00 중지", architecture "평일 9~24시". 남은 18시 표현 없음.
>
> ### 기준 ID별
> - FE-01: 충족. lint·tsc·jest·build 모두 exit 0, `next build` 25/25 생성. 경고는 변경하지 않은 `app/products/**`의 `<img>`뿐이고 lint 로그에 Error 없음.
> - FE-05: 충족. 기존 alert 테스트 유지 + 새 문구 검사.
> - OPS-03: plan 행은 원문 보존 필요(지적 3), 적용 행은 병합 후.
> - OPS-05: 되돌리기 문서 충족(ADR 0015:40). 병합 재배포 행은 병합 후.
> - OPS-07: 병합 후.
> - SEC-05: 충족. 제가 검색한 문서·tf·CI·compose 파일에 비밀값 없음(runbook:29는 변경 전부터 있는 자리표시 설명문). 프론트 변경 파일은 원문으로 확인. tfvars는 폴더에 없고 `.terraform.lock.hcl`·`.terraform/`·tfvars 모두 `.gitignore:24-29` 대상.
> - SEC-07: 코드상 IAM 변경 없음. plan 근거는 지적 3.
>
> ### 파일로 확인된 주장과 메인 기록뿐인 주장
>
> **파일로 확인됨**
> - 커밋 1~7의 순서·제목(reflog), 변경 파일 18개, verify 두 번의 HEAD와 결과
> - REQ-06 변경 후 0건(제외 대상 외 직접 검색, `.github`·`.claude` 포함)
> - 변경 전 대조 목록(계획 3회차에서 독립 확인한 것과 같음)
> - devlog 커밋 뒤 verify-002, tfvars 미복사, 잠금 파일이 gitignore 대상
> - 백엔드 211초 실행. Gradle 결과 XML에 실패·오류·건너뜀 0건(통합 테스트 시각 2026-11-25 03:48Z가 verify-001과 일치)
>
> **메인 기록뿐**
> - terraform fmt·validate·plan 결과와 init 1차 실패·해결
> - AWS 스케줄 조회 값, `gh variable list`
> - 사용자의 커밋 계획 승인과 Q-02~05 발언
> - Docker 깨진 링크·Desktop 29.2.1, 다이어그램 렌더 확인
> - plan-review의 verifier 응답이 원문이라는 점
> - 커밋별 실제 diff와 1~6번 커밋 메시지 본문. 커밋 객체를 읽을 수 없어 확인하지 못했습니다. 마지막 커밋 메시지에는 attribution이 없습니다.
>
> ### verify의 실효성
> - `.claude/checks.json`의 6개 명령은 실제 검사이고 실행된 argv와 같습니다. skipped_checks는 비어 있습니다.
> - 새 테스트 실행 근거: 테스트 소스의 케이스 수를 직접 세면 프록시 9 + server-status API 6 + 안내 4(신규 1 포함) + api-client 5 + domain-error 1 + server-status 7 = 32, suite 6개입니다. 로그의 "6 passed / 32 passed"와 일치합니다. 개별 테스트 이름은 로그에 없습니다.
>
> ### 범위·커밋·문구
> - 범위 밖 변경: 없음. `docs/devlog/README.md` 1줄은 개발일지 목록이라 정당합니다. 교착 실험 runbook과 `perf/`는 변경 목록에 없습니다.
> - 커밋 분리: 제목·구성이 계획과 같고 기능·테스트·설정·문서가 나뉘어 있습니다. devlog 커밋 내용은 verify-001·002 사이의 changes 차이로도 확인됩니다.
> - devlog: 사실과 다른 문구를 찾지 못했습니다. 운영 적용이 병합 뒤라고 명시하고 있습니다. "약 $11에서 약 $14"는 ADR 0011 기준 표기라 그대로 둬도 됩니다.
> - 적용 제외 항목의 이유(FE-02·03·04·06·07, 백엔드, AI, 성능, 전체 흐름, OPS-01·02·04·06·08, SEC-01~04·06·08)는 구현 결과와도 맞아 타당합니다.
>
> ### 병합 후 확인 필요(통과 아님)
> 1. OPS-05: backend-cd 결과(성공 또는 건너뜀), 배포했으면 `/readyz` 200
> 2. REQ-04: plan 공유, 실험 여부 확인, 승인, 직전 재조회(ENABLED·18시), 새 plan 대조, apply, 재조회, plan 파일 삭제 기록
> 3. REQ-01·03 / OPS-03: apply 후 중지·시작 스케줄 조회 값
> 4. REQ-02 / OPS-07: 첫 검증 평일 18:05 이후 running, 18시 전후 StopInstances 없음, 00:00~00:05 스케줄러 StopInstances 1건
> 5. 교착 실험 작업 알림: 공유 노트 폴더는 현재 비어 있음(확인함). apply 직후 노트 작성, 메인 세션 보고, progress.md 기록
> 6. 지적 3의 증거 보존과 최종 결과 리뷰 재요청
>
> ### 이전 지적
> - 결과 리뷰는 이번이 최초라 이전 지적이 없습니다.
> - 계획 리뷰 3회차 개선 4건: 4번은 ADR 0015:39에 반영됨. 1·2·3번은 병합 후 절차라 아직 확인할 수 없습니다.
>
> ### 병합 전 판정
> 차단 지적 없음. 지적 1을 고치고 verify를 다시 통과시키면 PR 생성·병합 가능합니다. PR 본문에 "교착 실험 중이 아닐 때 병합, 병합 시 api 수십 초 재시작"을 적어야 합니다(Q-05). 지적 2·3은 병합을 막지 않고 완료를 막습니다. 지적 4~8은 선택입니다.
>
> 최종 판정: 수정 필요

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | infra/terraform/modules/ec2/schedule.tf(start 변경 없음) | 아래 plan에 start 변경 없음 | 코드 충족, apply 후 조회 미확인 |
| REQ-02 | schedule.tf stop `cron(0 0 * * ? *)` | 없음(apply 뒤 확인) | 미확인 |
| REQ-03 | schedule.tf | 아래 plan 0 add·1 change·0 destroy | plan 충족, apply 후 조회 미확인 |
| REQ-04 | ADR 0015 적용 절차 | 없음(병합 뒤 실행) | 미확인 |
| REQ-05 | server-offline-notice.tsx, 주석 2곳 | server-offline-notice.test.tsx 신규 검사, verify jest 32개 통과 | 충족 |
| REQ-06 | ADR 0015, 0011·0014 상태, README, runbook, docs/06, svg, 주석 | 검색 0건, verifier 내용 검토 | 지적 1 수정 필요 |

## 지적별 처리

1회차에서 나온 지적의 처리는 이 리뷰를 실패로 등록한 뒤 진행하고 다음 회차에 확인받는다.

- 1 [중요] ADR 0015 비용 합계: verifier가 제안한 대로 고침(별도 docs 커밋).
- 2 [중요] 병합 후 필수 확인 미실행: 병합·apply 뒤 실행하고 최종 리뷰를 받음.
- 3 [중요] plan 원문: 아래에 붙임. 병합 뒤 새 plan도 같은 형식으로 남김.
- 4 [개선] progress.md에 verify-002 백엔드는 UP-TO-DATE라고 적음.
- 5 [개선] 커밋 2~6을 잇달아 만들고 검사는 한 번에 함. 커밋마다 따로 확인한 기록은 없음(사실로 남김).
- 6 [개선] observability runbook에 병합 전 인스턴스 stopped 확인과 09:00 직전 회피를 적음.
- 7 [개선] 병합 전·후 `gh variable list`를 다시 조회함.
- 8 [개선, 범위 밖] docs/06 비용표는 이번 작업에서 고치지 않음.

## 완료 기준별 근거

terraform 실행 기록(비밀값 없음). 명령은 모두 `-chdir=infra/terraform/envs/dev`. `init -plugin-dir=<메인 체크아웃 provider> -backend-config=bucket=<상태 버킷>`, `fmt -check -recursive ../..`, `validate`, `plan -input=false -var-file=<메인 체크아웃 terraform.tfvars>`.

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

SEC-05: `git diff origin/main...` 비밀값 검색 0건, `git ls-files`의 tfvars 0건(2026-11-25).

## 성능 테스트 확인

불필요. 요청 경로·처리량·자원 사용 변화가 없고 켜져 있는 시간만 늘어난다.

## 발견한 문제

지적 1~3. 1은 수정하고, 2는 병합 뒤에 확인하고, 3은 위 기록으로 보완한다.

## 판정과 이유

1회차 실패. ADR 0015의 비용 설명이 계산과 다르고, 병합·apply 뒤에만 할 수 있는 필수 확인이 남았다.

## 확인하지 못한 부분

REQ-02·REQ-04 전체, REQ-01·03의 apply 후 조회, OPS-05 병합 재배포, OPS-07, 교착 실험 작업 알림.
