# 계획 리뷰

## 1회차

회차 계획 지문: 771cf36c374d513a6843ac640fe31cc65934ebad4771a80daebcee60d3fc090a (2026-10-29T07:18:23Z 호출)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 결과: observability-logs-alerts (1회차)
>
> **판정: 수정 필요.** 차단 1건, 중요 5건이 있습니다. 가장 큰 문제는 로컬 Prometheus가 지금 구성으로는 api 지표를 아예 못 가져온다는 점입니다. 이 상태로는 완료 기준 3의 "api 다운 → 해제 메일" 시나리오가 성립하지 않습니다.
>
> - 대상: `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/task.md` (명세 상태: 확정, 미추적 파일). 별도 버전 표기는 없습니다. 구현 전이라 코드 변경도 없습니다.
> - 읽은 자료: docs/testing-policy.md, docs/testing/devops.md, docs/testing/security.md, task.md, progress.md, 요청하신 참고 파일 전부(compose 두 파일, perf target compose, prometheus.yml·prometheus.prod.yml, datasources.yml, jbank-transfer.json, .env.example, Caddyfile, deploy.sh, boot.sh, sync-latest.sh, bottleneck-analysis.md, TransferController).
> - 추가로 읽은 자료: SecurityConfig.java, PrometheusScrapeRequestMatcher.java, application.yml, application-dev.yml, application-local.yml, ADR 0013, AccountRepository의 `db.lock.wait` 부분, .gitignore, checks.json, infra-cd.yml.
> - 확인하지 못한 범위: 아무것도 실행하지 않았습니다. Alertmanager·Loki의 실제 검증 동작은 공식 동작에 대한 지식으로 판단했습니다. 구현 단계에서 `amtool`·`loki -verify-config`로 확인해야 합니다.
>
> ---
>
> ### 차단
>
> **B-1. 로컬 Prometheus가 api 지표를 못 가져옵니다(401). 로컬 api 다운 시나리오가 성립하지 않습니다.**
> - 기준: REQ-06, REQ-08, OPS-04, OPS-07, 완료 기준 3.
> - 근거:
>   - `infra/compose/observability/prometheus.yml:8`은 `host.docker.internal:8080/actuator/prometheus`를 수집합니다.
>   - 로컬 api 컨테이너(`docker-compose.yml:37-45`)에는 `MANAGEMENT_SERVER_PORT`가 없습니다. dev·local 프로필에도 관리 포트 설정이 없습니다.
>   - `PrometheusScrapeRequestMatcher.java:33-36`은 관리 포트가 본 포트와 다를 때만 인증 없이 허용합니다. 그 외에는 `SecurityConfig.java:64` `anyRequest().authenticated()`로 401이 납니다.
>   - ADR 0013의 21행과 38행도 "관리 포트 미설정이면 401"이라고 적고 있습니다.
> - 영향:
>   - 로컬 `up{job="jbank-api"}`이 api가 정상이어도 늘 0입니다. 그래서 api 다운 알림이 기동 직후부터 계속 firing됩니다.
>   - 그러면 "2분 미만이면 알림 없음"과 "다시 띄우면 해제 메일"을 관찰할 수 없습니다.
>   - 이체 5xx·락 대기 지표도 로컬에 쌓이지 않습니다. 후속 교착 실험의 "알림 → 로그" 기반도 무너집니다.
>   - 조사한 사실 11행이 이 점을 놓쳤습니다.
> - 필요한 수정:
>   - 로컬 수집 경로를 정해 범위와 설계 결정에 넣어야 합니다. 예: 로컬 api 컨테이너에 `MANAGEMENT_SERVER_PORT=9095`를 주고 대상을 `api:9095`로 바꿉니다.
>   - bootRun으로 띄운 api를 수집할지도 함께 정해야 합니다. D-04와 일관되게 맞춥니다.
>   - e2e 절차에 사전 조건을 추가해야 합니다. api를 멈추기 전에 `up==1`이고 api 다운 알림이 inactive인지 확인합니다.
>
> ### 중요
>
> **I-1. 메일 변수가 비었을 때의 기대 결과가 지금 문구대로는 나올 수 없습니다.**
> - 기준: REQ-09, OPS-03, 완료 기준 5.
> - 근거:
>   - Alertmanager는 email 수신자의 `to`나 SMTP smarthost가 비어 있으면 설정 검증에 실패해 기동하지 않습니다. `unless-stopped`면 재시작을 반복합니다.
>   - 그래서 "Alertmanager 로그에 발송 실패가 남는다"는 결과는 나오지 않고, 설정 오류로 반복 재시작하게 됩니다.
>   - OPS-03 행에는 "비움·더미 값"이 섞여 있어 시나리오도 하나로 정해져 있지 않습니다.
> - 영향: 통과 기준이 모호합니다. Alertmanager가 죽어 있으면 REQ-08의 "Grafana에서 발생 중인 알림 보기"도 함께 깨집니다.
> - 필요한 수정: 변수가 비었을 때의 동작을 정해 REQ-09에 적어야 합니다. 그리고 비움과 더미 두 경우를 나눠 관찰 가능한 기대 결과로 바꿉니다.
>   - 예 1: 기동 스크립트가 경고를 로그에 남기고, 메일 없는 수신자로 기동합니다.
>   - 예 2: 명확한 오류로 종료하고 재시작 반복을 막습니다.
>
> **I-2. SMTP 비밀번호가 api 컨테이너 환경변수로 들어갑니다.**
> - 기준: SEC-07, SEC-05.
> - 근거: `docker-compose.prod.yml:26`에서 api가 `env_file: .env`를 씁니다. D-02대로 메일 변수를 운영 `.env`에 두면 api 컨테이너에도 Gmail 앱 비밀번호와 받는 주소가 주입됩니다.
> - 영향: Gmail 앱 비밀번호는 계정 전체에 접근할 수 있습니다(IMAP 포함). 최소 권한 원칙에 어긋나고, api가 침해되면 개인 메일 계정까지 노출됩니다.
> - 필요한 수정:
>   - Alertmanager 전용 파일로 분리해야 합니다. 예: `.env.alertmanager`, chmod 600.
>   - 이름에 주의해야 합니다. `.gitignore`의 `.env*` 패턴에 걸리는 이름이어야 하고, `alertmanager.env`는 걸리지 않습니다.
>   - 확인 항목을 추가합니다: `docker inspect api`의 환경변수에 메일 변수가 0건이어야 합니다.
>
> **I-3. 도커 소켓 `:ro`를 보호 수단으로 적은 근거가 사실과 다릅니다.**
> - 기준: SEC-07, D-03.
> - 근거: 유닉스 소켓을 `:ro`로 마운트해도 도커 API 호출(쓰기 포함)은 막히지 않습니다. 소켓에 접근할 수 있으면 사실상 호스트 root 권한입니다. SEC-07 행은 "Alloy만 소켓 마운트(`:ro`)"를 통과 기준으로 삼고 있습니다.
> - 영향: 공개 서비스 호스트에서 실제 위험이 과소평가됩니다.
> - 필요한 수정:
>   - 위험을 정확히 적고 사용자에게 수용 여부를 받아야 합니다.
>   - 또는 대안을 비교합니다. 예: GET containers/logs만 허용하는 소켓 프록시, 또는 json 로그 파일을 읽기 전용으로 읽는 방식(컨테이너 이름 라벨을 잃는 손해가 있음).
>
> **I-4. 메모리 상한 값과 전체 증가량이 정해지지 않았습니다.**
> - 기준: OPS-06, REQ-10, Q-05.
> - 근거:
>   - bottleneck-analysis.md 25행: OOM 킬 없이 호스트 전체 스래싱으로 50분 무응답이 있었습니다. 관측 도구가 없는 perf 대상에서도 가용 메모리가 60~67MB까지 떨어졌습니다.
>   - 계획에는 컨테이너별 상한 숫자도, 합계도 없습니다. 지금 REQ-10은 어떤 값이든 "상한 있음"이면 통과합니다.
> - 영향: 상한은 그 컨테이너 안의 폭주만 막습니다. 새 컨테이너들의 합이 남은 여유를 넘으면 api가 있는 호스트 전체가 다시 스래싱할 수 있습니다. Q-05의 "그 컨테이너만 재시작"이 전제한 상황과 다릅니다. devops.md도 "수치 목표 미정은 계획 미완료"라고 합니다.
> - 필요한 수정:
>   - loki·alloy·postgres-exporter·alertmanager 각각의 상한 값과 합계를 정합니다.
>   - 그 합계를 알려진 여유 메모리와 비교한 근거를 적습니다.
>   - 배포 후 중단·롤백 조건을 정합니다. 예: 유휴 가용 메모리가 X MB 미만이면 loki를 내림.
>   - 합계가 여유를 넘을 수 있으면 사용자에게 다시 물어야 합니다.
>
> **I-5. 운영 반영 시의 영향과 롤백 설명이 실제와 다릅니다.**
> - 기준: OPS-05.
> - 근거와 영향:
>   - postgres에 `command: ... log_lock_waits=on`을 추가하면 `up -d` 때 postgres 컨테이너가 다시 만들어집니다. 그동안 api의 DB 연결이 끊기고 진행 중인 요청이 실패합니다.
>   - OPS-05의 "새 서비스만 내리면 기존 상태"는 틀립니다. postgres·prometheus 설정과 grafana provisioning도 바뀌기 때문입니다.
>   - main 병합 뒤에는 수동 `up -d` 전이라도 boot 때 `sync-latest.sh:14`가 저장소를 바꿉니다. 그러면 기존 prometheus·grafana가 재시작하면서 새 설정 파일을 읽는 중간 상태가 생깁니다.
> - 필요한 수정: runbook 범위에 다음을 적어야 합니다.
>   - postgres 재기동 구간과 api 영향
>   - 기존 서비스 설정까지 포함한 롤백 절차
>   - 중간 상태에서 prometheus가 새 설정으로 정상 기동하는지 확인하는 절차(완료 기준 5의 로컬 prod 기동에서 함께 확인 가능)
>
> ### 권고
>
> 1. **REQ-02 보관 설정:** Loki는 `limits_config.retention_period`만으로는 로그를 지우지 않습니다. compactor의 `retention_enabled: true`(와 `delete_request_store`)가 필요합니다. 정적 확인 항목에 두 키를 모두 명시하세요.
> 2. **REQ-08 Grafana 알림 보기:** 지금은 데이터소스 상태 확인뿐입니다. e2e에서 알림이 firing인 동안 Grafana API로 발생 중인 알림 조회 결과를 남기세요.
> 3. **REQ-05·07 지표 이름 확인:** B-1을 고친 뒤, 규칙이 쓰는 지표가 로컬 Prometheus에 실제로 있는지 확인하세요. 예: `http_server_requests_seconds_count{uri=~"/api/v1/transfers.*"}`, `db_lock_wait_seconds_max`. promtool의 합성 데이터로는 지표 이름·라벨 오타를 잡지 못합니다.
> 4. **교착 규칙 비교식:** `increase(...[5m])`는 외삽 때문에 값이 정수가 아닐 수 있습니다. `>= 1`보다 `> 0`이 안전합니다(사용자 기준 "1 이상 증가"와 같은 뜻).
> 5. **메일 주기:** `group_wait`·`group_interval`·`repeat_interval` 값을 명시하세요. 기본 반복 4시간이면 같은 알림 메일이 다시 옵니다.
> 6. **SEC-05 로그 표본:** postgres 로그(교착 DETAIL·STATEMENT)도 표본 검사에 넣으세요. 공개 grafana.j-bank.site에서 로그가 보이게 된다는 점도 접근 정책으로 기록하세요.
> 7. **postgres-exporter 계정:** 지금 안은 superuser `jbank`를 쓰는 perf 구성과 같습니다. pg_monitor 전용 계정을 쓰거나, 수용한 이유를 SEC-07에 기록하세요.
> 8. **부팅 시 오알림:** 운영 부팅 때 api 기동이 2분을 넘으면 평일마다 api 다운 오알림이 올 수 있습니다. boot.sh는 기동 대기를 600초까지 허용합니다. 알려진 위험으로 기록하거나 기존 기동 시간을 근거로 확인하세요.
> 9. **운영 구성 사전 검증:** 완료 기준 5의 로컬 prod 기동에서 Loki 로그 조회와 `docker inspect`(상한·포트)도 함께 확인하세요. 운영 구성의 REQ-01·10·11이 배포 전에 `config -q` 이상으로 검증됩니다.
> 10. **SEC-06 제외:** 이번 작업에서 가장 높은 권한을 받는 이미지가 Alloy입니다. "미검증(수용)"으로 적고 통과로 보지 않는다는 점을 분명히 하세요.
>
> ---
>
> ### 질문·결정 구분 (확인 요청 1)
> - Q-01~Q-08(사용자 결정)과 D-01~D-04(메인 선택·근거)는 명확히 나뉘어 있습니다.
> - 다만 동작이나 위험을 바꾸는데 확인·기록되지 않은 선택이 있습니다.
>   - 로컬 수집 경로 (B-1)
>   - 메모리 상한 값 (I-4)
>   - 소켓 권한 위험의 수용 여부 (I-3)
>   - 메일 변수가 비었을 때의 동작 (I-1)
>   - 비밀 파일 위치 (I-2. D-02의 공용 `.env`는 api에도 들어갑니다)
> - 이 중 I-3과 I-4는 사용자에게 물어야 합니다. 나머지는 메인 결정으로 근거를 남기면 됩니다.
>
> ### REQ 연결 (확인 요청 2)
> - 모든 REQ가 완료 기준 1~6 중 하나 이상과 명령·증거 위치에 연결돼 있습니다.
> - 문제가 있는 REQ:
>   - REQ-06: 로컬 e2e가 B-1 때문에 관찰할 수 없습니다.
>   - REQ-09: 기대 결과가 실현 불가능하거나 모호합니다 (I-1).
>   - REQ-10: 통과 기준에 숫자가 없습니다 (I-4).
>   - REQ-02: 정적 확인이 불충분합니다 (권고 1).
>   - REQ-08의 Grafana 부분: 증거가 약합니다 (권고 2).
> - REQ-05·07을 promtool로만 판정하는 이유는 적혀 있어 수용 가능합니다. 권고 3을 보완하면 더 좋습니다.
>
> ### 적용 제외의 타당성 (확인 요청 3)
> - 타당: OPS-02(CI 변경 없음), OPS-08, SEC-01~04, SEC-08, 성능·프론트·AI·전체 흐름 해당 없음.
> - 백엔드 해당 없음은 앱 코드 기준으로는 타당합니다. 단 postgres 재기동 영향은 I-5에서 다뤄야 합니다.
> - OPS-05를 완료 기준에서 제외하고 배포 후 점검으로 넘긴 것 자체는 타당합니다. 다만 롤백 설명은 I-5에 따라 고쳐야 합니다.
> - SEC-07의 근거는 I-3 때문에 부적절합니다.
> - 참고로 checks.json에는 promtool·compose 검사가 없습니다. 이 검사들은 수동 로그로만 증거를 남기게 되니 결과 리뷰에서 원본 출력을 확인하겠습니다.
>
> ### 조사한 사실 대조 (확인 요청 4)
> - 일치하는 항목:
>   - 운영 서비스 6개, `api:9095` 단일 수집 대상
>   - 로컬 프로필 구성
>   - postgres-exporter는 perf target에만 있음
>   - 대시보드의 지표 이름(`http_server_requests_seconds_*`, `db_lock_wait_seconds_*`, `hikaricp_*`)
>   - 이체 URI 두 개
>   - 가설 E 수치(60~67MB, 181~204MB, 50분 무응답)
>   - deploy.sh는 api만 교체, boot.sh·sync-latest.sh 동작, infra-cd는 Terraform만 다룸
>   - `.env`는 gitignore 대상
> - 누락·부정확:
>   - 11행: 로컬 수집이 401로 실패한다는 사실이 빠졌습니다 (B-1).
>   - 15행: boot 시 저장소 갱신 후 기존 컨테이너가 새 설정을 읽는 중간 상태가 빠졌습니다 (I-5).
>
> ### 이전 지적
> 최초 리뷰라 없습니다.
>
> 최종 판정: 수정 필요

### 지적 처리

- B-1: D-04(로컬 api 관리 포트 9095, 대상 `api:9095`, healthcheck 이동), 완료 기준 3 사전 조건 추가.
- I-1: D-03, REQ-09를 "없음/틀림" 두 경우로 분리, 완료 기준 4.
- I-2: D-02 `.env.alertmanager` 분리, api 환경변수 0건 확인 추가.
- I-3: 사용자 결정 Q-09(소켓 프록시), D-05, REQ-11·SEC-07 갱신.
- I-4: 운영 유휴 메모리 측정(944MB), 사용자 결정 Q-10(t3.small 유지, 600MB 미만이면 로그 수집 3종 내림), D-06 상한 값 합계 368MB.
- I-5: D-08(rule_files glob), REQ-12·완료 기준 4 중간 상태, 배포 후 점검에 postgres 재생성 영향·롤백(`--remove-orphans`) 기록.
- 권고 1 REQ-02 키 두 개 / 2 Grafana 알림 조회 / 3 지표 존재 확인 / 4 `> 0` / 5 D-09 / 6 SEC-05 postgres 로그·접근 정책 / 7 D-07 수용 / 8 운영 부팅 `up` 실측 15~30초(OPS-04) / 9 완료 기준 4 / 10 SEC-06 미검증(수용) 명시.

## 2회차

회차 계획 지문: c15def45eb28dcdcf222b7765f2d540f2a05e2ce2ee2f269a950d990d279ed89 (2026-10-29T07:50:48Z 호출)
회차 판정: 수정 필요

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 결과: observability-logs-alerts (2회차)
>
> 판정은 **수정 필요**입니다. 1회차 지적 16건(차단 1, 중요 5, 권고 10)은 task.md에 모두 반영됐습니다. 다만 이번에 처음 읽은 배포 스크립트와 기존 측정 자료에서 새로운 중요 결함 3건이 나왔습니다.
> - 병합 순간 운영 postgres가 계획에 없던 시점에 재생성됩니다.
> - 메모리 기준이 부팅 배치 때의 최고 사용량을 보지 않습니다.
> - 소켓 프록시가 허용하는 읽기 범위가 사용자 답변(Q-09)보다 넓습니다.
>
> ### 검증 대상과 범위
> - **모드·대상:** 계획 리뷰 2회차. task.md(명세 상태: 확정, 미추적 파일, 버전 표기 없음)와 plan-review.md(1회차 원문·처리 내역).
> - **읽은 자료:**
>   - docs/testing-policy.md, docs/testing/devops.md, docs/testing/security.md, task.md, plan-review.md, progress.md
>   - infra/compose: docker-compose.yml, docker-compose.prod.yml, observability/prometheus.yml·prometheus.prod.yml, .env.example, deploy.sh, run-batch.sh, host/boot.sh, host/sync-latest.sh, perf/docker-compose.target.yml
>   - 앱: application.yml·-dev·-prod·-local.yml, PrometheusScrapeRequestMatcher.java, AccountRepository.java의 `db.lock.wait` 부분
>   - 문서·설정: ADR 0011·0013, bottleneck-analysis.md, user_data.sh.tpl, .gitignore
>   - 이번에 새로 읽음: .github/workflows/backend-cd.yml, .github/scripts/deploy-ec2.sh
> - **적용 기준 ID:** OPS-01~08, SEC-01~08(task.md의 적용·제외 표 기준).
> - **확인하지 못한 범위:**
>   - 아무것도 실행하지 않았습니다.
>   - 운영 메모리 측정치와 운영 부팅 `up` 실측은 원본 출력이 작업 폴더에 없어서 읽기로 확인할 수 없습니다. 작업 폴더에는 task.md, plan-review.md, progress.md, state.json만 있습니다.
>   - Compose의 의존 서비스 재생성, socket-proxy 경로 허용 범위, Alloy의 inspect 호출 여부는 공식 동작에 대한 지식으로 판단했습니다.
>
> ---
>
> ### 1. 1회차 지적 해소 여부
>
> | 지적 | 판정 | 근거 |
> | --- | --- | --- |
> | B-1 로컬 401 | 해결 | D-04(관리 포트 9095, 대상 `api:9095`, healthcheck 이동)와 완료 기준 3의 사전 조건(`up==1`, 알림 inactive). application.yml·matcher 코드상 `MANAGEMENT_SERVER_PORT`로 동작 가능 |
> | I-1 메일 변수가 비었을 때 | 해결 | D-03, REQ-09를 "없음/틀림" 두 경우로 분리, 완료 기준 4, `amtool` 두 경우 검사 |
> | I-2 SMTP 비밀이 api에 들어감 | 해결 | D-02 `.env.alertmanager`(`.gitignore:42` `.env*`에 걸림), api 환경변수 0건 검사 |
> | I-3 소켓 `:ro` | 해결 | Q-09 사용자 결정, D-05. 단 허용 범위는 새 지적 N-3 |
> | I-4 메모리 상한 | 해결 | Q-10, D-06 숫자(합계 368MB, 계산 맞음), 600MB 기준. 단 측정 시점은 새 지적 N-2 |
> | I-5 운영 반영 영향·롤백 | 기록 항목은 해결 | D-08, REQ-12, 배포 후 점검에 postgres 재생성 영향과 롤백을 적음. 단 실제 배포 경로가 빠져 새 지적 N-1 |
> | 권고 1~10 | 모두 반영 | 1 키 두 개 / 2 Grafana API 조회 / 3 지표 존재 확인 / 4 `> 0`(Q-02 "1 이상"과 같은 뜻) / 5 D-09 / 6 SEC-05 postgres 로그·접근 정책 / 7 D-07 수용 기록 / 8 운영 부팅 실측 15~30초(원본 없음, 권고 R-2) / 9 완료 기준 4 / 10 SEC-06 미검증(수용) |
>
> ---
>
> ### 중요
>
> **N-1. 병합 순간 backend-cd가 운영 postgres를 계획 밖 시점에 재생성합니다. 조사한 사실 16행과 롤백 절차가 실제 배포 경로와 다릅니다.**
> - **기준:** OPS-05, REQ-12. 조사한 사실 16행, 배포 후 점검 96·99행.
> - **근거:**
>   - `.github/workflows/backend-cd.yml:6-8`: paths에 `infra/compose/**`가 있습니다. 이 PR을 병합하면 backend-cd가 바로 돕니다.
>   - `.github/scripts/deploy-ec2.sh:34`: 인스턴스가 켜져 있으면 `git reset --hard origin/main` 후 `deploy.sh`를 실행합니다.
>   - `deploy.sh:14`: `docker compose -f docker-compose.prod.yml up -d api`
>   - api는 `depends_on: postgres`입니다(`docker-compose.prod.yml:39-41`).
>   - Compose v2의 `up <service>`는 `--no-deps`가 없으면 설정이 바뀐 의존 서비스도 재생성합니다. postgres에 `command: ... log_lock_waits=on`이 추가되면 설정이 바뀐 것으로 봅니다.
>   - 인스턴스가 꺼져 있을 때 병합하면 다음 부팅의 `sync-latest.sh:35`가 새 `:latest` revision을 보고 같은 `deploy.sh`를 실행합니다.
>   - ADR 0011:46: 호스트에서 되돌린 상태는 다음 부팅·배포의 `reset --hard origin/main`으로 덮입니다. 롤백을 유지하려면 main에 롤백 커밋을 올려야 합니다.
> - **영향:**
>   - 계획은 postgres 재생성이 사용자가 고른 시점(수동 `up -d`, 트래픽 적은 때)에 일어난다고 봅니다. 실제로는 병합 즉시(평일 09~18시) 또는 다음 부팅 때 일어납니다.
>   - 기존 api 컨테이너가 아직 살아 있는 동안 DB가 먼저 내려가서 진행 중인 요청이 실패할 수 있습니다. 커밋된 데이터 유실은 없습니다.
>   - "중간 상태"도 부팅 때만이 아니라 병합 즉시 시작됩니다.
>   - 99행 롤백("이전 커밋 설정으로 되돌려 `up -d --remove-orphans`")은 다음 배포·부팅에 덮입니다.
> - **필요한 수정:**
>   - 조사한 사실에 backend-cd 경로와 의존 서비스 재생성을 적습니다.
>   - 병합 시점·방식을 정합니다. 예: (a) 인스턴스가 꺼진 뒤 병합해 다음 부팅 배치 전에 재생성되는 것을 수용, (b) `deploy.sh`를 `up -d --no-deps api`로 바꿔 범위를 늘림, (c) 병합 즉시 재생성을 수용하고 공지. 동작이 바뀌는 선택이므로 사용자에게 확인을 권합니다.
>   - 완료 기준 4의 중간 상태 확인에 "새 compose로 `up -d api` 실행 → 재생성되는 컨테이너 기록"을 추가합니다.
>   - runbook 롤백을 "main에 revert 커밋 + 호스트 `up -d --remove-orphans`"로 고칩니다. revert 병합도 backend-cd를 다시 돌린다는 점을 함께 적습니다.
>
> **N-2. 600MB 기준과 측정 시점이 알려진 사고 원인인 부팅 배치 때의 최고 메모리 사용량을 보지 않습니다.**
> - **기준:** OPS-06, REQ-10, Q-10, D-06.
> - **근거:**
>   - `bottleneck-analysis.md:25`: 배치 JVM(512MB)이 뜨자 가용 메모리가 807MB에서 127MB로, 약 680MB 줄었습니다. 그 뒤 스래싱으로 50분 동안 응답이 없었습니다.
>   - `run-batch.sh:18`: 배치는 api와 같은 설정(`JAVA_TOOL_OPTIONS -Xmx512m`)의 별도 JVM으로 매 부팅 직후 돕니다(ADR 0011:24).
>   - 944MB 측정은 07:39Z(16:39 KST)로 배치가 끝난 뒤의 유휴 값입니다.
>   - 배포 후 점검 97행도 "반영 후 5분 뒤 유휴"만 잽니다.
>   - D-06의 "실제 사용량은 상한보다 작을 것으로 보고"는 근거가 없는 가정입니다.
> - **영향:**
>   - 유휴 가용 메모리가 600~700MB면 기준은 통과합니다. 그런데 부팅 배치(약 600~680MB) 때는 가용 메모리가 0 근처까지 떨어질 수 있습니다. 가설 E의 50분 무응답이 평일 아침마다 재현될 위험이 있습니다.
>   - 부팅 때는 Loki·Alloy가 기동 로그를 한꺼번에 받으므로 유휴 때보다 더 씁니다.
>   - Q-10을 결정할 때 배치 최고 사용량을 사용자에게 알렸는지 task.md에 기록이 없습니다.
> - **필요한 수정:** 이 정보를 사용자에게 설명하고 다음 중 하나를 고르게 합니다.
>   - (a) 배포 후 점검에 "배치 1회 실행 중 최저 가용 메모리" 측정과 그 기준값(사용자 결정)을 추가합니다.
>   - (b) 600MB를 배치 여유분으로 정한 것이라면 그 근거를 적고 남는 위험을 수용으로 기록합니다.
>
> **N-3. socket-proxy가 허용하는 읽기 범위와 네트워크 접근이 Q-09("목록·로그 읽기만")보다 넓습니다. 테스트도 쓰기 거부만 봅니다.**
> - **기준:** SEC-07, REQ-11, Q-09, D-05.
> - **근거:**
>   - D-05는 "컨테이너 조회(GET)만 연다"고 적었습니다. 일반적인 소켓 프록시의 컨테이너 허용 설정은 `GET /containers/{id}/json`(inspect)과 `/containers/{id}/archive`(컨테이너 안 파일 읽기)까지 엽니다.
>   - api 컨테이너는 `env_file: .env`로 `DB_PASSWORD`·`JWT_SECRET`·`PII_ENCRYPTION_KEY`·`RESIDENT_REG_NO_HASH_KEY`를 받습니다(`docker-compose.prod.yml:26`, `.env.example`). inspect 응답에는 이 값이 평문으로 나옵니다.
>   - 계획에 네트워크 분리 언급이 없습니다. 그러면 socket-proxy는 기본 compose 네트워크에 붙습니다. api·caddy·grafana(외부 공개)·postgres-exporter 등 모든 컨테이너가 `socket-proxy:2375`에 닿습니다.
> - **영향:** 같은 네트워크의 컨테이너 하나만 침해돼도 운영 비밀 전체(개인정보 암호화 키 포함)를 읽을 수 있습니다. "외부 포트 없음"과 "POST 거부"만으로는 Q-09가 의도한 최소 권한이 확인되지 않습니다.
> - **필요한 수정:**
>   - socket-proxy와 Alloy만 붙는 전용 내부 네트워크를 D-05·REQ-11에 넣습니다.
>   - 테스트를 추가합니다: 다른 컨테이너(예: api나 grafana)에서 `socket-proxy:2375`에 닿지 않아야 합니다.
>   - Alloy 로그 수집에 inspect가 필요하면 "inspect로 환경변수가 보인다"는 남는 위험을 SEC-07·ADR에 기록합니다. 또는 경로 단위로 허용 목록을 줄 수 있는 프록시를 고릅니다.
>   - Q-09 답변 범위를 넘으므로 남는 위험은 사용자에게 알립니다.
>
> ### 권고
> - **R-1. exporter 접속 주소(D-07):** perf 구성(`perf/docker-compose.target.yml:135`)처럼 `DATA_SOURCE_NAME` URL에 `${DB_PASSWORD}`를 넣으면 비밀번호의 `/`·`+`·`=`·`@`에서 주소 해석이 깨집니다. `.env.example`도 base64 생성을 안내합니다. `DATA_SOURCE_URI`+`DATA_SOURCE_USER`+`DATA_SOURCE_PASS`를 쓰거나, 사전 검증의 더미 비밀번호에 특수문자를 넣으세요.
> - **R-2. 운영 측정 원본 없음:** 메모리(944MB, 컨테이너별 사용량)와 부팅 `up` 15~30초는 OPS-04·06 판정의 근거입니다. 그런데 요약만 있고 명령·시각·SSM 명령 ID·원본 출력이 `logs/`에 없습니다. 이번 리뷰에서도 확인할 수 없었습니다. 원본을 보존하세요.
> - **R-3. 지표 존재 확인 조건:** 미인증 이체 요청은 보안 필터에서 401로 끝납니다. 이때 `uri`가 `UNKNOWN`으로 기록될 가능성이 높고, 락 조회까지 가지 않아 `db_lock_wait_seconds_max`도 생기지 않습니다. 완료 기준 3에 "인증된 성공 이체 1건(락 단계 통과)"이라고 명시하세요.
> - **R-4. 로그 수집 3종 내림이 유지되지 않음:** `docker compose stop`은 이후 전체 `up -d`를 하면 다시 올라옵니다. 유지 방법(profile 분리나 후속 커밋)을 runbook에 적으세요.
> - **R-5. 문서 동기화:** `docs/10_J-Bank_폴더구조.md:189`의 "로컬(host.docker.internal)"이 D-04 이후 맞지 않게 됩니다. runbook 파일 경로(OPS-05 증거 위치)도 정해 적으세요.
> - **R-6. 알려진 한계로 기록할 것:**
>   - 18:00 종료 때 발생 중이던 알림은 해제 메일이 오지 않습니다.
>   - Alertmanager 데이터 볼륨이 없으면 재기동 뒤 같은 알림을 다시 보냅니다.
>   - postgres-exporter가 죽으면 교착 알림이 조용히 꺼집니다(`up{job=...}` 규칙 추가를 검토).
>
> ---
>
> ### 2. 새 결정의 임의 가정·묻지 않은 선택
> - Q-09·Q-10은 사용자 결정으로 기록됐습니다. 다만 D-05가 Q-09의 범위를 넓혔고(N-3), Q-10을 판단할 근거에 배치 최고 사용량이 빠졌습니다(N-2).
> - D-06의 "실제 사용량 < 상한"은 가정입니다(N-2).
> - 병합 시점과 postgres 재생성 시점은 사실상 정해지지 않은 운영 선택입니다(N-1).
> - D-01·D-02·D-03·D-04·D-08·D-09는 메인이 정할 수 있는 범위이고 근거가 있어 타당합니다. D-04에서 bootRun api를 수집하지 않는 것은 지금도 401이라 회귀가 아닙니다.
> - D-07의 superuser 수용은 1회차 권고 7에 따라 근거를 남겨 수용 가능합니다(R-1 별도).
>
> ### 3. REQ 연결
> - REQ-01~12 모두 완료 기준 1~6 중 하나 이상, 명령, 증거 위치에 연결돼 있습니다. 실패 조건도 대부분 관찰 가능합니다.
> - 보완이 필요한 REQ:
>   - REQ-11: 네트워크 접근 차단 시나리오가 없습니다(N-3).
>   - REQ-12: 실제 배포 경로(`up -d api`)가 빠졌습니다(N-1).
>   - REQ-10: 운영 기준의 측정 시점이 사고 조건과 맞지 않습니다(N-2).
>   - REQ-05·07: 지표 존재 확인의 전제가 모호합니다(R-3).
> - REQ-08의 운영 Gmail 수신과 REQ-10의 운영 측정을 배포 후 점검으로 분리한 것은 testing-policy의 "작업 완료와 배포 승인은 별개"에 맞습니다.
>
> ### 4. 조사한 사실 대조
> - **운영 메모리 측정, 운영 부팅 `up` 실측:** 원본이 없어 읽기로 확인할 수 없습니다. 산술(944-368=576, 상한 합 368)만 맞습니다.
> - **compose v2.29.7:** `user_data.sh.tpl:11` `COMPOSE_VERSION=v2.29.7`과 일치합니다. 실제 호스트에 깔린 버전은 읽기로 확인할 수 없습니다. `env_file`의 `required`는 2.24부터 지원하므로 문제없습니다.
> - **.gitignore:** `.gitignore:42-43` `.env*`·`!.env.example`과 일치합니다. `.env.alertmanager`는 git에서 제외됩니다.
> - **로컬 401:** 일치합니다. `prometheus.yml:8`이 `host.docker.internal:8080`을 가리키고, 로컬 api에 `MANAGEMENT_SERVER_PORT`가 없으며, `PrometheusScrapeRequestMatcher.java:33-35` 조건이 성립하지 않습니다.
> - **배포 관련 16행:** "deploy.sh는 `up -d api`만"은 맞습니다. 하지만 backend-cd의 `infra/compose/**` 트리거와 의존 서비스 재생성이 빠져 결론이 부정확합니다(N-1).
>
> ### 적용 제외 항목
> OPS-02, OPS-08, SEC-01~04, SEC-08, 성능·프론트·AI·전체 흐름 제외는 타당합니다. SEC-06을 "미검증(수용), 통과로 보지 않음"으로 적은 것도 타당합니다.
>
> 최종 판정: 수정 필요

### 지적 처리

- N-1: 조사한 사실에 backend-cd 경로·의존 서비스 재생성 추가. 사용자 결정 Q-11(18시 이후 병합). REQ-12·완료 기준 4에 `up -d api` 재생성 범위 기록, 롤백을 revert 커밋 병합 + `--remove-orphans`로 수정.
- N-2: 사용자에게 127MB 사고·181~241MB 정상 사례를 알리고 Q-12(배치 1회 실행 중 최저 150MB 기준) 결정. D-06의 가정 문구 삭제, REQ-10·배포 후 점검 갱신.
- N-3: D-05(경로 허용 목록 프록시, 전용 internal 네트워크), REQ-11·완료 기준 3에 archive 거부·타 컨테이너 접근 불가 추가. 남는 위험은 사용자에게 알리고 Q-14(수용) 기록.
- R-1: D-07 `DATA_SOURCE_URI/USER/PASS`, 완료 기준 1 특수문자 더미 비밀번호.
- R-2: 운영 측정 원본을 progress.md 부록에 보존(계획 단계는 logs/ 쓰기 불가), start 뒤 `logs/prod-measure.md`로 이동.
- R-3: 완료 기준 3에 인증된 성공 이체 1건 명시.
- R-4: D-10 운영 `logs` 프로필 + `COMPOSE_PROFILES`, 완료 기준 4에 프로필 끔 확인.
- R-5: 범위에 `docs/10_J-Bank_폴더구조.md`, runbook 경로 `docs/runbook/observability.md`.
- R-6: 알려진 한계 기록, D-11 alertmanager 상태 볼륨, Q-13(다운 알림을 exporter까지 확장).

## 3회차

회차 계획 지문: 7d3592ca2cbaf0f59031ab600b80cd321e94899ea47e16c53ab4872a3c02bc10 (2026-10-29T08:03:17Z 호출)
회차 판정: 통과 권고

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 결과: observability-logs-alerts (3회차, 마지막 회차)
>
> 판정은 **통과 권고**입니다. 2회차의 중요 지적 3건(N-1~N-3)과 권고 6건(R-1~R-6)은 모두 task.md에 실제로 반영됐습니다. 이번에 새로 확인한 문제는 모두 권고 수준입니다. 이 중 권고 1·2·3은 runbook과 e2e 절차를 쓸 때 꼭 넣어야 합니다.
>
> ### 검증 대상과 범위
> - **모드·대상:** 계획 리뷰 3회차입니다. 대상은 task.md(명세 상태: 확정, 구현 전)입니다. state.json에 기록된 3회차 계획 지문은 `7d3592ca…bc10`(08:03:17Z)입니다. 지금 파일이 이 지문과 같은지는 읽기만으로 확인할 수 없습니다.
> - **읽은 자료:**
>   - 정책·작업 문서: docs/testing-policy.md, docs/testing/devops.md, docs/testing/security.md, task.md, plan-review.md(1·2회차 원문과 처리 내역), progress.md 부록, state.json
>   - 인프라: docker-compose.yml, docker-compose.prod.yml, observability/prometheus.yml·prometheus.prod.yml, .env.example, deploy.sh, run-batch.sh, host/boot.sh, host/sync-latest.sh
>   - 배포: .github/workflows/backend-cd.yml, .github/scripts/deploy-ec2.sh
>   - 앱: LedgerReconciliationTasklet·JobConfig, SingleInstanceJobExecutionListener, PrometheusScrapeRequestMatcher, application.yml의 management 부분, jbank-transfer.json의 지표식
>   - 문서·설정: ADR 0011, bottleneck-analysis.md의 가설 E, docs/10_J-Bank_폴더구조.md, .gitignore
> - **적용 기준 ID:** OPS-01~08, SEC-01~08(task.md의 적용·제외 표 기준).
> - **확인하지 못한 범위:**
>   - 아무것도 실행하지 않았습니다.
>   - 다음 동작은 공식 동작에 대한 지식으로 판단했습니다. Compose의 설정 해시와 재생성, 파일 하나만 바인드 마운트했을 때의 동작, Spring Batch에서 식별 파라미터 없는 잡의 재실행, wollomatic 프록시와 Alloy의 API 사용 범위.
>   - 운영 측정은 progress.md 부록에 있는 원본만 봤습니다. SSM 명령 ID는 없습니다.
>
> ---
>
> ### 1. 2회차 지적 해소 여부
>
> | 지적 | 판정 | 근거 |
> | --- | --- | --- |
> | N-1 병합 시 postgres 재생성 | 해결 | 조사한 사실 17행이 실제 경로와 맞습니다(backend-cd.yml:8, deploy-ec2.sh:34, sync-latest.sh:35, deploy.sh:14에 `--no-deps` 없음). Q-11(18시 이후 병합)은 사용자가 정했습니다. REQ-12와 완료 기준 4에 재생성 범위 기록이 들어갔습니다. 롤백은 revert 병합 + `--remove-orphans`로 고쳤습니다(110행). 문구 불일치는 권고 4 |
> | N-2 배치 때 최고 메모리 | 해결 | Q-12는 127MB 사고와 181~241MB 정상 사례를 알린 뒤 받은 결정이라고 기록돼 있습니다. D-06의 근거 없는 가정 문구는 삭제됐습니다. REQ-10과 배포 후 점검에 배치 중 최저 150MB 기준이 들어갔습니다 |
> | N-3 소켓 프록시 범위 | 해결 | D-05에 경로 허용 목록(GET 5종)과 전용 internal 네트워크가 들어갔습니다. REQ-11과 완료 기준 3에 archive 거부, api·grafana에서 접속 실패 확인이 있습니다. 남는 위험은 Q-14 수용과 SEC-07·ADR 기록으로 처리했습니다 |
> | R-1 exporter 접속 주소 | 해결 | D-07이 `DATA_SOURCE_URI/USER/PASS`를 씁니다. 완료 기준 1에 특수문자가 든 더미 비밀번호가 있습니다 |
> | R-2 운영 측정 원본 | 해결(한계 있음) | progress.md 부록에 명령·시각·원본 출력이 있습니다. 부팅 `up` 구간은 JSON 원본이 아니라 요약이고, SSM 명령 ID가 없다는 점은 스스로 밝혀 두었습니다 |
> | R-3 인증된 이체 | 해결 | 완료 기준 3에 "인증된 성공 이체 1건(락 단계 통과)"이 있습니다 |
> | R-4 로그 수집 3종 내림 유지 | 해결 | D-10에서 `logs` 프로필과 `COMPOSE_PROFILES`를 씁니다. 완료 기준 4에 프로필을 끈 경우 확인이 있습니다. 실제 내림 순서 검증은 권고 5 |
> | R-5 문서 동기화 | 해결 | 범위에 폴더구조 문서와 `docs/runbook/observability.md` 경로가 들어갔습니다. 추가로 고칠 곳은 권고 9 |
> | R-6 알려진 한계 | 해결 | 180행에 기록했습니다. D-11 상태 볼륨, Q-13 exporter 다운 알림도 들어갔습니다. 남은 빈틈은 권고 6 |
>
> ---
>
> ### 차단
> 없음.
>
> ### 중요
> 없음.
>
> ### 권고
>
> **권고 1. 운영 반영 뒤 Grafana가 새 데이터소스를 읽지 못합니다.**
> - 기준: REQ-01·08 운영 부분, OPS-05. 대상은 배포 후 점검 107·109행과 runbook입니다.
> - 근거:
>   - 부팅 때 docker 데몬은 grafana를 저장소 갱신 전의 provisioning으로 띄웁니다. 갱신(`git reset`)은 그 뒤 sync-latest.sh:14에서 일어납니다(boot.sh:65).
>   - grafana 서비스 정의는 이번 범위에서 바뀌지 않습니다. 그러면 수동 `up -d`를 해도 grafana가 다시 만들어지지 않습니다. Compose 설정 해시에는 마운트된 파일의 내용이 들어가지 않습니다.
>   - Grafana는 데이터소스 provisioning을 기동할 때만 읽습니다.
> - 영향:
>   - 반영 당일 grafana.j-bank.site에 Loki·Alertmanager 데이터소스가 없어 109행 점검이 실패합니다.
>   - 다음 부팅까지 이 상태가 이어집니다.
>   - 롤백할 때도 provisioning에서 뺀 데이터소스는 Grafana DB에 남습니다(`deleteDatasources` 없이는 지워지지 않음).
>   - 완료 기준 4는 처음부터 띄우는 시나리오라서 이 문제를 잡지 못합니다.
> - 필요한 수정:
>   - runbook의 `up -d` 다음에 `docker compose restart grafana`(또는 provisioning reload API)와 데이터소스 조회 확인을 넣습니다.
>   - prometheus는 서비스 정의(규칙 디렉터리 마운트)가 바뀌어 다시 만들어지는지 구현에서 확인합니다.
>   - 롤백 절차에는 데이터소스 정리를 넣습니다.
>
> **권고 2. 롤백 절차의 순서가 모호합니다.**
> - 근거: 110행은 "revert를 18시 이후 병합 + 호스트에서 `up -d --remove-orphans`"라고 적었습니다. 서버가 꺼진 동안에는 호스트 명령을 실행할 수 없습니다. 다음 부팅의 sync-latest가 저장소를 되돌리기 전에 실행하면 새 서비스가 아직 정의에 남아 있어 아무것도 지워지지 않습니다.
> - 필요한 수정: runbook에 "다음 부팅에서 sync-latest가 끝난 뒤(boot.log로 확인) `up -d --remove-orphans` → grafana 재시작"으로 순서를 적습니다.
>
> **권고 3. 배치 메모리 측정 중 중단 조건과 복구 절차가 없습니다.**
> - 기준: REQ-10, OPS-06, Q-12.
> - 근거:
>   - 가설 E에서 배치 JVM은 가용 메모리를 약 680MB 줄였습니다(807→127). 운영 유휴 메모리는 944MB입니다.
>   - 새 스택이 실제로 200~300MB를 쓰면, 운영 시간에 하는 수동 측정 자체가 스래싱(SSM 끊김, 약 50분 무응답)을 다시 일으킬 수 있습니다.
>   - 지금 계획은 측정이 끝난 뒤에만 내립니다(108행).
> - 필요한 수정: runbook에 두 가지를 넣습니다.
>   - 표본을 뜨는 중에 150MB 미만이 보이면 즉시 `docker compose stop loki alloy socket-proxy`를 실행합니다.
>   - 무응답이 되면 EC2 중지·시작으로 복구합니다. 커밋된 데이터는 WAL로 복구됩니다.
> - 이 측정은 다음 부팅의 배치보다 위험하지 않고 오히려 안전망입니다. Q-12 결정은 유지하되 절차만 보강하면 됩니다.
>
> **권고 4. REQ-12와 완료 기준 4의 문구가 다릅니다.**
> - REQ-12는 "postgres만 재생성"이라고 적었습니다. 완료 기준 4는 "재생성된 컨테이너가 api·postgres뿐"이라고 적었습니다.
> - 운영에서는 이미지 태그가 바뀌어 api도 다시 만들어집니다.
> - 수정: 통과 조건을 "재생성은 {api, postgres}의 부분집합, 새 서비스 0개"로 통일합니다.
>
> **권고 5. 3종 내림 절차를 실제 순서대로 사전 검증해야 합니다.**
> - 완료 기준 4는 "`COMPOSE_PROFILES`를 비운 상태에서 `up -d` 하면 3종이 없다"만 봅니다.
> - runbook의 실제 순서는 이렇습니다. `logs`로 떠 있는 상태 → `.env`에서 `logs` 제거 → `docker compose stop loki alloy socket-proxy` → 전체 `up -d`(필요하면 docker 재시작까지) → 3종이 계속 멈춰 있음.
> - 이 순서를 그대로 prod-preflight에서 재현해야 합니다. 프로필이 꺼진 서비스를 이름으로 `stop`할 수 있는지도 이때 확인됩니다.
>
> **권고 6. exporter는 살아 있는데 DB에 접속하지 못하는 경우가 남습니다(Q-13 빈틈).**
> - postgres-exporter는 DB 접속에 실패해도 `/metrics`에 200으로 응답하고 `pg_up 0`을 냅니다.
> - 이때 `up==1`이라 다운 알림이 없고, 교착 카운터도 없어서 교착 알림이 조용히 꺼집니다.
> - 선택지는 둘입니다. 알려진 한계로 기록하거나, 사용자 확인 후 다운 규칙에 `pg_up==0`을 추가합니다. 알림 4종 유지 결정과 맞닿아 있어 사용자 선택 사항입니다.
>
> **권고 7. 메일 확인 기준을 구체적으로 적어야 합니다.**
> - REQ-08의 "알림 이름·요약 포함"을 완료 기준 3에서 Mailpit API로 제목과 본문을 대조하도록 명시합니다. 상태(firing/resolved), alertname, summary, 다운 알림의 대상 이름(api 다운 포함)을 봅니다.
> - "재기동 뒤 메일이 다시 오지 않는다"는 관찰 시간을 정합니다. 예: `group_wait 30s` + `group_interval 5m` 이상 기다립니다.
>
> **권고 8. 메모리 단위를 정해야 합니다.**
> - 944MB는 `free -m`, 즉 MiB입니다. bottleneck-analysis는 MB=10^6입니다.
> - 600MB·150MB 기준이 어느 단위인지(예: MemAvailable kB ÷ 1024) runbook과 기록에 적습니다.
>
> **권고 9. 문서와 증거 위치를 보완해야 합니다.**
> - docs/10_J-Bank_폴더구조.md
>   - 190행 "EC2(api:8080)"은 이미 틀렸습니다(실제는 9095).
>   - 183행 서비스 목록도 함께 고칩니다.
> - REQ-02 보관 키 확인의 증거 파일이 정해지지 않았습니다(config-check.md 또는 secret-check.md 중 하나로 지정).
> - 완료 기준 3에서 "alloy 컨테이너에서 요청"을 보낼 방법을 정합니다. Alloy 이미지에 curl이 없을 수 있어 `--network container:<alloy>`로 curl 이미지를 붙이는 식으로 적어 두면 재현성이 좋아집니다.
>
> ---
>
> ### 2. 새 결정의 임의 가정·묻지 않은 선택
> - Q-11~Q-14는 모두 사용자 답변으로 기록돼 있습니다. Q-12에는 판단 근거(127MB 사고, 181~241MB 정상 사례)를 알렸다는 기록이 있습니다.
> - D-05는 Q-09·Q-14 범위 안입니다. inspect 허용은 Alloy에 꼭 필요하고, 남는 위험은 Q-14로 수용했습니다.
> - D-07(URI/USER/PASS 분리)과 D-11(상태 볼륨)은 메인이 정할 수 있는 구현 선택이고 근거가 있습니다.
> - D-10(`logs` 프로필)은 Q-10·Q-12의 내림 결정을 실행하는 방법이라 별도 사용자 결정이 필요 없습니다.
> - 임의 가정이나 묻지 않은 중요한 선택은 없습니다. 권고 6의 `pg_up`만 사용자 선택 사항으로 남습니다.
>
> ### 3. REQ 연결
> REQ-01~12는 모두 완료 기준, 명령, 증거 위치에 연결돼 있습니다.
>
> | REQ | 연결된 완료 기준 | 증거 위치 |
> | --- | --- | --- |
> | REQ-01 | 3, 4, 배포 후 | local-e2e, prod-preflight |
> | REQ-02 | 1, 5 | 파일 미지정(권고 9) |
> | REQ-03 | 3 | local-e2e |
> | REQ-04 | 2, 3 | promtool, local-e2e |
> | REQ-05 | 2, 3(지표 존재) | promtool, local-e2e |
> | REQ-06 | 2, 3 | promtool, local-e2e |
> | REQ-07 | 2, 3(지표 존재) | promtool, local-e2e |
> | REQ-08 | 3, 배포 후 | local-e2e |
> | REQ-09 | 1(amtool), 4, 5 | config-check, prod-preflight, secret-check |
> | REQ-10 | 3, 4, 배포 후 | local-e2e, prod-preflight |
> | REQ-11 | 3, 4 | config-check, local-e2e |
> | REQ-12 | 4 | prod-preflight |
>
> - 실패 조건은 모두 관찰할 수 있습니다. 관찰 시간과 내용 대조가 덜 구체적인 곳은 권고 7입니다.
> - REQ-02는 설정 키가 있는지로 판정합니다. 15일 뒤 실제로 지워지는지는 검증하지 않는데, task.md가 이 범위를 분명히 적어 두어 수용할 수 있습니다.
>
> ### 4. 조사한 사실 대조
> - **10~11행:** 운영 서비스 6개, `api:9095` 하나만 수집, `env_file: .env`, 로컬은 `host.docker.internal:8080`에 관리 포트가 없어 401이 나는 점이 모두 파일과 맞습니다(matcher:33-36). 로컬 healthcheck 8080도 맞습니다.
> - **13행:** 대시보드 지표 이름과 이체 URI가 맞습니다.
> - **14행:** progress 부록 원본과 맞습니다(944MB = MemAvailable 966908kB, v2.29.7, 0 표본 1~2개 = 15~30초).
> - **15행:** bottleneck-analysis.md:25와 맞습니다.
> - **16행:** 맞습니다. Tasklet은 SELECT 집계와 로그만 남기고, REPEATABLE READ 읽기 트랜잭션입니다. 이 밖에 쓰는 것은 배치 메타데이터와 Redis 잡 락 키(SingleInstanceJobExecutionListener)뿐입니다. 식별 파라미터가 없는 잡이라 이미 COMPLETED된 인스턴스가 있어도 다시 실행됩니다. boot.sh:87이 매 부팅 같은 방식으로 돌립니다.
> - **17행:** backend-cd.yml:8, deploy-ec2.sh:34, sync-latest.sh:14·35, deploy.sh:13-14, ADR 0011:46과 맞습니다. 다만 "기존 grafana가 새 설정을 읽는다"는 재시작할 때만 맞고, 수동 `up -d`로는 다시 만들어지지 않습니다(권고 1).
> - **18행:** .gitignore:42-43과 맞습니다.
> - **19·20행:** 공식 동작과 맞습니다.
>
> ### 적용 제외 항목
> OPS-02, OPS-08, SEC-01~04, SEC-08, 프론트·백엔드·AI·전체 흐름, 앱 성능 제외는 타당합니다. SEC-06을 "미검증(수용), 통과로 보지 않음"으로 둔 것도 타당합니다.
>
> ### 이전 지적별 상태
> - 1회차 B-1, I-1~I-5, 권고 1~10은 2회차에서 해결로 확인했고, 이번에도 회귀가 없습니다.
> - 2회차 N-1~N-3, R-1~R-6은 모두 해결입니다(위 표).
>
> ### 참고
> state.json의 `phase: waiting`, `reason`이 Q-10 대기로 남아 있습니다. 계획 확정 뒤 workflow로 갱신이 필요해 보입니다. 판정에는 영향이 없습니다.
>
> 참고 경로:
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/task.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/host/boot.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/infra/compose/host/sync-latest.sh
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/10_J-Bank_폴더구조.md
>
> 최종 판정: 통과 권고

### 지적 처리

계획 리뷰 한도(최초 1 + 재리뷰 2)에 도달해 task.md는 3회차 지문 그대로 둔다. 권고는 명세를 바꾸지 않는 범위에서 구현 산출물에 반영한다.
- 권고 1·2·3·5·8: runbook 절차(grafana 재시작·데이터소스 확인·정리, 롤백 순서, 측정 중 즉시 내림·EC2 복구, 내림 순서 재현, MiB 단위)와 prod-preflight에 반영.
- 권고 4: 완료 기준 4 판정 시 "재생성은 {api, postgres}의 부분집합, 새 서비스 0개"로 해석해 기록.
- 권고 7: local-e2e에서 Mailpit 제목·본문(상태·alertname·summary·대상) 대조, 재기동 뒤 6분 관찰.
- 권고 9: 폴더구조 문서 183·190행 수정, REQ-02 증거는 `logs/config-check.md`, 프록시 요청은 `--network container:<alloy>` curl 이미지.
- 권고 6(`pg_up`): 사용자 선택. 추가하면 명세가 바뀌어 계획 재검증(review-extend, 사용자 허락)이 필요하다.

추가 처리(사용자가 권고 6 추가와 재검증을 선택): 명세가 바뀌므로 위 권고를 구현 산출물이 아니라 task.md에 직접 반영하고 4회차 리뷰를 받는다.
- 권고 6: Q-15, REQ-06·완료 기준 2·3에 `pg_up==0` 2분 이상 다운 알림.
- 권고 1·2·3·8: 배포 후 점검에 grafana 재시작·데이터소스 확인/삭제, 롤백 순서, 측정 중 즉시 내림·EC2 복구, MiB 단위.
- 권고 4: REQ-12·완료 기준 4 "재생성은 {api, postgres}의 부분집합, 새 서비스 0개".
- 권고 5: 완료 기준 4 내림 순서 재현.
- 권고 7: 완료 기준 3 메일 대조 항목, 재기동 뒤 6분 관찰.
- 권고 9: 범위의 폴더구조 문서 수정 내용, REQ-02 증거 `logs/config-check.md`, `--network container:<alloy>` curl.

## 4회차 (사용자가 Q-15 명세 변경과 함께 1회 추가 허용)

회차 계획 지문: 3489d143a027557495e212641c9bde0905280ecfde8e535c9151e305bc4c417b (2026-10-29T08:19:38Z 호출)
회차 판정: 통과 권고

### verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 계획 리뷰 결과: observability-logs-alerts (4회차, 사용자가 허락한 추가 회차)
>
> 판정은 통과 권고입니다. 3회차 권고 1~9는 모두 task.md에 실제로 들어갔습니다. Q-15도 REQ-06과 완료 기준 2·3에 맞게 반영됐고 관찰도 가능합니다. 이전 회차에서 해결한 사항에 회귀는 없습니다. 남은 것은 기준표(OPS-04·07)와 일부 문구가 Q-15를 따라가지 못한 정도이고, 모두 권고 수준입니다.
>
> ### 검증 대상과 범위
> - 모드·대상: 계획 리뷰 4회차입니다. state.json에 기록된 4회차 계획 지문은 `3489d143…417b`(08:19:38Z)입니다. 지금 task.md가 이 지문과 같은지는 읽기만으로는 확인할 수 없습니다.
> - 읽은 자료: task.md 전체, plan-review.md(3회차 원문과 처리 내역, 498~507행의 추가 처리), state.json, infra/compose/docker-compose.yml(DB 비밀번호와 depends_on 부분만).
> - 적용 기준 ID: OPS-01~08, SEC-01~08. 적용 영역 판단(DevOps·보안 적용, 나머지 제외)은 이전 회차와 같고 타당합니다.
> - 확인하지 못한 범위:
>   - 아무것도 실행하지 않았습니다.
>   - postgres-exporter가 DB 접속에 실패해도 200 응답과 `pg_up 0`을 내는 동작, Alertmanager와 Grafana의 동작은 공식 동작에 대한 지식으로 판단했습니다.
>   - 이번 회차에서는 docs/testing-policy.md와 docs/testing/devops.md·security.md를 다시 읽지 않았습니다. 3회차 이후 적용 영역이 바뀌지 않았기 때문입니다.
>
> ### 1. 3회차 권고 1~9 반영 여부
>
> | 권고 | 판정 | 근거(task.md 행) |
> | --- | --- | --- |
> | 1. Grafana provisioning 재읽기 | 해결 | 109행: `up -d` 다음에 `docker compose restart grafana`, 이어서 데이터소스 조회 확인. 112행: 롤백할 때 데이터소스 삭제. 17행 사실도 "재시작 시"로 고쳤음. 102행: 전체 `up -d`로 grafana가 재생성되지 않으면 재시작 뒤 데이터소스를 확인하도록 사전 검증에 넣었음 |
> | 2. 롤백 순서 | 해결 | 112행: sync-latest가 끝난 뒤(boot 로그로 확인) `up -d --remove-orphans`, 그다음 grafana 재시작, 그다음 데이터소스 삭제 |
> | 3. 배치 측정 중 중단·복구 | 해결 | 110행: 1초 간격 표본 중 150MiB 미만이면 즉시 3종 stop, 무응답이면 EC2 중지·시작(WAL 복구) |
> | 4. 재생성 범위 문구 | 대부분 해결 | 80행(REQ-12)과 102행이 "{api, postgres}의 부분집합, 새 서비스 0개"로 맞춰졌음. OPS-05(139행)만 아직 "재생성 api·postgres뿐"으로 남음(권고 A) |
> | 5. 내림 순서 재현 | 해결 | 99행: logs 프로필로 기동, `.env`에서 logs 제거, stop 3종, 전체 `up -d`, 3종이 계속 멈춰 있는지 확인 |
> | 6. pg_up 빈틈 | 해결 | Q-15 사용자 결정(39행). 21행 질문 범위도 Q-01~Q-15로 갱신됨 |
> | 7. 메일 대조·관찰 시간 | 해결 | 93행: Mailpit API로 상태·alertname·summary·다운 대상 이름 대조. 94행: 재기동 뒤 6분 관찰 |
> | 8. 메모리 단위 | 대부분 해결 | 110행에 MiB(`MemAvailable` kB ÷ 1024)를 정의함. Q-10·Q-12·REQ-10·OPS-06은 아직 "MB"로 적혀 있음(권고 B) |
> | 9. 문서·증거 위치 | 해결 | 63행: 폴더구조 문서의 서비스 목록과 `api:9095` 수정을 범위에 넣음. 103행: REQ-02 증거는 `logs/config-check.md`. 95행: 프록시 요청은 `--network container:<alloy>` curl 컨테이너로 보냄 |
>
> ### 2. Q-15 반영의 일관성과 관찰 가능성
> - REQ-06(74행): 조건 세 가지(api 수집 실패, exporter 수집 실패, `pg_up==0`)와 2분 경계, 대상 이름 포함, 정상 상태(`up==1`·`pg_up==1`, 알림 inactive)가 모두 관찰 가능한 형태로 적혀 있습니다.
> - 완료 기준 2(85행): 세 경우를 각각 1분45초와 2분으로 나눠 경계를 시험합니다. Q-02의 "2분 이상"과 맞습니다.
> - 완료 기준 3:
>   - 87행 사전 조건에 `pg_up==1`이 있습니다.
>   - 92행: 접속 정보를 틀리게 주입해 2분 넘게 두면 다운 메일(대상 postgres)이 오고, 원복하면 해제 메일이 오는지 봅니다.
>   - 93행: 메일에서 대상 이름을 대조합니다.
> - 알림 종류 4개 유지(목표 56행 "네 가지", 커밋 계획 172행 "규칙 4종")와 모순이 없습니다.
> - exporter 자체가 내려가면 `pg_up` 시계열도 없어집니다. 그래서 exporter 다운 알림과 postgres 다운 알림이 한꺼번에 오지 않습니다. 로컬 compose는 exporter 비밀번호를 compose 안의 고정값(`jbank`)으로 주기 때문에, exporter 환경변수만 바꾸면 postgres는 재생성되지 않습니다. 따라서 92행의 주입 방법은 실행할 수 있습니다.
> - 차단이나 중요 수준의 새 모순·누락은 없습니다.
>
> ### 차단
> 없음.
>
> ### 중요
> 없음.
>
> ### 권고
>
> **권고 A. 기준표 OPS-04·OPS-07·OPS-05가 새 명세를 따라가지 못했습니다.**
> - 기준: OPS-04·05·07. 위치는 task.md 138·139·141행입니다.
> - 근거:
>   - OPS-04의 기대 결과는 "정상 시 `up==1`·inactive", 실행 명령은 "로컬 api·exporter 정지"뿐입니다. `pg_up==1` 정상 조건과 `pg_up==0` 주입이 빠져 있습니다.
>   - OPS-07도 "교착·api·exporter 정지"만 적혀 있습니다.
>   - OPS-05는 권고 4 이전 문구("재생성 api·postgres뿐")를 그대로 두고 있습니다.
> - 영향: 완료 기준 2·3 본문에는 들어 있어서 실제 시험이 빠질 위험은 낮습니다. 하지만 결과 리뷰는 기준 ID와 증거를 맞대어 보기 때문에, 이 차이가 해석 차이를 부를 수 있습니다.
> - 필요한 수정:
>   - OPS-04·07에 `pg_up` 정상 조건과 주입 시나리오를 넣습니다.
>   - OPS-05 문구를 "{api, postgres}의 부분집합, 새 서비스 0개"로 통일합니다.
>
> **권고 B. 메모리 단위 표기를 MiB로 맞춥니다.**
> - 위치: Q-10(34행), Q-12(36행), REQ-10(78행), OPS-06(140행), 성능 테스트(164행)의 "600MB·150MB"입니다.
> - 근거: 110행만 MiB로 정의돼 있습니다.
> - 영향: 판정할 때 단위를 섞어 쓸 수 있습니다. 차이는 약 5%로, 기준선 근처 값에서만 판정이 갈립니다.
> - 필요한 수정: 위 위치를 "MiB"로 맞추거나, "단위는 110행 정의를 따른다"는 한 줄을 넣습니다.
>
> **권고 C. 운영 부팅 때의 `pg_up==0` 구간도 기록합니다.**
> - 근거: OPS-04는 2분 기준이 안전하다는 근거로 api 부팅 실패 구간(15~30초)만 듭니다. 이제 `pg_up==0`도 같은 다운 알림이 됩니다. 부팅 순간이나 `up -d api`로 postgres가 재생성될 때(Q-11) exporter가 잠시 `pg_up 0`을 낼 수 있는데, 이 구간은 측정된 적이 없습니다.
> - 영향: 2분을 넘을 가능성은 낮다고 봅니다. 다만 다음 부팅의 postgres 재생성 때 오알림이 나면 원인을 짚기 어렵습니다.
> - 필요한 수정: 배포 후 점검에서 첫 부팅의 `pg_up` 0 구간 길이를 기록합니다. 결과 리뷰를 막을 사항은 아닙니다.
>
> **권고 D. Q-02·Q-13 행에 Q-15 확장을 상호 참조합니다.**
> - 근거: Q-02(26행)는 "Q-13으로 확장"까지만 적혀 있습니다.
> - 수정: "Q-13·Q-15로 확장"으로 고칩니다. 문서 일관성만의 문제입니다.
>
> ### 3. 이전 회차 해결 사항의 회귀 여부
> - 1회차 B-1, I-1~I-5, 권고 1~10: 회귀 없음.
> - 2회차 N-1~N-3, R-1~R-6: 회귀 없음.
>   - 17행 배포 경로 사실, Q-11·Q-12, D-05·D-07·D-10·D-11, REQ-11·12, 완료 기준 4의 중간 상태와 프로필 검증이 모두 그대로 있습니다.
>   - 권고 반영으로 오히려 더 구체적이 됐습니다.
> - 3회차 권고 1~9: 위 표 기준으로 모두 해결입니다. 4와 8은 일부 표기가 남아 권고 A·B로 넘겼습니다.
>
> ### 적용 제외 항목
> OPS-02, OPS-08, SEC-01~04, SEC-08, 프론트·백엔드·AI·전체 흐름, 앱 성능 측정 제외는 이전 회차와 같고 타당합니다. SEC-06을 "미검증(수용), 통과로 보지 않음"으로 둔 것도 타당합니다.
>
> ### 참고
> state.json은 아직 `phase: waiting`이고, `reason`이 "Q-10 대기"로 남아 있습니다. 실제 상태와 다르므로 계획을 확정한 뒤 workflow로 갱신해야 합니다. 판정에는 영향이 없습니다.
>
> 참고 경로:
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/task.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/plan-review.md
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/state.json
>
> 최종 판정: 통과 권고

### 지적 처리

추가 회차까지 소진해 task.md는 4회차 지문 그대로 둔다. 권고는 명세 의미를 바꾸지 않으므로 구현·증거에 반영한다.
- 권고 A: 결과 증거에서 OPS-04·07에 `pg_up` 정상 조건·주입 결과를 함께 연결하고, OPS-05는 "{api, postgres}의 부분집합, 새 서비스 0개"(REQ-12)로 판정한다.
- 권고 B: 모든 메모리 기준은 MiB(`MemAvailable` kB ÷ 1024)로 판정·기록한다(runbook에 명시).
- 권고 C: runbook 배포 후 점검에 첫 부팅의 `pg_up` 0 구간 기록 추가.
- 권고 D: 문서 일관성만의 문제. Q-02는 Q-13·Q-15로 확장된 것으로 읽는다.

대상 계획 지문: 3489d143a027557495e212641c9bde0905280ecfde8e535c9151e305bc4c417b
최종 판정: 통과 권고
