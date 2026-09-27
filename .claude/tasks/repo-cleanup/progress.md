# 진행 기록

- 브랜치 `chore/repo-cleanup` 생성, 작업 repo-cleanup 생성.
- 사용자 결정: Q-01 docs/roadmap, Q-02 개발일지·roadmap 본문 유지, Q-03 perf/run-transfer.sh, Q-04 terraform 잔여 폴더는 사용자 직접 삭제.
- 계획 리뷰 1회차: 수정 필요(SVG 실사용, test_workflow.py:294 누락, REQ-09 명령 거짓 통과 위험). 반영 완료.
- REQ-09 양성 대조(이동 전): 22건 검출, checks.json 포함.

다음 행동: 계획 리뷰 2회차 → 통과 시 start → Docker Desktop 기동 → 커밋 계획 순서대로 구현.

- 2026-09-27: 다른 대화(이력서·부하 테스트 계획 상담)에서 코드 변경 없이 멈춤. repo-cleanup 구현은 이 대화에서 진행하지 않음. 새 부하 테스트 작업을 만들지, repo-cleanup을 먼저 끝낼지 사용자 결정 대기.

## 구현 기록

- 커밋 1 `7dd5b2c` 프론트 README 교체. 커밋 2 `656d134` oidc.tf 주석 — `terraform fmt -check` 0, `validate` Success.
- 커밋 3 `3c96dd5` todo → docs/roadmap: rename 100%(W1~W7 변경 0줄), `ls todo` 실패. README·perf/README·docs/11·docs/10 트리 갱신.
- 커밋 4 `27fb866` Dockerfile 이동. `docker build -f apps/jbank-api/Dockerfile ... apps/jbank-api` 성공, 이미지 ID sha256:921461fd5e53c74e1582ae251a2d487a683d287acacd5e9bb2d36fd9b7c3f688. `docker compose --profile core --profile api config`가 context apps/jbank-api·dockerfile Dockerfile로 해석(exit 0), compose build api 성공(jbank-api:local sha256:0e02d717...). 참고: `--profile api` 단독 config는 postgres·redis 미정의로 실패 — 기존 구조(api가 core에 의존)이며 이번 변경과 무관.
- 커밋 5 `46ec79d` perf/run-transfer.sh: `bash -n` 통과, 인자 없이 실행 시 사용법 출력·exit 1. run-10m.sh 새 경로 호출.
- 문제: 커밋 5에서 계획대로 `docs/testing-policy.md:71` 경로를 고쳤는데 이 파일은 계획 지문 입력이라 계획 지문이 바뀜 → 코드 수정 Hook이 막힘. 요구사항·기준 내용은 불변(명령 경로 표기만). 계획 재검증과 start 재실행 필요, 계획 리뷰 한도 소진 상태라 사용자 허락 필요.

- 사용자 허락("추가 1회 허용") → 계획 리뷰 5회차 통과 권고 → start 재실행(exit 0). testing-policy diff는 71행 한 줄(`scripts/perf.sh` → `perf/run-transfer.sh`)뿐.
- 커밋 6 `66e84cb` tests → .claude/tests: ROOT `parents[2]`, 294행 문자열, checks.json, harness-ci(paths·명령), harness-guide, .claude/README(Hook 설명·트리). `python3 -m unittest discover -s .claude/tests -v` → 37 tests OK. `ls tests` 실패. 워크플로 5개 YAML 파싱 OK, checks.json JSON OK.
- 커밋 7 `a6daf97` README·docs/README·docs/10에서 bruno/수동 호출 컬렉션 표기 제거.
- 로컬 잔여물: `contracts/bruno`(rmdir), `.local-logs/pr-infra-ec2-body.md`, `.DS_Store` 3개 삭제 → `ls` 실패, `.DS_Store` 0개. 첫 삭제 시도는 `find -print0`의 `-pr`이 Hook의 `rm -r` 패턴에 걸려 거부됨 — 명령을 나눠 재실행(재귀 삭제 아님).
- REQ-09 검색(이동 후): 출력 없음, exit 1. 수동 확인: `perf.sh` 단독·`tests/`·infra Dockerfile 표기 잔존 없음(.claude/README 트리의 새 `tests/` 항목만).
- REQ-02: icon-chevron-up.svg 유지 확인. REQ-03: create-next-app 0건.

- verify-001 통과 → 결과 리뷰 1회차 수정 필요(devlog 미작성) → devlog 커밋 e7da1fb, verify-002 통과.
- 결과 리뷰 2회차 수정 필요: 18:39 외부 checkout으로 커밋 8개가 로컬 main에 쌓임. 사용자 선택으로 chore/repo-cleanup = e7da1fb, main = origin/main(51f4831)로 복구. verify-003 통과(snapshot 동일).
- 결과 리뷰 3회차 통과 권고 → review pass.

남은 일(작업 완료 밖): complete 후 작업 기록 커밋, 사용자에게 terraform 잔여 폴더 삭제 명령 안내, push·PR과 원격 CI 확인은 사용자 결정.
