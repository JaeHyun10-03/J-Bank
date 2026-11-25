# 진행 기록

- 2026-11-23: 작업 생성(메인 폴더는 transfer-deadlock-experiment 진행 중이라 별도 체크아웃 j-bank-ops-hours, 브랜치 chore/ops-hours-midnight). 조사 후 Q-01~05 결정(새 ADR 0015, 병합 직후 메인이 승인받고 apply, 00시 중지까지 확인, 평일만, 주석 수정·실험 없을 때 병합).
- 계획 리뷰 3회: 1·2회차 수정 필요 → 3회차 통과 권고(plan-review.md). 3회차 개선 4건은 실행 절차로 지킴(plan-review.md 3회차 처리).
- 2026-11-25: 사용자가 커밋 계획을 승인("승인, 구현 시작"). origin/main 변경 없음(65d15e0), PR #19는 아직 열림. 중지 스케줄 조회 `cron(0 18 * * ? *)` ENABLED.
- start 1차 거부: plan-review.md의 `최종 판정` 줄이 회차마다 있어서. 지난 회차 줄을 `(N회차)`로 바꿈. 이때 verifier 응답을 요약해 놓고 "원문"이라 적은 잘못을 발견해, 세션 기록에서 3회차 응답 원문을 그대로 꺼내 교체(판정·지문·처리 내용은 그대로). start 성공.
- REQ-06 변경 전 대조: deploy-ec2.sh:3, backend-cd.yml:61, README:11·106, route.ts:28, server-offline-notice.tsx:7, server-status.ts:4, docs/06:63, svg 2개, observability runbook 10·76·85, compose:55, schedule.tf:1·2가 잡힘(계획 목록과 같음).
- 커밋 1~6 완료: 5a2e288(ADR) 286e158(schedule.tf) aeed01b(프론트) 1864b2f(테스트) 2f78d6a(주석) 59f5a27(문서·svg).
- REQ-06 변경 후: 같은 검색 0건. SEC-05: 비밀값 검색 0건, tfvars 추적 0건.
- terraform: fmt 0, validate 성공, plan `0 to add, 1 to change, 0 to destroy`, `module.ec2.aws_scheduler_schedule.stop`의 `schedule_expression`만 `cron(0 18 * * ? *)` -> `cron(0 0 * * ? *)`. IAM·인스턴스·시작 스케줄·State 변경 없음(REQ-01·03, SEC-07).
  - tfvars는 복사하지 않고 메인 체크아웃 파일을 `-var-file`로 읽음. 잠금 파일(.terraform.lock.hcl, gitignore)만 복사하고 provider는 메인 체크아웃의 것을 `-plugin-dir`로 씀. init 1차는 bucket 누락으로 실패, `-backend-config`를 넣어 성공.
- verify-001 통과(HEAD 59f5a27, 6개 모두 exit 0, 백엔드 테스트 211초). Docker CLI 링크(`/usr/local/bin/docker`)가 삭제된 OrbStack을 가리켜 깨져 있어, Docker Desktop(29.2.1)을 켜고 verify 명령의 PATH에만 `/Applications/Docker.app/Contents/Resources/bin`을 넣음. 커밋 7(devlog) 뒤 verify-002 통과.
- 다이어그램 2개를 이미지로 렌더해 글자 깨짐 없음 확인(weekday-ops "매일 24:00 중지", architecture "평일 9~24시").
- verify-002의 백엔드 테스트는 Gradle UP-TO-DATE였고 실제 실행은 verify-001(devlog 2개 파일만 차이).
- 결과 리뷰 1회차(병합 전): 수정 필요로 등록(review fail). 코드·설정 결함 없음. ADR 0015 비용 합계 설명을 고침(cb4e6b4), observability runbook에 병합 전 stopped 확인을 넣음(4d4003f). plan 원문은 review.md에 보존(12:54 KST). 병합·apply 뒤 확인 항목은 미확인.
- 수정 뒤 verify-003 통과(HEAD 4d4003f). REQ-06 검색 0건, 커밋 메시지 attribution 0건. `gh variable list` 다시 조회: `EC2_DEPLOY_READY`만 있음.
- 작업 폴더만 커밋하고 공용 상태 파일(활성 작업·목록)은 이 PR에 넣지 않음. 메인 폴더의 진행 중 작업과 같은 파일이라 병합 충돌을 피하려는 것. 완료 기록 때 함께 커밋함.
- 다음 행동(병합 뒤): 교착 실험 여부와 병합 확인 → backend-cd 결과·`/readyz` → plan 공유·승인 → 재조회 → `plan -out`(scratchpad) 대조 → apply → 조회 → plan 파일 삭제 → 실험 작업 알림(공유 노트·메인 세션) → 첫 평일 18:05 running → 00:00 StopInstances → verify·결과 리뷰 2회차 → complete.
