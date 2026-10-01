# 진행 기록

작업 생성. 다음 행동: 요구사항과 완료 기준 작성.

## 2026-10-01 계획

- 끝낸 일: 운영 SSM 읽기 전용 확인(target down, 401, up=0). Q-01~03 사용자 결정(관리 포트 분리, 본 포트 /readyz·/livez, 기본 생성 비밀번호 제거). task.md 작성. verifier 계획 리뷰 1회차(수정 필요) 받아 중요-1~3·권고 반영.
- 남은 문제: 이 세션이 git worktree에서 돌지만 hook이 `$CLAUDE_PROJECT_DIR`(메인 체크아웃)의 workflow.py를 불러, verifier 호출이 메인 체크아웃의 async-credit 작업 상태 파일에 기록됨(메인 체크아웃에 수정된 상태 파일 2개). 이 작업 상태에는 호출 기록이 없어 start 조건(현재 계획 지문 호출 기록)을 정상 충족할 수 없다.
- 다음 행동: 하네스가 이 작업 폴더를 가리키는 세션에서 재개 → 계획 재리뷰(2회차) → 사용자 커밋 계획 승인 → start → 구현.
- 2026-10-01 사용자: task.md의 커밋 계획 8개 승인(다시 묻지 않음). 재개 방식은 "이 worktree를 프로젝트 디렉터리로 연 새 세션"으로 결정.
- 재개 세션 첫 행동: `python3 .claude/hooks/workflow.py status`로 blocked 확인 → task.md·plan-review.md 읽기 → verifier 계획 재리뷰 2회차(호출이 이 작업 상태에 기록되는지 status로 확인) → 통과 권고면 plan-review.md에 지문·판정 기록 → start → implement-task. Prometheus 재시작(SSM)은 실행 직전 사용자 확인.

## 2026-10-01 계획 재개(worktree 세션)

- 끝낸 일: 인계 반영. Q-04(없는 경로 404 COMMON_004, 커밋 2 추가)·REQ-11·AC-08, 2회차 권고1~7을 task.md에 반영. plan-review.md에 2회차 요약·3회차 원문 기록. 3회차 verifier 호출이 이 작업 상태에 정상 기록됨(hook 경로 문제 해소). 판정 통과 권고.
- 구현 시 반영할 3회차 권고: AC-01 테스트에 본 포트 미인증 `/actuator/health` 404, Host 위조는 Host 헤더를 실제 보내는 방식+관리 포트 200 대조, 커밋 2에서 `NoHandlerFoundException`도 404.
- 다음 행동: start → 커밋 계획 1번부터 구현. Prometheus 재시작(SSM)은 실행 직전 사용자 확인.

## 2026-10-01 구현

- 끝낸 일: 커밋 1(faac3a2 readyz·livez), 2(24392c5 없는 경로 404), 3(fbdd395 관리 포트 prometheus 허용).
- 발견: 관리 포트 미설정(같은 포트)이면 Boot가 `local.management.port`를 `local.server.port` 별칭으로 채운다(SameManagementContextConfiguration). task.md 재점검 항목·3회차 verifier의 "같은 포트면 값 없음" 전제가 틀렸고, ActuatorSamePortIntegrationTest가 본 포트 미인증 prometheus 200을 잡았다. 매처에 "관리 포트 != 본 포트" 조건을 추가해 401로 고침. REQ-03·AC-02 자체는 그대로라 task.md는 바꾸지 않고 결과 리뷰에 보고.
- 발견: 테스트는 기본으로 지표 내보내기가 꺼져 prometheus 엔드포인트가 없다. 두 통합 테스트에 @AutoConfigureObservability 추가.
- 다음 행동: 커밋 4(기본 생성 비밀번호 제거) → 5 프론트 → 6 infra → verify.
- 커밋 4(4807e79 기본 사용자 제외), 5(063f28e 프론트 /readyz), 6(4860193 운영 compose·Prometheus 9095), 7(41fdf45 문서·ADR 0013) 완료. logs/compose-config.txt 저장.
- 환경: worktree에 apps/frontend/node_modules가 없어 메인 체크아웃의 node_modules(package-lock 동일)를 심볼릭 링크로 연결(gitignore 대상).
- 다음 행동: verify → review-begin → verifier 결과 리뷰 → 개발일지·작업 기록 커밋 → PR.
- verify-001 통과. 결과 리뷰 1/3: verifier 통과 권고(배포 전 범위), 차단·중요 없음. REQ-09 운영 증거가 없어 review fail로 등록(review.md). 개선-4 반영(9c385cc), 개발일지(68fae7c).
- 남은 일: PR 생성·사용자 병합 → backend-cd 배포 → SSM으로 `docker compose restart prometheus`(실행 직전 사용자 확인) → 운영 확인을 logs/prod-check.md에 기록 → verify → 결과 리뷰 2/3 → review pass → complete → 후속 PR(커밋 10·11).
