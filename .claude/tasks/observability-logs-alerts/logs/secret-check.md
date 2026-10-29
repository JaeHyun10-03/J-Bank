# 비밀·개인정보 확인 (완료 기준 5, SEC-05, REQ-09)

## 1. 커밋 대상 파일 검색

시각: 2026-10-29T10:4xZ. 대상은 `origin/main` 대비 바뀐 파일(`.claude` 제외)과 새 문서 2개, 15개 파일이다.

패턴: `<사용자 메일 아이디>|@gmail\.com|SMTP_AUTH_PASSWORD=\S|app ?password\s*[:=]\s*\S`(대소문자 무시. 메일 아이디는 이 기록이 커밋되면 노출되므로 가렸다 — 결과 리뷰 1회차 I-2)

```text
검사 파일 15
docs/06_J-Bank_인프라아키텍처.md
docs/10_J-Bank_폴더구조.md
infra/compose/.env.example
infra/compose/docker-compose.prod.yml
infra/compose/docker-compose.yml
infra/compose/observability/alertmanager-entrypoint.sh
infra/compose/observability/alloy.alloy
infra/compose/observability/loki.yml
infra/compose/observability/prometheus.prod.yml
infra/compose/observability/prometheus.yml
infra/compose/observability/provisioning/datasources/datasources.yml
infra/compose/observability/rules/alerts.yml
infra/compose/observability/tests/alerts.test.yml
docs/adr/0014-observability-logs-alerts.md
docs/runbook/observability.md
일치: ['docs/runbook/observability.md:29: SMTP_AUTH_PASSWORD=Gmail 앱 비밀번호(2단계 인증 후 발급)']
```

일치 1건은 runbook의 입력 안내 문구(값 자리 설명)이며 실제 비밀번호가 아니다. 사용자 메일 주소는 저장소 전체 `git grep -nE "<사용자 메일 아이디>"`에서 0건(exit=1)이다(당시 이 작업 폴더는 미추적이라 검사 대상이 아니었다 — 2절에서 다시 검사). `infra/compose/.env.alertmanager`는 `.gitignore`의 `.env*` 규칙으로 추적되지 않는다(`git ls-files infra/compose`의 `.env` 계열은 `.env.example`, `perf/target.env.example`뿐).

로컬 compose의 alertmanager 메일 값(`dev@jbank.local`, `mailpit:1025`)은 로컬 전용 가짜 주소다.

## 2. 커밋할 파일 전체 재검사 (결과 리뷰 1회차 I-2)

대상: `origin/main` 대비 바뀐 추적 파일 + 이 작업 폴더 전체(미추적 포함) + `.claude/tasks/index.md`. 패턴은 1절과 같다(메일 아이디는 스크립트 안에서만 쓰고 출력에서 가렸다).

```text
시각 2026-10-29T12:37:11Z, HEAD c500c5b
검사 파일 46개 (origin/main 대비 변경 19개 + 작업 폴더 27개 + .claude/tasks/index.md)
.claude/tasks/active.json
.claude/tasks/index.md
.claude/tasks/observability-logs-alerts/evidence/changes.txt
.claude/tasks/observability-logs-alerts/evidence/checks.json
.claude/tasks/observability-logs-alerts/evidence/review-1.json
.claude/tasks/observability-logs-alerts/evidence/review-1.md
.claude/tasks/observability-logs-alerts/evidence/review.json
.claude/tasks/observability-logs-alerts/evidence/verify-001/changes.txt
.claude/tasks/observability-logs-alerts/evidence/verify-001/check-1.log
.claude/tasks/observability-logs-alerts/evidence/verify-001/checks.json
.claude/tasks/observability-logs-alerts/evidence/verify-002/changes.txt
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-1.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-2.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-3.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-4.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-5.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/check-6.log
.claude/tasks/observability-logs-alerts/evidence/verify-002/checks.json
.claude/tasks/observability-logs-alerts/logs/config-check.md
.claude/tasks/observability-logs-alerts/logs/local-e2e.md
.claude/tasks/observability-logs-alerts/logs/prod-measure.md
.claude/tasks/observability-logs-alerts/logs/prod-preflight.md
.claude/tasks/observability-logs-alerts/logs/promtool.md
.claude/tasks/observability-logs-alerts/logs/secret-check.md
.claude/tasks/observability-logs-alerts/plan-review.md
.claude/tasks/observability-logs-alerts/progress.md
.claude/tasks/observability-logs-alerts/review.md
.claude/tasks/observability-logs-alerts/state.json
.claude/tasks/observability-logs-alerts/task.md
docs/06_J-Bank_인프라아키텍처.md
docs/10_J-Bank_폴더구조.md
docs/adr/0014-observability-logs-alerts.md
docs/devlog/2026-10-29_관측스택로그와알림.md
docs/devlog/README.md
docs/runbook/observability.md
infra/compose/.env.example
infra/compose/docker-compose.prod.yml
infra/compose/docker-compose.yml
infra/compose/observability/alertmanager-entrypoint.sh
infra/compose/observability/alloy.alloy
infra/compose/observability/loki.yml
infra/compose/observability/prometheus.prod.yml
infra/compose/observability/prometheus.yml
infra/compose/observability/provisioning/datasources/datasources.yml
infra/compose/observability/rules/alerts.yml
infra/compose/observability/tests/alerts.test.yml
일치:
.claude/tasks/observability-logs-alerts/logs/config-check.md:59: SMTP_AUTH_PASSWORD=a | $ docker run --name chk4 -v /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compos
.claude/tasks/observability-logs-alerts/logs/secret-check.md:7: SMTP_AUTH_PASSWORD=\ | 패턴: `<사용자 메일 아이디>|@gmail\.com|SMTP_AUTH_PASSWORD=\S|app ?password\s*[:=]\s*\S`(대소문자 무시. 메일
.claude/tasks/observability-logs-alerts/logs/secret-check.md:26: SMTP_AUTH_PASSWORD=G | 일치: ['docs/runbook/observability.md:29: SMTP_AUTH_PASSWORD=Gmail 앱 비밀번호(2단계 인증 후 발급)']
docs/runbook/observability.md:29: SMTP_AUTH_PASSWORD=G | SMTP_AUTH_PASSWORD=Gmail 앱 비밀번호(2단계 인증 후 발급)
```

판정: 사용자 메일 아이디·`@gmail.com` 0건. 일치 4줄은 모두 실제 비밀이 아니다.
- `config-check.md:59`: amtool 검사에 넣은 더미 비밀번호(`a'b c"d`, 따옴표 처리 확인용).
- `secret-check.md:7·26`: 이 문서의 검색 패턴과 1절 결과 인용.
- `runbook:29`: 입력 안내 문구(값 자리 설명).

## 3. 수집 로그 표본 검색

- 2차(local-e2e): api·postgres 로그 390줄에서 JWT·주민번호·`password=` 0건.
- 4차(local-e2e): 새 무작위 테스트 비밀번호로 가입·실패 로그인·로그인·이체를 한 뒤 api·postgres 로그 309줄에서 비밀번호 원문·JSON `"password":` 키·`password=`·JWT·주민번호 모두 0건.
