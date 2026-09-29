# 검토 결과

## 검토 범위

- 작업: ec2-load-test-followup. 명세 task.md(REQ-01~06), 계획 리뷰 2회차 통과(지문 25b244de…).
- 커밋: 9f18e2c, 9f0518a, 299ecc5, 1704dc5 (+ 1회차 개선 반영 커밋).
- 적용 기준: PERF-01, PERF-06, PERF-07.

## 독립 검증 결과

### 1/3회차

- 호출: verifier(agentId a96a9a96a607af793), 2026-09-28 KST.
- 회차 snapshot: c13f91b0157164118552b44b5c8e4acc2135e0f33a5baed10fc14f27a14f68b5
- 판정: 통과 권고(차단·중요 0, 개선 4)

#### verifier 응답 원문

## 결과 리뷰: ec2-load-test-followup (1/3회차)

- 모드: 결과 리뷰. 대상 snapshot은 `c13f91b0…`(HEAD 1704dc5, dirty는 작업 기록 파일뿐). 커밋은 9f18e2c, 9f0518a, 299ecc5, 1704dc5.
- 읽은 자료
  - 작업 기록: task.md, progress.md, plan-review.md(처리 내역), evidence/checks.json, evidence/changes.txt
  - 코드: perf/run-ec2.py:294-447, perf/ec2/target.sh 전체 단계, perf/README.md:398-441
  - 결과 문서: summary.md, bottleneck-analysis.md, first-pass 두 문서의 머리말
  - 원자료: s1/s2/s3의 k6-summary.json `dropped_iterations`, s1/s2/s3/s5의 analysis.json, s2/r1·r3의 integrity.md와 failed-keys-target.txt
  - 이력: .git/logs/HEAD:604-637
- 적용 기준: PERF-01, PERF-06, PERF-07. PERF-02~05는 새 측정이 없어 해당 없다는 판단이 타당합니다.
- 확인하지 못한 범위
  - EC2에서 stop-api 뒤 `docker compose logs api`가 실제로 동작하는지(다음 드라이런 항목).
  - progress.md의 실행 주장은 읽기만으로 재현할 수 없습니다. ast 문법 검사, s2/r3 사본으로 표 재생성, 대조 스크립트 ALL_MATCH, `git diff 479eefc b4f097a` 트리 비교가 여기에 해당합니다.
  - 드롭·메모리·키 값은 원자료와 제가 직접 대조했습니다(아래).

## REQ별 판정

| REQ | 판정 | 근거 |
| --- | --- | --- |
| REQ-01 | 충족 | 아래 "REQ-01 대조"에 정리 |
| REQ-02 | 충족 | 아래 "REQ-02 대조"에 정리 |
| REQ-03 | 충족 | 아래 "REQ-03 대조"에 정리 |
| REQ-04 | 충족 | first-pass/summary.md:3-7, first-pass/bottleneck-analysis.md:3-7. H1 바로 아래에 결함 표시, ../summary.md 링크, 결함 4가지가 있습니다. changes.txt 통계는 두 파일 모두 +6/-0이라 본문 수치는 바뀌지 않았습니다 |
| REQ-05 | 충족 | 아래 "REQ-05 대조"에 정리 |
| REQ-06 | 충족 | 아래 "REQ-06 대조"에 정리 |

### REQ-01 대조 (summary.md)

- 확정 드롭 수(k6-summary.json `count`)가 모두 일치합니다.
  - S1: 1628/1549/1588 (summary.md:28-30)
  - S2: r1 항목 없음 = 0, r2 1403, r3 99 (:54-56)
  - S3: 19620/19685/19635 (:71-73)
  - S5: 3회 모두 항목 없음 = 0 (:89)
- 추정치(analysis.json, `target_rps`로 단계 확인)도 일치합니다.
  - S1 100 단계: null ×3 → "수집 없음"
  - S1 150 단계: 1267.13 / 1048.89 / 1124.32 → 1,267 / 1,049 / 1,124
  - S2 r1: 80·100 단계 null
  - S2 r2: 100 단계 778.15 → 778, 120 단계 276.10 → 276
  - S2 r3: 80 단계 70.0, 100 단계 null
  - S3 스파이크: 20985.1 / 20764.0 / 20834.8 → 20,985 / 20,764 / 20,835
- S3 추정치가 확정 수보다 큰 것은 외삽 과대라고 적혀 있습니다(:75).

### REQ-02 대조

- summary.md:50은 r1·r3의 "실패 응답 0건"을 먼저 적고, 이어서 다음을 나눠 적습니다.
  - r1: 키 0건, 키 없는 반영 1건
  - r3: 끊긴 요청의 키 2건 중 1건 반영, 잔차 0, 하한 0
- 원자료와 같습니다.
  - s2/r3: failed-keys-target.txt:5-8(total 2, committed 1)과 integrity.md:6-8
  - s2/r1: failed-keys-target.txt:5-6(0/0)과 integrity.md:8(잔차 1)
- bottleneck-analysis.md:33도 같은 구분을 따릅니다. 섞인 "0 / 1" 표현은 남아 있지 않습니다.

### REQ-03 대조

- summary.md:79-84는 열 이름을 "배치 구간 / 회차 전체"로 나눴습니다. 값은 217/185, 204/204, 241/181입니다.
- analysis.json과 일치합니다.
  - `overall_system.mem_available_min`: 185.16 / 203.91 / 181.40 MB
  - 배치 3구간 최솟값: r1 min(430, 381, 217), r2 min(429, 364, 204), r3 min(407, 356, 241)
- 대표 행 중앙값(217/185)과 단위 MB = 10^6바이트가 적혀 있습니다(:84, :89).
- 가설 E(bottleneck-analysis.md:25)도 "배치 구간 204~241MB·회차 전체 181~204MB"로 같은 구분을 씁니다.

### REQ-05 대조 (run-ec2.py)

- 순서는 analyze(346) → export(347) → stop-api(350) → integrity(351) → 실패 키 복사·failed-keys(352-354) → 대사 batch(355) → logs(357)입니다. 정지 전에 스냅샷을 찍는 경로는 남아 있지 않습니다.
- 정지 후에도 각 단계는 동작합니다.
  - integrity·failed-keys는 `exec -T postgres`만 씁니다(target.sh:13, 208, 271). stop-api는 api, metrics-proxy, caddy만 멈추므로(target.sh:128) postgres는 계속 떠 있습니다.
  - 대사 batch는 `run --rm --no-deps api` 일회용 컨테이너라 정지된 api와 관계없습니다(target.sh:185). 이전 순서에서도 stop 뒤에 돌던 단계라 변화가 없습니다.
  - S5는 logs 뒤에 restore(`down -v`)가 오므로 로그 수집 전에 컨테이너가 지워지지 않습니다.
- 행 이름과 하한
  - 행 이름은 run-ec2.py:424 "실패 키 중 DB 반영(멱등키 대조, k6 중단 순간 끊긴 요청의 키 포함)"입니다.
  - 하한 식은 `max(fk_committed - max(fk_total - fail201, 0), 0)`입니다. s2/r3 값을 넣으면 max(1 − 2, 0) = 0으로 문서와 같습니다.
- 기존 integrity.md는 옛 행 이름 그대로입니다(s2/r3/integrity.md:7). 다시 만들지 않았습니다.

### REQ-06 대조

- README:407의 흐름이 REQ-05 순서와 같습니다.
- README:423-425에 비교 조건 차이와 드라이런 확인 항목 `api_log_lines > 0`이 있습니다. target.sh:281-282에서 이 값을 만듭니다.
- 해시 대응표(progress.md:12-20)의 7행은 모두 .git/logs/HEAD와 맞습니다.
  - 479eefc → b4f097a: amend(:617)
  - a2f21f7 → 51286d8 → a870ee0: pick 후 amend(:618, :627)
  - b1b2c1c → fb3cf7c(:628), 0759923 → 00f0087(:629), bd5e671 → f6b0fdf(:630), 49c97bc → f1f544b(:631), eff7ccf → 575c634(:632)
- "내용 동일"(트리 비교)은 git을 실행해야 확인할 수 있어 제가 확인하지 못했습니다.

## 기타 확인

- checks.json의 verify는 6개 명령이 모두 실제 검사입니다(unittest, lint, tsc, jest, gradle test+spotless, build). 모두 exit 0이고 건너뛴 항목은 없습니다. 앱 코드 변경이 없으니 회귀 확인으로 적절합니다. gradle이 3.8초로 짧은데, 캐시 적중으로 보입니다.
- verify는 perf/run-ec2.py 자체를 검사하지 않습니다. 이 부분은 task.md 계획대로 ast 문법 검사와 순서 확인이 근거인데, 그 출력은 progress.md 요약뿐입니다. review.md에 원출력을 남겨야 합니다(개선 2).
- 계획 리뷰 개선 3건은 plan-review.md:144에 적은 대로 구현에서 처리됐습니다.
  - PERF-07 대조를 커밋 3 뒤로 옮겼습니다.
  - S2 한 단계 더 간 단계가 문서(:54-56)에 반영됐습니다.
  - 해시 트리 비교를 기록했습니다.
- `perf/results/ec2-baseline/dryrun/` 미추적 파일은 changes.txt상 작업 시작 전부터 있던 것입니다. 이번 작업 범위가 아닙니다.

## 지적 (차단·중요 없음)

1. **개선 (PERF-01, REQ-06): `api_log_lines`가 에러 출력만으로도 0보다 커질 수 있습니다.**
   - 근거: target.sh:278은 `logs ... api > api-full.log 2>&1 || true`이고, :281은 그 파일의 `wc -l`을 셉니다. 정지된 컨테이너의 로그 읽기가 실패해도 compose 에러 메시지 한 줄이 들어가 `api_log_lines` ≥ 1이 됩니다.
   - 영향: README:425가 다음 드라이런 확인 기준으로 정한 "> 0"이 실패를 통과로 볼 수 있습니다. 컨테이너가 지워지지 않는 한 compose logs는 정지된 컨테이너에서도 동작하므로, 실제로 일어날 가능성은 낮습니다.
   - 수정 제안: 둘 중 하나를 택합니다.
     - stderr를 별도 파일로 보냅니다.
     - `grep -c '"log.level"'`처럼 앱 로그 형식의 줄 수를 셉니다.
   - 다음 드라이런 기준을 그 값으로 바꿉니다.
2. **개선 (PERF-06·07 증거): 실행 결과 원출력이 없습니다.**
   - 근거: 대조 스크립트 ALL_MATCH, ast 검사, s2/r3 재생성 표, 트리 diff는 progress.md:5, :10의 요약뿐입니다.
   - 수정 제안: task.md가 증거 위치로 정한 review.md에 명령과 원출력(또는 요지)을 남깁니다. 값 자체는 제가 원자료와 따로 대조해 일치를 확인했습니다.
3. **개선 (REQ-01 표기): S5 확정 드롭 수가 표가 아닌 문장에 있습니다.**
   - 근거: S5 확정 드롭 수는 표가 아니라 summary.md:89 문장에 "3회 모두 0"으로 적혀 있습니다.
   - 판단: 값은 맞고 회차별 값이 모두 드러나므로 결함은 아닙니다. 다른 시나리오와 형식을 맞추려면 S5 표에 열을 추가할 수 있습니다(선택).
4. **개선 (주석): stop-api 주석이 새 순서와 조금 맞지 않습니다.**
   - 근거: target.sh:126의 stop-api 주석은 "대사 잡을 돌리기 전에 api를 멈춘다"라고만 적혀 있습니다.
   - 수정 제안: 이제 정합성 스냅샷 전에 멈추는 목적도 있으니 한 줄을 덧붙이면 됩니다. 동작에는 영향이 없습니다.

## 적용 제외 항목

- PERF-02~05: 새 측정이 없으므로 제외가 타당합니다.
- 프론트, 백엔드, AI, 보안, DevOps 영역: 앱·인프라 변경이 없으므로 제외가 타당합니다.
- EC2 실제 실행: 다음 드라이런으로 넘긴 것은 명세(REQ-05, 성능 테스트 절)에 합의된 제외입니다.

## 이전 지적 처리 (계획 리뷰)

- 중요-1, 중요-2: 계획 단계에서 해결됐고, 구현도 이를 따릅니다.
- 개선-1~3: plan-review.md:144에 적은 처리 방식대로 구현됐습니다. 해결.

(원문 마지막 줄) 최종 판정: 통과 권고

#### 1회차 처리

- 개선 1(api_log_lines가 에러 출력만으로 0보다 커짐): 반영 — 앱 로그 형식(`"log.level"`) 줄 수를 세도록 바꾸고 README 드라이런 기준을 그 값으로. 수정 후 2회차 리뷰.
- 개선 4(stop-api 주석): 반영.
- 개선 2(원출력): 아래 "실행 증거 원출력"에 기록.
- 개선 3(S5 드롭을 표 대신 문장으로): 값이 맞고 회차별로 드러나 유지(선택 사항).

### 2/3회차 (재리뷰)

- 호출: verifier(agentId adf25346b73ec5856), 2026-09-28 KST.
- 대상 snapshot: 705b2e247d6d5cdab3ea407f07e0a0725aed43b2e1579ef17252f456125f034d (HEAD 6fd56a2)
- 판정: 통과 권고(새 지적 0)

#### verifier 응답 원문

## 결과 리뷰: ec2-load-test-followup (2/3회차, 재리뷰)

- 대상: evidence/checks.json snapshot `705b2e24…`, HEAD 6fd56a2. dirty 상태이지만 작업 기록과 작업 전부터 있던 dryrun 미추적 파일뿐입니다.
- 읽은 자료
  - review.md: 1회차 원문, 처리 내역, 실행 원출력
  - perf/ec2/target.sh 전체
  - perf/README.md:425-426
  - evidence/checks.json
  - apps/jbank-api/src/main/resources/logback-spring.xml: ECS 형식인지 확인
- 적용 기준: PERF-01, PERF-06, PERF-07
- 확인하지 못한 범위
  - EC2에서 정지된 api의 `docker compose logs`가 실제로 동작하는지. 다음 드라이런으로 넘긴 합의된 제외 항목이라 이번에도 확인하지 못했습니다.
  - review.md의 로컬 실행 원출력. 읽기만 했고 제가 다시 실행하지 않았습니다.

## 이전 지적 처리

- **개선 1: 해결.**
  - target.sh:283이 이제 `grep -c '"log.level"' … || true`로 앱 로그 줄만 셉니다. 이 키는 logback-spring.xml:8-9의 StructuredLogEncoder `ecs` 출력 형식과 맞습니다. 기존 api_error_lines·api_warn_lines(:287-288)도 같은 키를 이미 전제하고 있습니다.
  - README:425-426의 드라이런 기준도 같은 값을 쓰도록 바뀌었습니다.
  - review.md:198-203의 원출력(에러 한 줄 파일은 0, 앱 로그 2줄 파일은 2)이 의도와 맞습니다.
- **개선 2: 해결.** review.md:151-203에 다음 원출력이 남아 있습니다.
  - 문법 검사
  - 순서 오프셋
  - 트리 diff
  - numstat
  - ALL_MATCH 대조
  - s2/r3 재생성 표(:196에 행 이름 시점 차이 주석 포함)
- **개선 3: 유지 결정.** 값이 정확하고 선택 사항이라 타당합니다. 다시 요구하지 않습니다.
- **개선 4: 해결.** target.sh:126-128 주석에 두 목적이 모두 적혀 있습니다.
  - 정합성 스냅샷과 실패 키 대조가 같은 상태를 보게 함
  - 대사 JVM 스래싱 방지

## 회귀 확인 (grep -c와 set -euo pipefail)

- 줄이 하나도 맞지 않으면 `grep -c`는 "0"을 출력하고 exit 1로 끝납니다.
  - 명령 치환 안의 `|| true`가 종료 코드를 0으로 만들고, `true`는 아무것도 출력하지 않습니다. 결과는 `api_log_lines=0`이고 스크립트는 중단되지 않습니다.
  - 파일이 없으면 grep은 exit 2로 끝나지만 `|| true`로 역시 흡수됩니다. 다만 :279의 리다이렉션이 항상 파일을 만들기 때문에 이 경우는 생기지 않습니다.
- 같은 블록의 :285-288도 같은 패턴이라 동작 방식이 일관됩니다. `{…} | tee`는 pipefail이 걸려 있어도 블록 안 명령이 모두 0으로 끝나므로 문제가 없습니다.
- `api_log_lines`를 파싱하는 소비자는 run-ec2.py에 없습니다(grep 결과가 target.sh와 README뿐). 값의 의미가 바뀌어도 하위 영향은 없습니다.
- stop-api 동작은 바뀌지 않았고 주석만 바뀌었습니다.
- checks.json(verify-002, HEAD 6fd56a2)
  - 6개 명령이 모두 실제 검사입니다(unittest, lint, tsc, jest, gradle test+spotless, build).
  - 모두 exit 0이고 건너뛴 검사는 없습니다.
  - gradle 1.2초와 jest 1.0초는 캐시 적중으로 보입니다. 앱 코드는 바뀌지 않았으므로 회귀 확인으로 받아들일 수 있습니다.

## 새 지적

없음. 차단·중요·개선 모두 0건입니다.

## REQ별 판정

| REQ | 판정 | 비고 |
| --- | --- | --- |
| REQ-01 | 충족 유지 | 문서 변경 없음 |
| REQ-02 | 충족 유지 | 문서 변경 없음 |
| REQ-03 | 충족 유지 | 문서 변경 없음 |
| REQ-04 | 충족 유지 | 문서 변경 없음 |
| REQ-05 | 충족 유지 | run-ec2.py 순서는 그대로이고, target.sh 변경은 logs 집계와 주석뿐 |
| REQ-06 | 충족 유지 | README 드라이런 기준이 새 계산과 일치 |

## 적용 제외 항목

- PERF-02~05, 앱·인프라 영역: 새 측정이 없고 앱·인프라 변경도 없어 제외가 타당합니다(1회차 판단 유지).
- EC2 실제 실행: 다음 드라이런으로 넘긴 합의된 제외입니다.

(원문 마지막 줄) 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | perf/results/ec2-baseline/summary.md(S1·S2·S3 드롭 표, S5 문장) — 299ecc5 | 문서 대조 스크립트 ALL_MATCH(아래 원출력), verifier 1회차 원자료 대조 | 통과 |
| REQ-02 | summary.md S2 문단, bottleneck-analysis.md 금융 표 — 299ecc5 | s2/r1·r3 integrity.md·failed-keys-target.txt 대조(verifier), "0 / 1" 잔존 없음 | 통과 |
| REQ-03 | summary.md S5 표·주석, bottleneck-analysis.md 가설 E — 299ecc5 | 대조 스크립트(217/185, 204/204, 241/181) ALL_MATCH | 통과 |
| REQ-04 | first-pass/summary.md·bottleneck-analysis.md 머리말 — 1704dc5 | `git show --numstat 1704dc5` 두 파일 +6/−0 | 통과 |
| REQ-05 | perf/run-ec2.py cmd_run 후처리 순서(9f18e2c), write_integrity 행 이름·하한(9f0518a) | 호출 오프셋 stop 56 < integrity 57 < failed-keys 60 < recon 61 < logs 63, s2/r3 사본 표 생성(하한 0), 문법 검사. EC2 실제 동작은 다음 드라이런(합의된 제외) | 통과 |
| REQ-06 | perf/README.md 회차 흐름·비교 조건 차이·드라이런 기준(9f18e2c, 6fd56a2), perf/ec2/target.sh api_log_lines(6fd56a2), progress.md 해시 대응표 | 트리 diff 없음(479eefc↔b4f097a, a2f21f7↔a870ee0), grep -c 확인(에러 0, 앱 로그 2) | 통과 |

## 지적별 처리

- 1회차 개선 1·4: 6fd56a2로 반영, 2회차 해결 확인.
- 1회차 개선 2: 실행 증거 원출력 기록, 2회차 해결 확인.
- 1회차 개선 3: 유지(값 정확, 선택 사항), 2회차 타당 판단.

## 완료 기준별 근거

- REQ-01~06: 위 표. 필수 검사 verify 통과(snapshot 705b2e247d6d5cdab3ea407f07e0a0725aed43b2e1579ef17252f456125f034d).

## 성능 테스트 확인

- 불필요(새 측정 없음). 후처리 순서의 실제 동작과 `api_log_lines` > 0은 다음 EC2 드라이런 확인 항목으로 README에 적었다.

## 발견한 문제

- 없음(작업 범위). 로컬 미추적 dryrun 산출물은 사용자 결정으로 그대로 둔다.

## 판정과 이유

결과 리뷰 2회차 통과 권고, REQ-01~06 충족, 검사 통과, 미해결 지적 없음. 통과로 등록한다.

## 확인하지 못한 부분

- EC2에서 정지된 api 로그 발췌(다음 드라이런).

## 실행 증거 원출력

2026-09-28, HEAD 6fd56a2(1회차 개선 반영 후) 로컬 실행.

```
$ python3 -c "import ast; ast.parse(open('perf/run-ec2.py').read())" && bash -n perf/ec2/target.sh && echo "syntax ok"
syntax ok

$ (cmd_run 함수 시작 기준 호출 줄 오프셋)
stop-api 56 < integrity 57 < failed-keys 60 < recon 61 < logs 63

$ git diff --stat 479eefc b4f097a ; git diff --stat a2f21f7 a870ee0
tree diff 479eefc..b4f097a=[] a2f21f7..a870ee0=[]        # 빈 출력 = 트리 차이 없음

$ git show --numstat --format= 1704dc5
6	0	perf/results/ec2-baseline/first-pass/bottleneck-analysis.md
6	0	perf/results/ec2-baseline/first-pass/summary.md
```

문서 대조 스크립트(커밋 299ecc5 뒤, 원자료 k6-summary.json count·analysis.json에서 REQ-01·03 규칙으로 기대 행을 만들어 summary.md에서 찾음):

```
OK  | r1 | 1,628 | 수집 없음 | 1,267 |
OK  | r2 | 1,549 | 수집 없음 | 1,049 |
OK  | r3 | 1,588 | 수집 없음 | 1,124 |
OK  | r1 | 0 | 수집 없음 (80) | 수집 없음 (100) |
OK  | r2 | 1,403 | 778 (100) | 276 (120) |
OK  | r3 | 99 | 70 (80) | 수집 없음 (100) |
OK  | r1 | 19,620 | 20,985 |
OK  | r2 | 19,685 | 20,764 |
OK  | r3 | 19,635 | 20,835 |
OK  | 217MB / 185MB |
OK  | 204MB / 204MB |
OK  | 241MB / 181MB |
stale '0 / 1' in bottleneck: False
ALL_MATCH
```

s2/r3 사본으로 새 행 이름 정합성 표 생성(write_integrity, 원본 integrity.md는 다시 만들지 않음):

```
| 실패 이체 멱등키 기록 수 vs k6 이체 체크 실패 수(자기 검증) | 2 vs 0 | 키가 더 많음: k6 중단(SIGINT) 순간 끊긴 요청은 키가 남지만 체크 집계에는 들어가지 않는다 |
| 실패 키 중 DB 반영(멱등키 대조, k6 중단 순간 끊긴 요청의 키 포함) | 1 (0: 2건 중 반영 1) | 실패 응답(체크 실패) 중 반영 하한 0 |
| k6 중단 순간 처리 중이던 요청의 반영(잔차 = 새 완료 − 201 − 실패 중 반영) | 0 |  |
```
(위 셋째 행은 행 이름 변경 커밋 9f0518a 직전 출력이다. 9f0518a 이후 이름은 "키가 남지 않은 중단 순간 요청의 반영(잔차 = 새 완료 − 201 − 실패 키 중 반영)".)

api_log_lines 계산 확인(개선 1 반영 후):

```
$ grep -c '"log.level"' (compose 에러 한 줄 파일) → 0
$ grep -c '"log.level"' (앱 로그 2줄 파일)        → 2
```
