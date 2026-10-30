# 검토 결과

## 검토 범위

- 작업: observability-logs-alerts. 명세 task.md(승인 계획 지문 3489d143a027557495e212641c9bde0905280ecfde8e535c9151e305bc4c417b).
- 변경: base_head 8d3dc6e 이후 커밋 1471841·db437dc·b0ed529·ba0c1b3·3ae63ad·5419093·f1900d5·78dde66·c500c5b(1회차 지적 문서 보정). 작업 시작 전부터 있던 미추적 `perf/results/ec2-baseline/dryrun/` 파일은 무관(base_status).
- 검사: verify-003 통과(1회차 처리 후, checks.json 전체). verify-002도 통과. verify-001은 저장소 안 다른 git worktree로 하네스 unittest가 시간 초과·2건 실패(이번 변경과 무관). 사용자 결정으로 worktree를 저장소 밖으로 옮긴 뒤 재실행.
- 수동 증거: logs/config-check.md, promtool.md, local-e2e.md, prod-preflight.md, secret-check.md, prod-measure.md.

## 독립 검증 결과

### 1회차 (review-begin 1/3, 2026-10-29, 대상 snapshot e4f7f1b627531f152f60d4c64e921b9e0c907a0e1cc0babf21a0be01f72016cd)

판정: 수정 필요(차단 0, 중요 3, 권고 9). 응답 원문은 아래 「1회차 verifier 응답 원문」 절에 보존.

### 2회차 (review-begin 2/3, 2026-10-29, 대상 snapshot c9c211d16c42531933ba723b43148fecaaf08bfd682ef4233385fe0e3d9f93b3)

판정: 통과 권고(차단 0, 중요 0, 권고 N-1·R-1~R-3). 확인 자료: task.md, review.md, progress.md, logs/local-e2e·secret-check·config-check, evidence/checks.json·verify-002·verify-003, ADR·runbook·개발일지, 스크래치 원본 e2e4.py·e2e4.out·secret2.out. 미확인: 실행 없음, c500c5b diff 직접 확인 안 함(changes.txt 통계로 대조), Mailpit 본문 직접 확인 안 함, 대화 보고(I-3)는 기록으로만 확인. 응답 원문은 아래 「2회차 verifier 응답 원문」 절에 보존.


## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | 로그 한 곳 조회: `observability/alloy.alloy`(container·service 라벨), `loki.yml`, compose `loki`·`alloy`·`socket-proxy`(운영 `logs` 프로필), `provisioning/datasources/datasources.yml` Loki | local-e2e 1차 2단계(라벨 값에 compose-api-1·compose-postgres-1, 각 로그 조회), prod-preflight 4단계(prodcheck-api-1·postgres-1 각 5줄 이상, Loki health OK) | 통과 |
| REQ-02 | 15일 보관: `loki.yml` `retention_period: 360h`, `compactor.retention_enabled: true`, `delete_request_store: filesystem` | config-check(`loki -verify-config` exit 0, 키 grep). 실제 15일 뒤 삭제는 미검증(명세상 설정 키로 판정) | 통과 |
| REQ-03 | 교착·락 대기 로그: compose postgres `command: log_lock_waits=on` | local-e2e 1차 3단계(psql 교착 1건, Loki `deadlock detected`·`DETAIL ... Process`·`still waiting for`), 4차(`Process NNNN: <쿼리>`·`STATEMENT` 줄 조회), prod-preflight 2단계(`show log_lock_waits` on) | 통과 |
| REQ-04 | 교착 알림: `rules/alerts.yml` DeadlockDetected(`increase(...[5m]) > 0`), compose postgres-exporter, `prometheus*.yml` job postgres | promtool(증가 0 없음·1 발생), local-e2e 1차 1·4·9단계(카운터 존재, firing, 발생·해제 메일) | 통과 |
| REQ-05 | 이체 5xx 비율: `rules/alerts.yml` TransferServerErrorRate | promtool(5% 발생, 4.9%·4xx만·요청 0 없음), local-e2e 1차 1단계(이체 URI 지표 실제 존재) | 통과 |
| REQ-06 | 수집 대상 다운: `rules/alerts.yml` TargetDown(api `up`, exporter `up`, `pg_up`, `for: 2m`, target 라벨) | promtool(각 1분45초 없음·2분 발생, 정상 시 없음), local-e2e 1차 0·5~7·9단계(사전 조건, api·exporter 발생·해제, pg_up 발생, 대상 이름 대조), 4차(exporter 수집 유지 상태에서 pg_up 0→1 전환 12:21:53→12:31:23, 해제 메일 12:36:20), prod-measure(운영 부팅 실패 15~30초) | 통과 |
| REQ-07 | 락 대기: `rules/alerts.yml` AccountLockWaitHigh(`max(db_lock_wait_seconds_max) > 1`) | promtool(1초 없음·1.01초 발생), local-e2e 1차 1단계(지표 실제 존재) | 통과 |
| REQ-08 | 메일·Grafana 조회·재발송 없음: `alertmanager-entrypoint.sh`(send_resolved, D-09 주기), compose alertmanager(+로컬 mailpit, 상태 볼륨), datasources Alertmanager | local-e2e 1차 4~7·9단계(메일 제목·본문 대조, Grafana 경유 알림 조회), 3차(`restart` 뒤 6분 재발송 없음·해제 메일), 4차(`--force-recreate` 뒤 6분 재발송 없음). 운영 Gmail 수신은 배포 후 점검. 비고: 로컬 | 통과 |
| REQ-09 | 비밀값: `.env.alertmanager`(`required: false`) 분리, 진입 스크립트 경고·메일 없는 수신자 | config-check amtool(있음·없음), prod-preflight 4·5단계(파일 없음: 경고+기동·재시작 0, 틀림: 발송 실패 로그, api 메일 변수 0건), secret-check 2절(작업 폴더 포함 커밋할 파일 46개, 메일 아이디 0건), 3절(4차 로그 309줄 비밀번호 원문·JSON 키 0건) | 통과 |
| REQ-10 | 메모리: compose `mem_limit`·`restart: unless-stopped`, 운영 `logs` 프로필, runbook 3·4절 | local-e2e 2·4차(상한·마지막 시점 재시작 0·OOM 없음), prod-preflight 4·6단계(상한, 끄기 순서 뒤 3종 exited). 운영 유휴·배치 측정은 배포 후 점검. 비고: 배포 후 측정 남음 | 통과 |
| REQ-11 | 노출·권한: socket-proxy 허용 목록·`-allowfrom=alloy`·internal `docker-api` 네트워크, 새 서비스 `ports` 없음(운영) | local-e2e 2차(GET 200, POST·DELETE 405, archive 403, api·grafana에서 접근 불가), config-check·prod-preflight(소켓은 socket-proxy만, 포트 없음, Caddyfile 변경 없음) | 통과 |
| REQ-12 | 중간 상태: `prometheus*.yml` `rule_files` glob | prod-preflight 2·3단계(`up -d api` 재생성 postgres만·새 서비스 0, 재시작 prometheus `/-/ready` 200·api up=1) | 통과 |

## 지적별 처리

### 1회차

- I-1(중요, pg_up 원복 → 해제 미관찰): 인정. 1차 9단계의 해제는 exporter가 사라져 시계열이 없어진 결과였다. **처리**: 4차에서 override 파일로 exporter의 DB 비밀번호만 틀리게 재생성(수집 up=1 유지, pg_up=0) → 발생 메일 → 원래 설정으로 재생성 → `query_range`로 pg_up 0(12:21:53Z)→1(12:31:23Z) 전환 기록 → `[RESOLVED] TargetDown postgres` 12:36:20Z 도착. local-e2e 판정 표 근거도 고쳤다. 4차 첫 시도는 스크립트 버그 2개(정규식, compose 인자 조립)로 실패했고 원본과 원인을 local-e2e에 남겼다.
- I-2(중요, secret-check.md에 사용자 메일 식별자): 인정. **처리**: secret-check 1절의 패턴·명령에서 아이디를 `<사용자 메일 아이디>`로 가렸고, review.md에 보존한 1회차 원문에서도 같은 문자열을 가렸다(가림 사실 표기). secret-check 2절에서 커밋할 파일 46개(origin/main 대비 변경 19개 + 작업 폴더 27개 + index.md)를 다시 검사해 메일 아이디·`@gmail.com` 0건. 일치 4줄은 더미 값·패턴 인용·입력 안내.
- I-3(중요, prune 부작용 미보고): 인정. **처리**: 사용자에게 보고했다(1차 통합 확인 뒤 대화 보고 「알려드릴 점」: 정리용 `docker container prune -f`를 여러 번 실행해 로컬 Docker의 다른 멈춘 컨테이너도 지웠을 수 있고, 볼륨·이미지는 남아 있으며, 이후 이름 지정 삭제만 쓴다). 실행 내역은 2026-10-29 10:32~11:02Z 사이 약 6회(설정 검사·promtool 기록·config-check.sh·e2e 9·10단계)이며 출력을 버려 지운 목록은 남지 않았다. 4차부터는 `docker container rm <이름>`만 썼다. 최종 보고에 다시 적는다. 이와 별개로 세션 재시작 뒤 OrbStack이 비어 있던 것은 이 작업의 명령과 무관하다(progress·local-e2e 4차 기록).
- 권고 1: 4차에서 새 테스트 비밀번호로 가입·실패 로그인·이체 후 원문·JSON `"password":`·`password=`·JWT·주민번호 0건(309줄). 반영함.
- 권고 2: 4차에서 `Process NNNN:` 2줄·`STATEMENT` 2줄 조회 기록, runbook 7절에 여러 줄 로그 조회 방법 추가(c500c5b). 반영함.
- 권고 3: ADR 0014 수정(c500c5b). 반영함.
- 권고 4: 4차 끝(12:36Z) 5개 컨테이너 재시작 0·OOM 없음. 반영함(4차는 스택을 다시 만든 12:09Z부터 집계).
- 권고 5: 4차에서 발생 중 `--force-recreate` 후 6분 재발송 0통. 반영함.
- 권고 6: runbook 3-3에 상한 대비 사용률·재시작 확인 추가(c500c5b). 4차 측정 socket-proxy 12.79/16MiB(80%), loki 146.5/192MiB(76%). 반영함.
- 권고 7: 최종 보고에 명시한다(ADR 0014·progress에는 이미 기록).
- 권고 8: 개발일지·review.md 수정(c500c5b). 반영함.
- 권고 9: config-check.md 끝에 전체 출력 추가. 반영함.

### 2회차

- N-1(최종 보고 필수): 최종 보고에 prune 내역, 세션 재시작 뒤 OrbStack이 비어 있었고 원인을 확인하지 못했다는 점, Docker 컨텍스트가 desktop-linux로 바뀌어 있었고 전역 설정은 바꾸지 않았다는 점을 함께 적는다. "이 작업이 지운 것 아님"은 확인된 사실이 아니라 남은 기록에 원인 명령이 없다는 뜻으로 고쳐 말한다.
- R-1: 작업 기록을 커밋하기 직전에 같은 패턴으로 다시 검사한다(complete 뒤 커밋 전).
- R-2: verify-003의 gradle test는 `UP-TO-DATE`(이전 결과 재사용)다. 실제 실행은 verify-002(78dde66, BUILD SUCCESSFUL)이고 그 뒤 변경은 문서뿐이라 회귀 근거로 쓴다.
- R-3(선택): runbook의 socket-proxy 사용률 문구(75%)와 4차 측정(80%) 차이는 고치지 않는다. 고치면 다시 검사·리뷰가 필요하고 판단에 영향이 없다. 배포 후 점검에서 실측으로 갱신한다.

## 완료 기준별 근거

1. 설정 검사: config-check 15개 명령 모두 exit=0.
2. promtool: promtool.md `SUCCESS`, 테스트 묶음 10개·평가 지점 11개.
3. 로컬 통합: local-e2e 1~4차(1차 실패 2건·오류 1건, 4차 첫 시도 스크립트 버그 2건의 원인과 재검사 보존).
4. 운영 구성 사전 검증: prod-preflight 1~6단계.
5. 정적·비밀: config-check(보관 키), secret-check 1·2절(커밋할 파일 46개), 3절·local-e2e 2·4차(로그 표본 0건).
6. 앱 회귀: verify-003 통과(verify-002도 통과, verify-001은 저장소 내 worktree로 실패).

## 성능 테스트 확인

불필요(task.md): 앱 요청 처리 경로와 부하 테스트 구성 변경 없음. 운영 메모리는 OPS-06·배포 후 점검(유휴 600MiB, 배치 중 150MiB)으로 다룬다.

## 발견한 문제

- 구현 코드 결함은 두 회차 모두 없었다.
- 증거 문제(1회차 I-1·I-2)와 부작용 미보고(I-3)는 처리했다. 검사 스크립트 실수 4건(1차 판정 방식·API 버전·prune, 4차 첫 시도 정규식·인자 조립)은 원본과 원인을 local-e2e에 남겼다.
- 실행 환경: 저장소 안 git worktree로 verify-001 실패(사용자 결정으로 이동), 세션 재시작 뒤 Docker 컨텍스트 변경·OrbStack 비어 있음(원인 미확인).

## 판정과 이유

통과. 2회차 verifier 통과 권고. REQ-01~12와 완료 기준 1~6이 구현·테스트·실행 증거로 연결되고, 미해결 차단·중요 지적이 없다. verify-003 통과(gradle test는 verify-002 실행 결과 재사용, 이후 문서만 변경). 운영 반영 항목(Gmail 수신, 운영 메모리, 첫 부팅 pg_up 구간)은 계획대로 배포 후 점검으로 남는다.

## 확인하지 못한 부분

- 운영 반영: Gmail 실제 수신, 운영 유휴·배치 중 메모리, 첫 부팅 `pg_up` 0 구간(배포 후 점검, runbook).
- 15일 뒤 실제 로그 삭제(설정 키로만 판정).
- 이체 5xx·락 대기 알림의 실제 상황 재현(promtool로 판정, 지표 존재는 실측).

## 1회차 verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존. 단, I-2가 지적한 사용자 메일 아이디 문자열은 커밋 시 노출되므로 `<사용자 메일 아이디>`로 가렸다.)

> ## 결과 리뷰: observability-logs-alerts (1/3회차)
>
> 결론부터 말하면 **수정 필요**입니다. 차단은 없고 중요 3건이 있습니다. 구현 코드 자체에서 결함은 찾지 못했습니다. 문제는 증거 기록 2곳과, 작업 중 생긴 부작용을 사용자에게 아직 보고하지 않은 점입니다.
>
> ### 검증 대상과 범위
> - **모드·대상:** 결과 리뷰입니다. snapshot은 `e4f7f1b627531f152f60d4c64e921b9e0c907a0e1cc0babf21a0be01f72016cd`이고, state.json의 review_pending, 최신 evidence/checks.json과 같습니다. HEAD는 78dde66, base_head는 8d3dc6e입니다.
> - **읽은 자료**
>   - 기준 문서: testing-policy.md, testing/devops.md, testing/security.md
>   - 작업 문서: task.md, plan-review.md(1~4회차), progress.md, state.json
>   - 증거: evidence/changes.txt, checks.json, verify-002/check-1·5.log, logs/ 6개 파일
>   - 변경 코드: 범위에 적힌 설정 파일 전부. compose 2개, .env.example, prometheus 설정 2개, loki, alloy, entrypoint, rules, tests, datasources
>   - 문서: ADR 0014, runbook, docs/06·10 관련 부분, 개발일지
>   - 참고로 읽은 파일: boot.sh, run-batch.sh, .gitignore
>   - 메인이 실행한 원본 스크립트: 세션 스크래치의 e2e.py, e2e2.py, e2e3.py, preflight.sh와 config-check.sh의 prune 부분
> - **적용 기준 ID:** OPS-01·03·04·05·06·07, SEC-05·06·07. 제외 항목은 OPS-02·08, SEC-01~04·08입니다.
> - **확인하지 못한 범위**
>   - 저는 아무것도 실행하지 않았습니다.
>   - 커밋별 diff를 보지 못했습니다(git log 미실행). 그래서 5419093 이후 두 커밋(f1900d5, 78dde66)이 문서만 바꿨는지는 커밋 계획과 파일 대조로만 판단했습니다. preflight가 쓴 운영 compose 사본과 저장소 파일은 핵심 줄이 같았습니다.
>   - Mailpit 메일 본문은 직접 보지 못했습니다. 다만 e2e.py의 `verify_mail`이 본문에서 summary와 대상 이름을 실제로 확인하는 코드임은 읽어서 확인했습니다.
>
> ---
>
> ### 차단
> 없음.
>
> ### 중요
>
> **I-1. "exporter DB 접속 정보 원복 → 해제 메일(대상 postgres)"을 보여준 증거가 없습니다. 기록된 해제 메일은 원복이 아니라 exporter가 사라져서 온 것입니다.**
> - **기준:** 완료 기준 3의 6번째 항목, REQ-06·08, OPS-04·07(계획 4회차 권고 A)
> - **근거**
>   - local-e2e.md 1차 9단계를 보면, `e2e-pe-bad`를 멈춘 직후(10:56:52Z) `docker container prune -f`가 멈춰 있던 진짜 exporter 컨테이너까지 지웠습니다. 그래서 `compose start postgres-exporter`는 아무것도 띄우지 못했습니다. 문서 58행도 이 사실을 직접 적고 있습니다.
>   - 그 뒤로는 exporter가 하나도 없었으므로 `pg_up` 시계열 자체가 사라졌습니다. 10:59:50Z에 exporter 다운 메일이 새로 온 것이 그 증거입니다.
>   - 그런데 판정 표(148행)는 11:02:20Z의 `[RESOLVED] TargetDown postgres`를 "원복 → 해제"의 근거로 들고 있습니다. 이 메일은 `pg_up`이 1로 돌아와서가 아니라 시계열이 없어져 규칙이 더 이상 맞지 않아서 온 해제입니다.
>   - 2차와 3차 실행은 이 시나리오를 다시 하지 않았습니다.
> - **영향**
>   - "DB 접속이 살아나면 해제 메일이 온다"는 경로가 실제로 관찰되지 않았습니다.
>   - 판정 표가 증거와 맞지 않습니다.
>   - 규칙식으로 보면 해제될 것이 거의 확실하지만, 완료 기준에 명시된 실측 항목입니다.
> - **필요한 수정**
>   1. exporter가 계속 수집되는 상태에서 접속 정보만 틀리게 바꿨다가 되돌립니다. 예: compose 서비스 환경변수를 바꿔 `up -d postgres-exporter`.
>   2. Prometheus `query_range`로 `pg_up`이 0에서 1로 바뀌는 것을 기록합니다.
>   3. 1이 된 뒤에 `[RESOLVED] TargetDown postgres`가 도착하는지 기록합니다.
>   4. local-e2e.md 판정 표에서 이 항목의 근거를 고칩니다.
>
> **I-2. 비밀 검사 증거 파일에 사용자 메일 주소가 들어 있습니다. 이 파일이 커밋되면 REQ-09를 위반합니다.**
> - **기준:** REQ-09, SEC-05, D-02(공개 저장소에 개인 메일을 남기지 않음)
> - **근거**
>   - `.claude/tasks/observability-logs-alerts/logs/secret-check.md:7` 검색 패턴이 `<사용자 메일 아이디>|@gmail\.com|...`입니다. 메일 아이디와 도메인이 한 줄에 같이 있어 주소 전체를 알 수 있습니다.
>   - 29행에도 `git grep -nE "<사용자 메일 아이디>"`이 다시 나옵니다.
>   - `.gitignore`는 작업 폴더의 `logs/*.md`를 제외하지 않습니다(49~50행은 evidence 로그만 제외). 다른 작업의 `logs/*.md`도 저장소에 있습니다. 그래서 이 작업 폴더도 커밋 대상이 될 가능성이 높습니다.
>   - 지금 이 폴더는 미추적 상태라서 "저장소 전체 git grep 0건"이 이 파일을 잡지 못했습니다.
>   - 그 밖에 개발일지와 작업 폴더 전체를 검색했는데, 이 두 줄 말고는 해당 주소가 없었습니다.
> - **영향:** 공개 저장소에 알림 받는 개인 메일 주소가 올라갑니다. "커밋 파일에 사용자 메일 주소 0건"이 커밋 시점에 깨집니다.
> - **필요한 수정**
>   - 해당 패턴을 `<사용자 메일 아이디>`처럼 가립니다.
>   - 커밋할 모든 파일을 다시 검사하고, 그 결과를 secret-check.md에 남깁니다. 대상은 `.claude/tasks/observability-logs-alerts/**`, 개발일지 2개 파일, `.claude/tasks/index.md`입니다.
>
> **I-3. `docker container prune -f`가 이번 작업과 무관한 로컬 컨테이너를 지웠을 수 있습니다. 아직 사용자에게 보고되지 않았습니다.**
> - **기준:** 데이터 유실 가능성(통과 권고 제한 조건), progress.md 81행의 "사용자에게 보고할 것"
> - **근거**
>   - config-check.sh:64와 e2e.py:255·274에서 모두 3번 실행됐습니다.
>   - 출력은 `>/dev/null`로 버리거나 출력하지 않아서, 무엇이 지워졌는지 기록이 없습니다.
>   - 볼륨과 이미지는 남아 있습니다.
> - **영향:** 사용자의 다른 멈춘 컨테이너가 지워졌다면 그 쓰기 계층이 사라졌습니다. 지워진 목록을 복구할 방법도 없습니다.
> - **필요한 수정:** 코드 수정은 필요 없습니다. 완료 전에 다음 내용을 사용자에게 보고하고 확인을 받으며, 보고서에도 기록합니다.
>   - 실행 시각과 횟수
>   - 지워진 목록이 기록되지 않았다는 점
>   - 볼륨과 이미지는 남아 있다는 점
>
> ### 권고
> 1. **SEC-05 로그 표본 검색이 약합니다.**
>    - 무작위 테스트 비밀번호를 그대로 찾는 검사(e2e.py:298)는 1차가 11단계에서 멈춰 실행되지 않았습니다. 2차(e2e2.py:75)에서는 이 검사가 빠졌습니다.
>    - `password\s*[=:]` 패턴은 api의 ECS JSON 로그에 나오는 `"password":"..."`(이스케이프된 `\"password\":`)를 잡지 못합니다.
>    - 계획은 결과 기록만 요구하므로 완료를 막지는 않습니다. 대신 secret-check나 local-e2e에 이 한계를 적거나, JSON 형태도 잡는 패턴으로 다시 검색하기를 권합니다.
>    - 주민번호와 JWT 패턴은 테스트 값을 잡을 수 있는 형태였습니다.
> 2. **REQ-03의 "쿼리" 부분에 증거가 없습니다.**
>    - postgres의 여러 줄짜리 DETAIL은 줄마다 따로 Loki에 들어갑니다. alloy에 여러 줄을 묶는 단계가 없기 때문입니다. 따라서 `Process NNNN: select ...` 줄과 `STATEMENT` 줄은 따로 조회해야 합니다.
>    - 완료 기준 3의 문구(`deadlock detected`·`DETAIL`)는 충족했습니다.
>    - 후속 교착 실험을 위해 쿼리 줄의 조회 결과와 "줄 단위로 나뉜다"는 한계를 runbook이나 ADR에 남기기를 권합니다.
> 3. **ADR 0014 21행 "나머지는 403으로 거부"가 실제와 다릅니다.** 실제로는 메서드가 허용 밖이면 405(POST·DELETE), 경로가 허용 밖이면 403(archive)입니다(local-e2e 2차 프록시 로그).
> 4. **재시작 횟수 확인 시점이 끝이 아닙니다.** RestartCount는 2차(11:03Z)에서 확인했고, 3차(11:04~11:18Z)가 끝난 뒤에는 다시 보지 않았습니다. exporter는 다시 만든 뒤부터만 집계됩니다. 마지막에 한 번 더 기록하기를 권합니다.
> 5. **alertmanager 재기동 확인 방식의 한계입니다.** `restart`는 같은 컨테이너 파일시스템을 유지하므로 D-11 볼륨의 효과(컨테이너를 다시 만들 때)는 검증되지 않았습니다. REQ-08 문구는 충족했습니다. `up -d --force-recreate alertmanager` 변형을 권합니다.
> 6. **socket-proxy 메모리 여유가 작습니다.** 로컬에서 12.03/16MiB(75%)를 썼습니다. 배포 후 `docker stats`에서 확인할 항목으로 runbook 3절에 넣기를 권합니다.
> 7. **D-05와 다른 점(GET `/networks` 추가)을 사용자에게 알려야 합니다.**
>    - 네트워크 목록 응답에는 비밀값이 없고 ADR에도 기록돼 있어 타당합니다.
>    - 다만 Q-09·Q-14에서 사용자가 승인한 허용 범위보다 하나 넓으므로 최종 보고에 명시해야 합니다.
> 8. **개발일지 43행 "promtool 경계 테스트 13건"이 실제와 다릅니다.** alerts.test.yml의 평가 지점은 11개, 테스트 묶음은 10개입니다.
> 9. **config-check.md의 `alloy fmt` 출력 앞부분이 잘려 있습니다.** 종료 코드 0은 기록돼 있습니다.
>
> ---
>
> ### REQ별 대조
> | REQ | 구현 | 테스트·실행 증거 | 판정 |
> | --- | --- | --- | --- |
> | 01 | alloy.alloy(container·service 라벨), datasources.yml Loki | e2e 1차 2단계(Loki API, 컨테이너 이름 11개, api 50줄·postgres 11줄), preflight 4단계(Grafana Loki health OK, 컨테이너 이름 라벨) | 충족 |
> | 02 | loki.yml 30·35·36행 | config-check(키 3개, `-verify-config` 종료 코드 0) | 충족(실제 15일 뒤 삭제는 범위 밖으로 합의됨) |
> | 03 | 두 compose의 `log_lock_waits=on` | e2e 3단계(deadlock detected·DETAIL·still waiting for), preflight `show log_lock_waits`=on | 충족(쿼리 줄은 권고 2) |
> | 04 | alerts.yml DeadlockDetected `> 0` | promtool 0/1, e2e 실제 교착 → firing → Grafana 조회 → 발생·해제 메일 | 충족 |
> | 05 | TransferServerErrorRate | promtool 4.9%/5%/4xx만/요청 0, 지표 존재(uri·status 201) | 충족 |
> | 06 | TargetDown(label_replace 3개를 or로 묶음, `for: 2m`) | promtool 1분45초/2분, 3대상과 정상, e2e 사전 조건, api 다운·해제, exporter 다운, pg_up 다운 | **pg_up 원복 → 해제 미관찰(I-1)** |
> | 07 | AccountLockWaitHigh | promtool 1초/1.01초, `db_lock_wait_seconds_max` method 4종 존재 | 충족 |
> | 08 | entrypoint(send_resolved, 주기), Grafana Alertmanager 데이터소스, alertmanager-data 볼륨 | 메일 제목·본문 확인(verify_mail 코드 확인), Grafana API 조회, 3차 재기동 6분 재발송 없음 | 충족(pg_up 해제는 I-1) |
> | 09 | `.env.alertmanager` 분리, 경고 후 기동 | amtool 변수 있음/없음, preflight 파일 없음(경고·재시작 0)과 틀린 값(Notify attempt failed), api 메일 변수 0건 | **커밋 파일 메일 주소(I-2)** |
> | 10 | mem_limit 합계 368MB, unless-stopped, logs 프로필 | e2e 2차·preflight inspect, 끄기 순서 재현(.env 원복 → stop → up -d → 3종 exited) | 충족(권고 4) |
> | 11 | socket-proxy 허용 목록, internal 네트워크, `-allowfrom=alloy` | e2e 2차 GET 200, POST·DELETE 405, archive 403, api·grafana에서 이름 해석 실패, preflight 호스트 포트 없음, Caddyfile diff 없음 | 충족 |
> | 12 | rule_files glob | preflight 2단계(postgres만 재생성, 새 서비스 0개), 3단계(ready 200, api up=1) | 충족 |
>
> ### 완료 기준별 판정
> - **1:** 충족. compose `config -q` 2건(특수문자 비밀번호 포함), promtool check config·rules, amtool 2가지 경우, loki, alloy, 이미지 버전 고정 모두 확인했습니다. 특히 preflight에서 `/+=@`가 들어간 비밀번호로 `pg_up=1`이 나와 D-07이 실제로 확인됐습니다.
> - **2:** 충족.
> - **3:** I-1 한 항목을 빼고 충족.
> - **4:** 충족.
> - **5:** 보관 설정 키는 충족. 비밀 검사는 I-2로 미충족이고, 로그 표본 검색은 권고 1입니다.
> - **6:** 충족. verify-002의 6개 명령은 하네스 unittest 37건, lint, tsc, jest, gradle test와 spotlessCheck, build로, 모두 성공만 돌려주는 명령이 아니라 실제 앱 검사이고 종료 코드는 0입니다. check-5.log에 BUILD SUCCESSFUL이 있습니다. verify-001 실패 원인(저장소 안의 worktree)과 처리 기록도 남아 있습니다.
>
> ### 적용 제외 항목의 타당성
> - OPS-02·08, SEC-01~04·08과 프론트·백엔드·AI·전체 흐름·앱 성능 제외는 타당합니다. 앱 코드는 바뀌지 않았습니다(changes.txt).
> - SEC-06을 "미검증(수용)"으로 둔 것이 ADR 52행에 기록돼 있어 타당합니다.
>
> ### 계획 4회차 권고 처리 확인
> - **A:** OPS-04·07의 `pg_up`은 사전 조건과 주입으로 연결됐습니다. 해제 부분은 I-1입니다. OPS-05는 REQ-12 문구대로 판정했습니다.
> - **B:** runbook 6행과 compose 주석이 MiB로 통일됐습니다.
> - **C:** runbook 5-4에 반영됐습니다.
> - **D:** 문서 일관성만의 문제로, 판정에 영향이 없습니다.
>
> ### 이전 지적
> 결과 리뷰는 이번이 처음이라 이전 지적이 없습니다.
>
> ### 참고 경로
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/logs/local-e2e.md (58행, 148행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/logs/secret-check.md (7행, 29행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/adr/0014-observability-logs-alerts.md (21행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/devlog/2026-10-29_관측스택로그와알림.md (43행)
> - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/e2e.py (255·274·298행)
> - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/e2e2.py (75행)
>
> 최종 판정: 수정 필요

## 2회차 verifier 응답 원문

(인용 표시 `> `만 붙이고 내용 그대로 보존)

> ## 결과 리뷰: observability-logs-alerts (2/3회차)
>
> 결론은 **통과 권고**입니다. 1회차 중요 지적 3건 중 I-1과 I-2는 원문 증거로 해소를 확인했습니다. I-3은 사용자에게 다시 보고하는 일만 남았는데, 이건 최종 보고에서 할 일이라 완료를 막지 않습니다. 새 결함이나 회귀는 찾지 못했습니다. 다만 아래 N-1은 최종 보고에 반드시 넣어야 합니다.
>
> ### 검증 대상과 범위
> - **모드·대상:** 결과 리뷰입니다. snapshot은 `c9c211d16c42531933ba723b43148fecaaf08bfd682ef4233385fe0e3d9f93b3`이고, state.json의 review_pending, evidence/checks.json과 같습니다. HEAD는 c500c5b, base_head는 8d3dc6e입니다.
> - **읽은 자료**
>   - 작업 문서: task.md, review.md(처리 내역과 1회차 원문), progress.md
>   - 수동 증거: logs/local-e2e.md(4차 첫 시도와 재실행), secret-check.md(1~3절), config-check.md(끝의 alloy fmt 출력)
>   - 검사 증거: evidence/checks.json, verify-003/changes.txt·check-1.log·check-5.log, verify-002/changes.txt·checks.json·check-5.log
>   - 문서: ADR 0014의 21행, runbook 전체, 개발일지 36·43행
>   - 저장소 파일: alloy.alloy, compose 두 파일의 socket-proxy 허용 목록
>   - 메인이 남긴 원본: 스크래치의 e2e4.py, e2e4.out, exporter-wrong-pass.yml, secret2.out
> - **적용 기준 ID:** OPS-01·03·04·05·06·07, SEC-05·06·07. 제외 항목인 OPS-02·08, SEC-01~04·08은 1회차 판정과 같이 타당합니다.
> - **확인하지 못한 범위**
>   - 저는 아무것도 실행하지 않았습니다. git show로 c500c5b의 diff도 보지 않았습니다.
>   - 대신 verify-002와 verify-003의 changes.txt 통계를 비교했습니다. infra 파일 줄 수는 같았고 runbook만 85에서 87줄로 바뀌었습니다. 문서만 바꾼 커밋이라는 설명과 맞습니다.
>   - Mailpit 메일 본문은 직접 보지 못했습니다.
>   - 대화에서 사용자에게 보고했다는 내용(I-3)은 review.md의 기록으로만 확인했습니다.
>
> ---
>
> ### 1회차 지적 처리 확인
>
> **I-1 (pg_up 원복 → 해제 미관찰): 해결**
> - e2e4.out의 원본과 local-e2e.md 185~227행이 같습니다.
> - e2e4.py 139~170행을 읽어 다음을 확인했습니다.
>   - override 파일은 `DATA_SOURCE_PASS: wrong-on-purpose`만 바꿉니다. 그 상태에서 `up -d postgres-exporter`로 exporter를 다시 만듭니다.
>   - 12:22:19Z에 `up{job="postgres"}=1`이면서 `pg_up=0`이었습니다. 수집은 유지되고 DB 접속만 실패한 상태입니다.
>   - 12:25:00Z에 발생 메일이 왔습니다.
>   - 원래 설정으로 되돌리자 `query_range`가 `[('12:21:08','1'),('12:21:53','0'),('12:31:23','1')]`를 기록했습니다.
>   - 12:36:20Z에 `[RESOLVED] TargetDown postgres (postgres-exporter:9187 …)`가 도착했습니다.
> - 해제 메일이 시계열 소멸이 아니라 같은 라벨의 pg_up이 1이 된 결과임을 확인했습니다. 시각 간격도 group_interval 5m과 맞습니다.
> - 첫 시도의 실패 원본과 원인(정규식, compose 인자 조립)도 보존돼 있습니다(local-e2e 160~183행).
> - local-e2e 148행 판정 표의 근거도 고쳐졌습니다.
>
> **I-2 (secret-check.md에 사용자 메일 아이디): 해결**
> - secret-check 7행과 29행이 `<사용자 메일 아이디>`로 가려졌습니다.
> - 2절의 46개 파일 목록과 일치 4줄이 secret2.out 원본과 같습니다.
> - 저는 현재 작업 폴더 전체를 사용자 메일 아이디와 `@gmail\.com`으로 직접 검색했습니다(rg 기본 동작이라 gitignore 대상은 제외). 아이디는 0건입니다.
>   - `@gmail.com` 문자열 2건이 나왔습니다. review.md 39행과 secret-check.md 93행으로, 둘 다 도메인 글자만 적은 설명문이라 노출은 아닙니다.
>   - 이 두 줄은 12:37:11Z 재검사 뒤에 쓰였습니다. 그래서 2절의 "0건"은 지금 파일에 그대로 맞지는 않습니다(권고 R-1).
> - review.md에 보존된 1회차 원문에도 아이디가 없습니다.
>
> **I-3 (prune 부작용 미보고): 조건부 해결**
> - review.md 40행에 보고 내용이 있습니다. 시각(10:32~11:02Z), 횟수(약 6회), 지운 목록이 남지 않았다는 점, 볼륨과 이미지는 유지됐다는 점입니다.
> - 4차부터는 이름을 지정해서만 지웠습니다. e2e4.py에는 prune이 없습니다.
> - 사용자에게 실제로 보고했는지는 대화 기록이라 확인할 수 없습니다. 최종 보고에 다시 적을 때 N-1과 함께 다뤄야 합니다.
>
> **권고 1~9**
> - 1: 해결. 테스트 비밀번호 원문, JSON `\"password\":` 형태, JWT, 주민번호 패턴으로 309줄을 검색해 0건입니다. e2e4.py 174~176행의 패턴이 테스트 값을 잡을 수 있는 형태임을 확인했습니다.
> - 2: 해결. `Process 1297: …`, `Process 1296: …`, `STATEMENT` 줄이 조회됐습니다. runbook 7절에 조회 방법도 추가됐습니다.
> - 3: 해결. ADR 21행이 "허용 밖 메서드는 405, 허용 밖 경로(archive 등)는 403"으로 바뀌었고 compose의 `-allowHEAD`와도 일치합니다.
> - 4: 해결. 4차 끝에 5개 모두 재시작 0, OOM 없음입니다. 집계 시작 시점은 생성 시각으로 함께 기록됐습니다.
> - 5: 해결. `--force-recreate` 뒤 6분 동안 발생 메일은 1통으로 유지됐고, 알림은 계속 firing이었습니다.
> - 6: 해결(runbook 3-3). 4차 측정은 80%이고 runbook 문구는 75%여서 사소한 차이만 있습니다.
> - 7: 최종 보고에서 처리할 일입니다. ADR 21행에 `/networks`가 적혀 있습니다.
> - 8: 해결. 개발일지 43행이 "테스트 묶음 10개, 평가 지점 11개"로 고쳐졌습니다.
> - 9: 해결. config-check 143~185행의 fmt 전체 출력이 저장소 alloy.alloy와 들여쓰기만 다릅니다.
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
> **N-1 (최종 보고 필수 항목): OrbStack이 비어 있던 사실을 사용자에게 알려야 합니다.**
> - **근거**
>   - local-e2e 158행과 progress 94행: 세션 재시작 뒤 OrbStack의 컨테이너, 볼륨, 이 작업 이미지가 모두 없었습니다.
>   - 이것 때문에 I-3 보고에 들어간 "볼륨·이미지는 남아 있다"는 지금 확인할 수 없는 상태가 됐습니다.
>   - "이 작업이 지운 것 아님"은 메인의 주장일 뿐 근거가 기록돼 있지 않습니다. 남은 기록에서 원인이 될 명령은 찾지 못했습니다. prod-preflight의 `dc down -v`는 `prodcheck` 프로젝트에만 적용됩니다. 그래서 원인은 불명으로 봐야 합니다.
> - **영향:** 사용자의 다른 로컬 개발 데이터(볼륨)가 사라졌을 수 있는데 사용자가 모를 수 있습니다.
> - **조치:** 최종 보고에 다음 세 가지를 함께 적습니다.
>   1. prune 내역(I-3)
>   2. 재시작 뒤 OrbStack이 비어 있었다는 관찰, 원인을 확인하지 못했다는 점, Docker 컨텍스트가 desktop-linux로 바뀌어 있었다는 점
>   3. 전역 컨텍스트는 바꾸지 않았다는 점
>
> **R-1. 커밋 직전에 비밀 검사를 한 번 더 하세요.**
> - secret-check 2절(12:37:11Z) 뒤에 review.md와 secret-check.md가 수정됐고, 이 응답도 review.md에 추가될 예정입니다.
> - 커밋 바로 전에 같은 패턴으로 다시 검사하기를 권합니다. 이 응답에는 메일 아이디를 적지 않았습니다.
>
> **R-2. verify-003의 gradle test는 다시 실행되지 않았습니다.**
> - check-5.log에 `Task :test UP-TO-DATE`가 찍혀 있습니다(4.9초).
> - 앞선 verify-002(HEAD 78dde66)에서는 실제로 실행됐습니다(2m16s, BUILD SUCCESSFUL). 그 뒤로는 문서만 바뀌었기 때문에 회귀 근거로는 타당합니다.
> - 보고서에 이 점을 한 줄 적어 두기를 권합니다.
>
> **R-3.** runbook 3-3의 socket-proxy 사용률 "약 12MiB(75%)"를 최근 측정값(12.79MiB, 80%)으로 맞추면 좋습니다. 선택 사항입니다.
>
> ---
>
> ### REQ별 최종 판정
> | REQ | 판정 | 1회차 대비 변화 |
> | --- | --- | --- |
> | 01 | 충족 | 변화 없음 |
> | 02 | 충족 | 설정 키로 판정. 실제 15일 뒤 삭제는 합의대로 범위 밖 |
> | 03 | 충족 | 4차에서 쿼리 줄 보강 |
> | 04 | 충족 | |
> | 05 | 충족 | |
> | 06 | 충족 | 4차에서 pg_up 0→1 뒤 해제 메일(I-1 해소) |
> | 07 | 충족 | |
> | 08 | 충족(로컬) | `restart`와 `--force-recreate` 모두 재발송 없음. 운영 Gmail 수신은 배포 후 점검 |
> | 09 | 충족 | I-2 해소. 커밋 직전 재검사는 R-1 |
> | 10 | 충족 | 로컬 부분 충족. 운영 유휴·배치 측정은 배포 후 점검 |
> | 11 | 충족 | `/networks` 추가는 최종 보고 필요(권고 7) |
> | 12 | 충족 | |
>
> ### 완료 기준별 판정
> - **1:** 충족. alloy fmt 전체 출력이 추가됐습니다.
> - **2:** 충족.
> - **3:** 충족. pg_up 해제는 4차에서 확인했고, 마지막 시점 재시작 0도 확인했습니다.
> - **4:** 충족. 변화 없습니다.
> - **5:** 충족. 메일 아이디 0건입니다. 로그 표본은 2차 390줄과 4차 309줄 모두 0건입니다.
> - **6:** 충족. verify-003의 6개 명령이 모두 종료 코드 0입니다. 하네스 unittest 37건이 OK이고, gradle은 R-2와 같이 이전 실행 결과를 재사용했습니다. 모두 실제 검사 명령이며 성공만 돌려주는 명령은 없습니다.
>
> ### 참고 경로
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/logs/local-e2e.md (148, 158, 185~227행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/logs/secret-check.md (7, 29, 37~93행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/review.md (39~40행)
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/observability-logs-alerts/evidence/verify-003/check-5.log
> - /Users/imjaehyeon/Documents/[01] - Project/j-bank/docs/runbook/observability.md (52, 83행)
> - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/e2e4.py
> - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/e2e4.out
> - /private/tmp/claude-501/-Users-imjaehyeon-Documents--01----Project-j-bank/4c5bcd19-5960-45b1-b46f-bdf71ddeeb76/scratchpad/secret2.out
>
> 최종 판정: 통과 권고
