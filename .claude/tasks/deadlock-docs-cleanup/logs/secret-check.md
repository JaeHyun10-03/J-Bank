# SEC-05 비밀값 검사

## 결과 리뷰 전 (2026-11-25 23:21:00 KST, HEAD 599cff1)

- 대상 25개 파일. 값 패턴 4종(`eyJ[A-Za-z0-9_-]{20,}\.|(XSRF-TOKEN|access_token)=[A-Za-z0-9._-]{16,}|[A-Za-z0-9._%+-]+@gmail\.com|110-[0-9]{6}-[0-9]`): 종료 코드 1, 0줄. 휴대폰 번호(`(^|[^0-9])010[0-9]{8}([^0-9]|$)`): 종료 코드 1, 0줄. stderr 0바이트.
- 검사 파일 목록:

```text
.claude/tasks/active.json
.claude/tasks/deadlock-docs-cleanup/evidence/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/checks.json
.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
.claude/tasks/deadlock-docs-cleanup/logs/cors-output.md
.claude/tasks/deadlock-docs-cleanup/logs/secret-check.md
.claude/tasks/deadlock-docs-cleanup/plan-review.md
.claude/tasks/deadlock-docs-cleanup/progress.md
.claude/tasks/deadlock-docs-cleanup/state.json
.claude/tasks/deadlock-docs-cleanup/task.md
.claude/tasks/index.md
docs/05_J-Bank_프론트엔드기술스택.md
docs/06_J-Bank_인프라아키텍처.md
docs/07_J-Bank_구현계획.md
docs/devlog/2026-11-25_교착실험후속정리.md
docs/devlog/2026-11-25_교착재현실험.md
docs/devlog/README.md
docs/planning/reconciliation-and-bulk-payroll-brief.md
docs/runbook/transfer-deadlock-experiment.md
perf/results/deadlock/README.md
perf/results/deadlock/prod/alert-mails.md
perf/results/deadlock/prod/cors-check.md
perf/run-deadlock.sh
```

## 결과 리뷰 2회차 전 (2026-11-25 23:27:48 KST, HEAD 31a6060)

- 대상 26개 파일. 값 패턴 4종(`eyJ[A-Za-z0-9_-]{20,}\.|(XSRF-TOKEN|access_token)=[A-Za-z0-9._-]{16,}|[A-Za-z0-9._%+-]+@gmail\.com|110-[0-9]{6}-[0-9]`): 종료 코드 1, 0줄. 휴대폰 번호(`(^|[^0-9])010[0-9]{8}([^0-9]|$)`): 종료 코드 1, 0줄. stderr 0바이트.
- 검사 파일 목록:

```text
.claude/tasks/active.json
.claude/tasks/deadlock-docs-cleanup/evidence/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/checks.json
.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
.claude/tasks/deadlock-docs-cleanup/logs/cors-output.md
.claude/tasks/deadlock-docs-cleanup/logs/secret-check.md
.claude/tasks/deadlock-docs-cleanup/plan-review.md
.claude/tasks/deadlock-docs-cleanup/progress.md
.claude/tasks/deadlock-docs-cleanup/review.md
.claude/tasks/deadlock-docs-cleanup/state.json
.claude/tasks/deadlock-docs-cleanup/task.md
.claude/tasks/index.md
docs/05_J-Bank_프론트엔드기술스택.md
docs/06_J-Bank_인프라아키텍처.md
docs/07_J-Bank_구현계획.md
docs/devlog/2026-11-25_교착실험후속정리.md
docs/devlog/2026-11-25_교착재현실험.md
docs/devlog/README.md
docs/planning/reconciliation-and-bulk-payroll-brief.md
docs/runbook/transfer-deadlock-experiment.md
perf/results/deadlock/README.md
perf/results/deadlock/prod/alert-mails.md
perf/results/deadlock/prod/cors-check.md
perf/run-deadlock.sh
```

## 결과 리뷰 2회차 전 (2026-11-25 23:28:58 KST, HEAD 5016c19)

- 대상 28개 파일. 값 패턴 4종(`eyJ[A-Za-z0-9_-]{20,}\.|(XSRF-TOKEN|access_token)=[A-Za-z0-9._-]{16,}|[A-Za-z0-9._%+-]+@gmail\.com|110-[0-9]{6}-[0-9]`): 종료 코드 1, 0줄. 휴대폰 번호(`(^|[^0-9])010[0-9]{8}([^0-9]|$)`): 종료 코드 1, 0줄. stderr 0바이트.
- 검사 파일 목록:

```text
.claude/tasks/active.json
.claude/tasks/deadlock-docs-cleanup/evidence/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-002/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-002/checks.json
.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
.claude/tasks/deadlock-docs-cleanup/logs/cors-output.md
.claude/tasks/deadlock-docs-cleanup/logs/secret-check.md
.claude/tasks/deadlock-docs-cleanup/plan-review.md
.claude/tasks/deadlock-docs-cleanup/progress.md
.claude/tasks/deadlock-docs-cleanup/review.md
.claude/tasks/deadlock-docs-cleanup/state.json
.claude/tasks/deadlock-docs-cleanup/task.md
.claude/tasks/index.md
docs/05_J-Bank_프론트엔드기술스택.md
docs/06_J-Bank_인프라아키텍처.md
docs/07_J-Bank_구현계획.md
docs/devlog/2026-11-25_교착실험후속정리.md
docs/devlog/2026-11-25_교착재현실험.md
docs/devlog/README.md
docs/planning/reconciliation-and-bulk-payroll-brief.md
docs/runbook/transfer-deadlock-experiment.md
perf/results/deadlock/README.md
perf/results/deadlock/prod/alert-mails.md
perf/results/deadlock/prod/cors-check.md
perf/run-deadlock.sh
```

## 작업 기록 커밋 전 (2026-11-25 23:31:58 KST, HEAD 5016c19)

- 대상 31개 파일. 값 패턴 4종(`eyJ[A-Za-z0-9_-]{20,}\.|(XSRF-TOKEN|access_token)=[A-Za-z0-9._-]{16,}|[A-Za-z0-9._%+-]+@gmail\.com|110-[0-9]{6}-[0-9]`): 종료 코드 1, 0줄. 휴대폰 번호(`(^|[^0-9])010[0-9]{8}([^0-9]|$)`): 종료 코드 1, 0줄. stderr 0바이트.
- 검사 파일 목록:

```text
.claude/tasks/active.json
.claude/tasks/deadlock-docs-cleanup/evidence/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/review-2.json
.claude/tasks/deadlock-docs-cleanup/evidence/review-2.md
.claude/tasks/deadlock-docs-cleanup/evidence/review.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-001/checks.json
.claude/tasks/deadlock-docs-cleanup/evidence/verify-002/changes.txt
.claude/tasks/deadlock-docs-cleanup/evidence/verify-002/checks.json
.claude/tasks/deadlock-docs-cleanup/logs/balance-check.md
.claude/tasks/deadlock-docs-cleanup/logs/cors-output.md
.claude/tasks/deadlock-docs-cleanup/logs/secret-check.md
.claude/tasks/deadlock-docs-cleanup/plan-review.md
.claude/tasks/deadlock-docs-cleanup/progress.md
.claude/tasks/deadlock-docs-cleanup/review.md
.claude/tasks/deadlock-docs-cleanup/state.json
.claude/tasks/deadlock-docs-cleanup/task.md
.claude/tasks/index.md
docs/05_J-Bank_프론트엔드기술스택.md
docs/06_J-Bank_인프라아키텍처.md
docs/07_J-Bank_구현계획.md
docs/devlog/2026-11-25_교착실험후속정리.md
docs/devlog/2026-11-25_교착재현실험.md
docs/devlog/README.md
docs/planning/reconciliation-and-bulk-payroll-brief.md
docs/runbook/transfer-deadlock-experiment.md
perf/results/deadlock/README.md
perf/results/deadlock/prod/alert-mails.md
perf/results/deadlock/prod/cors-check.md
perf/run-deadlock.sh
```
