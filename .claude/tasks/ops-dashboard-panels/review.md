# 검토 결과

## 검토 범위

결과 리뷰 2/3회차. 작업 시작 eda2d13 → 6aceeb4(PR #12로 main 2ce16ec에 병합). 최신 검사 verify-002.
1회차(review fail, 배포 전 범위 통과 권고, 원문은 evidence/review-1.md)에서 남았던 REQ-08·AC-03을 logs/prod-check.md로 확인했다.

## 독립 검증 결과

- 호출: verifier, agentId ab34d617196f30501, 2026-10-02(review-begin 2/3 직후).
- 대상 snapshot: 248c875557c679db36346110f387e53b480255ec4436142130467a29490efb6b
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> ## 결과 리뷰 2/3회차: ops-dashboard-panels
>
> ### 검증 대상
> - snapshot `248c875557c679db36346110f387e53b480255ec4436142130467a29490efb6b`이고, 출처는 evidence/checks.json입니다. git head는 `6aceeb4`, dirty는 false, passed는 true입니다.
> - 1회차 snapshot은 `925e6e…`(head `dc6f348`)였습니다. 그 뒤 변경은 evidence/verify-002/changes.txt에 있으며 아래 두 가지입니다.
>   - 개발일지 파일 추가
>   - `docs/devlog/README.md`에 1줄 추가
> - 대시보드 JSON(+181/-6)과 docs/06(+11)의 통계는 1회차와 같습니다. 1회차 이후 이 두 파일은 바뀌지 않았다고 봅니다.
>
> ### 읽은 자료
> - docs/testing-policy.md
> - docs/testing/devops.md(OPS-01·03·07 행)
> - task.md, review.md(1회차 원문과 지적별 처리), progress.md, state.json
> - logs/prod-check.md, logs/prod-query.md(끝의 쿼리 생성 명령 절 포함)
> - evidence/checks.json, verify-002/changes.txt, evidence/changes.txt
> - verify-002의 check-1·4·5·6.log
> - 현재 `infra/compose/observability/provisioning/dashboards/json/jbank-transfer.json`(제목, `"id":` 줄)
> - `infra/compose/docker-compose.prod.yml`(Grafana 이미지와 마운트)
> - `docs/devlog/2026-10-02_운영대시보드확장.md`, `docs/devlog/README.md` 25행
>
> 적용 기준 ID: OPS-01, OPS-03, OPS-07입니다. 나머지 영역은 계획대로 해당 없음입니다.
>
> ### 확인하지 못한 범위
> - 명령은 실행하지 않았습니다. 다음 항목은 기록된 내용만 보고 판단했습니다.
>   - SSM 결과의 진위
>   - md5 값과 "로컬 HEAD 파일도 같음"이라는 주장
>   - snapshot 해시 재계산
>   - backend-cd 성공과 PR 병합 사실
> - 운영 Grafana 화면은 직접 볼 수 없습니다. 화면 확인은 사용자 발언 기록에만 의존합니다.
> - 운영 Grafana가 메모리와 DB에 실제로 올린 대시보드 버전은 직접 증거가 없습니다. Grafana API 조회가 없고, 30초 주기 재로드는 info 로그를 남기지 않습니다. 대신 아래 세 가지로 간접 확인했습니다.
>   - 컨테이너 안 파일이 새 버전임
>   - 40분 넘게 dashboard 로거 오류가 0건임
>   - 사용자 화면 확인
> - prod-check.md 19행 "오늘 provisioning.dashboard 로그"를 조회한 명령은 실행 명령 목록에 없습니다. 29행의 grep은 error·warn만 거릅니다.
>
> ### 1. REQ-08 · AC-03 · OPS-07(배포 후) 대조
>
> | task.md 기대 | prod-check.md 근거 | 판단 |
> | --- | --- | --- |
> | 운영 Grafana에 `J-Bank 운영` 대시보드가 반영됨 | 아래 표 참고 | 충족(간접) |
> | Grafana 로그에 프로비저닝 오류가 없음 | 아래 표 참고 | 충족 |
> | 사용자가 화면에서 새 패널을 확인 | 34행 "머지했고 대시보드 화면 확인했어"(11:21 KST 무렵, 병합 10:47 KST 이후) | 충족, 한계 있음(권고-A) |
> | AC-03 방법(SSM 읽기 전용 파일·로그 grep + 사용자 응답 기록) | 37bdc5aa SSM 명령이 모두 조회(log, ps, exec grep·md5sum, logs grep)이고 쓰기 명령이 없음 | 합의한 방법과 일치 |
> | 비밀값 기록 없음 | 인스턴스 ID, SSM 명령 ID, 도메인, md5만 있습니다. Grafana 비밀번호는 다루지 않았다고 명시되어 있습니다. | 문제 없음 |
>
> 반영 여부의 근거:
> - 컨테이너 마운트 경로는 compose.prod 88행 `./observability/provisioning:/etc/grafana/provisioning:ro`이고, SSM이 읽은 경로와 같습니다.
> - 컨테이너 안 파일의 제목은 `J-Bank 운영`입니다.
> - `"id":` 줄 수 17은 현재 JSON을 grep한 결과와 같습니다. 행 100~103과 패널 1~13입니다.
> - 컨테이너 파일과 저장소 파일의 md5가 같습니다.
> - 운영 HEAD는 2ce16ec입니다.
>
> 로그 오류의 근거:
> - 01:40Z 이후 `logger=provisioning.dashboard`의 error·warn은 0건입니다. 이 구간은 병합 01:47Z를 포함합니다.
> - 판정 기준은 1회차 권고-1대로 한정되어 있습니다. plugins·alerting 2건은 무관 항목으로 분리되어 있습니다.
> - Grafana는 `Up 2 hours`이고 부팅 로그는 00:01:04Z입니다. 재시작 없이 바인드 마운트로 갱신된 상황과 앞뒤가 맞습니다.
>
> OPS-07은 병합 전(1회차 충족)과 배포 후를 합쳐 충족입니다. 알림과 5xx 0까지만 확인한다는 한계는 계획에서 합의한 그대로입니다.
>
> ### 2. 1회차 이후 회귀 여부
> - 1회차 이후 변경은 개발일지 문서 2개뿐이고, 대시보드 JSON과 docs/06은 변경 통계가 같습니다. 회귀 요인이 없습니다.
> - verify-002의 6개 검사는 모두 exit 0입니다.
>   - unittest 37개 OK
>   - jest 31/31
>   - next build 성공. 기존 `<img>` 경고만 있습니다.
>   - gradle test·spotlessCheck는 UP-TO-DATE입니다. 앱 코드 변경이 없으니 맞는 결과입니다.
>   - lint와 tsc도 exit 0입니다. tsc 로그가 비어 있는 것은 정상 출력입니다.
> - skipped_checks는 비어 있고, unchanged_during_checks는 true입니다.
> - checks.json 명령은 모두 실제 앱 검사이고, 성공만 반환하는 명령이 아닙니다.
> - 개발일지 내용은 task.md와 1회차 기록(5xx `or 0 *` 식, GC 단위 분리, 401·404 경로, 68건)과 일치합니다. 사실과 어긋나는 서술은 없습니다.
>
> ### 3. 1회차 권고 처리 상태
> - 권고-1(AC-03 판정 기준 한정): **해결.** prod-check.md 5행과 20행에서 판정 대상을 `provisioning.dashboard`의 error·warn으로 한정하고, plugins·alerting을 기존 무관 항목으로 적었습니다.
> - 권고-2(쿼리 생성 명령): **해결.** prod-query.md 72~84행에 생성 코드가 있습니다.
>   - 새 패널 id ≥ 6이고 row가 아닌 것만 고릅니다. 행 id는 100~103이라 `type != 'row'`로 빠집니다.
>   - `$__rate_interval`을 `5m`으로 바꾸고 `quote(safe='')` 처리합니다.
>   - 4행 설명과 일치합니다.
> - 권고-3(힙 max 2바이트): **미수정 결정이 타당합니다.** 선택 사항이었고, perf와 같은 식이며 영향이 미미합니다. 다시 요구하지 않습니다.
>
> ### 4. REQ별 판정(요구사항별 검증 표에 "통과" 등록 가능 여부)
>
> | REQ | 판정 | 근거 |
> | --- | --- | --- |
> | REQ-01 | 통과 | 1회차 충족. 대상 파일 변경 없음 |
> | REQ-02 | 통과 | 1회차 충족(prod-query.md) |
> | REQ-03 | 통과 | 1회차 충족 |
> | REQ-04 | 통과 | 1회차 충족 |
> | REQ-05 | 통과 | 1회차 충족 |
> | REQ-06 | 통과 | 1회차 충족(로컬 11.3.1). 운영에서도 dashboard 로거 오류 0건으로 보강됨 |
> | REQ-07 | 통과 | 1회차 충족 |
> | REQ-08 | 통과 | logs/prod-check.md. 한계는 권고-A |
> | REQ-09 | 통과 | 1회차 충족. docs/06 변경 없음 |
>
> AC-01~04와 하네스 verify(verify-002)도 모두 근거가 있습니다. REQ-01~09를 모두 "통과"로 등록해도 됩니다.
>
> ### 지적 사항
> - 차단: 없음
> - 중요: 없음
> - **권고-A (REQ-08 근거의 한계 표기, 선택).** prod-check.md "판정" 절에 한계를 한 줄 덧붙이면 좋습니다. 완료를 막지는 않습니다. 적을 내용은 다음과 같습니다.
>   - Grafana가 실제로 로드한 버전은 API로 조회하지 않았습니다. 30초 재로드는 info 로그가 없어서 파일 일치와 오류 0건으로 간접 확인했습니다.
>   - 사용자 발언 원문은 "대시보드 화면 확인"이고, 새 제목과 패널을 하나씩 짚었다는 표현은 아닙니다. 34행의 "새 대시보드를 확인함"은 메인의 해석입니다.
>   - 19행 "오늘 provisioning.dashboard 로그" 조회 명령이 명령 목록에 없습니다.
>
>   더 단단한 근거가 필요하면 다음 중 하나를 추가할 수 있습니다.
>   - SSM으로 Grafana 컨테이너 안에서 `/api/dashboards/uid/jbank-transfer`를 조회해 title과 panels 수를 확인합니다. 이 방법은 인증이 필요하므로 비밀값을 다루지 않는 방식이어야 합니다.
>   - 사용자에게 제목 `J-Bank 운영`과 4개 행이 보이는지 한 번 더 확인받습니다.
>
>   계획에서 합의한 AC-03 방법(파일·로그 grep + 사용자 응답)은 이미 충족했으므로 필수는 아닙니다.
>
> ### 적용 제외 항목
> OPS-02·04·05·06·08, SEC, BE, FE, E2E, 성능을 제외한 이유는 1회차 판단과 같으며 타당합니다. 1회차 이후 앱·설정·배포 방식 변경이 없습니다.
>
> ### 이전 지적별 상태
> - 1회차 차단·중요: 없었음
> - 권고-1: 해결
> - 권고-2: 해결
> - 권고-3: 미수정 결정, 타당
>
> ### 완료 전 남은 일(메인 담당)
> - review.md에 2회차 원문을 보존하고 REQ-08·AC-03 행을 갱신합니다.
> - review pass 등록, complete, 후속 PR(커밋 계획 5)을 진행합니다.
>
> 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | jbank-transfer.json 제목·패널 1~5 | logs/dashboard-check.txt 구조 검사(기존 패널 불변) | 통과 |
| REQ-02 | 패널 6·7·8 | logs/prod-query.md(건수·나이·max 0, p95 NaN 허용) | 통과 |
| REQ-03 | 패널 9 | logs/prod-query.md(products 단일 계열, UNKNOWN·`/**` 제외 확인) | 통과 |
| REQ-04 | 패널 11·12·13 | logs/dashboard-check.txt 단위 검사, logs/prod-query.md 값 | 통과 |
| REQ-05 | 패널 10 | logs/prod-query.md(5xx 0, 4xx 0.283) | 통과 |
| REQ-06 | 전체 JSON | logs/dashboard-check.txt(구조 검사, 로컬 Grafana 11.3.1 로드), 운영 dashboard 로거 오류 0건(logs/prod-check.md) | 통과 |
| REQ-07 | 운영 쿼리 절차 | logs/prod-query.md | 통과 |
| REQ-08 | 운영 배포(2ce16ec), Grafana 디렉터리 마운트 | logs/prod-check.md(컨테이너 안 제목·md5 일치, 대시보드 프로비저닝 오류 0건, 사용자 화면 확인 발언) | 통과 |
| REQ-09 | docs/06 3.5절 | verifier 1·2회차 대조 | 통과 |

## 지적별 처리

- 차단·중요: 없음.
- 1회차 권고-1~3: 해결 또는 타당한 미수정(위 원문 3절).
- 2회차 권고-A(선택): logs/prod-check.md는 reviewing 단계에서 hook이 편집을 막아 고치지 않고 여기 기록한다.
  - Grafana가 실제로 로드한 버전은 API로 조회하지 않았다(인증이 필요하고 비밀값을 다루지 않기 위해). 30초 재로드는 info 로그를 남기지 않아 파일 일치·오류 0건·사용자 발언으로 간접 확인했다.
  - 사용자 발언 원문은 "머지했고 대시보드 화면 확인했어"다. 직전 안내에서 확인할 항목(제목 `J-Bank 운영`, 행 4개, JVM·입금 반영 값)을 전달했지만, 하나씩 짚었다는 표현은 없다. prod-check.md의 "새 대시보드를 확인함"은 메인의 해석이다.
  - prod-check.md의 "오늘 provisioning.dashboard 로그"와 "그 밖의 provisioning error" 행은 같은 SSM 명령(37bdc5aa)의 `$C logs grafana --since 2026-10-02T00:00:00Z | grep 'logger=provisioning.dashboard' | tail -5`와 `... | grep -E 'logger=provisioning' | grep -E 'level=error' | sort | uniq -c`로 얻었다.

## 완료 기준별 근거

- AC-01: logs/dashboard-check.txt
- AC-02: logs/prod-query.md
- AC-03: logs/prod-check.md(한계는 위 권고-A)
- AC-04: verifier 1·2회차 대조
- 하네스 verify: verify-002 통과(앱 코드 변경 없어 Gradle UP-TO-DATE)

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 앱 요청 처리 변경 없음.

## 발견한 문제

- 운영의 `UNKNOWN` 401이 작업 시작 전 이미 68건이었다. 인터넷 탐색 요청으로 보이며, 4xx 비율에 그대로 잡힌다(Q-02 결정대로).

## 판정과 이유

review pass(2/3). REQ-01~09가 대시보드 파일·구조 검사·로컬 로드·운영 쿼리·운영 반영 증거로 확인됐고 차단·중요 지적이 없다.

## 확인하지 못한 부분

- 운영 Grafana의 메모리·DB 안 대시보드 버전(API 미조회). verifier는 명령을 실행하지 않고 기록을 읽어 판정했다.
