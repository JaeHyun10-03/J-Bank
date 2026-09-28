# 계획 리뷰

## 1회차

회차 계획 지문: 1716c55b7855c7b438a4326b25a37681482093c4225ab000c8cbda67c3066db2
회차 판정: 수정 필요

### verifier 응답 원문

- 모드: 계획 리뷰 1회차. 작업 ID: ec2-load-test-followup. 대상: `.claude/tasks/ec2-load-test-followup/task.md` 현재 판(질문 표 비어 있음, 명세 상태 확정).
- 읽은 자료: docs/testing-policy.md, docs/testing/performance.md, task.md, ec2-load-test/review.md의 3/3 개선-1~5와 처리 내역(:277-301, :383-389), perf/run-ec2.py:294-444, perf/ec2/target.sh:13, :100-289, perf/ec2/k6_monitor.py:80, perf/README.md:405-421, summary.md:1-63, bottleneck-analysis.md에서 grep한 줄, first-pass/summary.md:1-10, s1·s2·s3·s5 전 회차의 analysis.json(dropped_iterations·mem_available_min·collapse), k6-summary.json(dropped_iterations.count), s2/r1·r3의 integrity.md, s2/r3/target/failed-keys-target.txt, 12개 회차의 log-counts.txt, .git/logs/HEAD.
- 적용 기준: PERF-01, PERF-06, PERF-07. PERF-02~05와 나머지 영역을 해당 없음으로 둔 판단은 타당합니다(앱·인프라 변경이 없고 새 측정도 없음).
- 확인하지 못한 범위: stop-api 뒤 실제 EC2에서 돌아가는 동작(실행 사실이라 읽기만으로는 확인 불가), 해시 대응표의 "내용 동일"(트리 비교는 git 실행이 필요함).

## 원자료와 대조한 결과 (일치)
- S5 메모리: overall_system.mem_available_min 185,159,680 / 203,911,168 / 181,403,648 바이트 = 185/204/181MB(10^6 기준). 배치 구간 217/204/241MB도 analysis.json 값과 같습니다.
- S2 r3: failed-keys-target.txt:5-8에서 키 2건(상태 0) 중 반영 1건, integrity.md:6-8에서 체크 실패 0, 잔차 0. r1은 integrity.md:6-8에서 키 0, 체크 실패 0, 잔차 1. task.md:11과 같습니다.
- 무너진 단계: S1은 3회 모두 100(collapse ["p95"]), S2는 80/100/80(collapse_rps). 조사 사실과 같습니다.
- stop-api 선행 시 동작: target.sh:128은 `stop api metrics-proxy caddy`만 멈춥니다. postgres는 계속 떠 있어서 psql_q(target.sh:13, `exec -T postgres`)가 integrity와 failed-keys 모두에서 동작합니다. 실패 키 파일 복사는 loadgen→target 파일 복사라 api와 관계가 없습니다. `docker compose logs`는 멈춘(삭제되지 않은) 컨테이너의 로그도 읽습니다. 지금 순서에서도 logs는 이미 stop-api와 batch 뒤에 실행되고 있어서(run-ec2.py:352-355) 로그 쪽 회귀는 없습니다. 정지 전에 스냅샷을 찍는 경로는 cmd_run:348 하나뿐이고, noload·regen-integrity는 integrity를 호출하지 않습니다.

## 판정: 수정 필요

### 중요-1 (PERF-07, REQ-01): 드롭 "수"가 Prometheus 추정치인데 정확한 개수처럼 명세되어 있습니다
- 근거: k6_monitor.py:80은 `sum(increase(k6_dropped_iterations_total[..]))`로 계산합니다. 그래서 값이 소수이고(1048.8858…, 20985.1056…), 표본이 부족하면 0이 아니라 `null`이 나옵니다.
- S3 스파이크 구간 값이 k6 전체 드롭 수보다 큽니다.
  - r1: 20,985 > 19,620 (s3/r1/loadgen/k6-summary.json:175-177)
  - r2: 20,764 > 19,685
  - r3: 20,835 > 19,635
  - 이는 increase()의 외삽 때문에 과대 추정된 것입니다.
- S1 100 단계의 `"dropped_iterations": null`을 task.md:10은 "0/0/0"으로 적었습니다. 그런데 k6 전체 수(1,628/1,549/1,588)와 단계 값의 합(1,293/1,174/1,124)이 335~464건 차이 납니다. 이 차이가 100 단계에서 났을 수도 있어서 null=0이라는 근거가 없습니다. S2 r3도 단계 값 70과 전체 99가 다릅니다.
- 영향: REQ-01의 통과 기준 "analysis.json 값과 한 값이라도 다르면 실패"를 지키면, 부정확한 추정치(S3는 전체보다 큰 값)를 "드롭 수"로 확정해 적게 됩니다. null을 0으로 바꾸는 일도 기준상 "불일치"인데 처리 규칙이 없습니다.
- 수정:
  - 조사한 사실과 REQ-01에 다음을 명시합니다.
    - (a) 단계별 값은 Prometheus increase() 추정치이며 반올림 규칙을 정합니다.
    - (b) null은 "수집 없음"으로 적고 0으로 바꾸지 않습니다.
    - (c) 회차별 k6-summary.json의 `dropped_iterations.count`를 확정 전체 수로 함께 적습니다. S2 r1과 S5 r1~r3은 이 항목이 없으니 0으로 확정됩니다.
  - 대조 스크립트의 통과 기준에 위 규칙을 포함합니다.

### 중요-2 (commits.md): 커밋 계획에 개발일지가 없습니다
- 근거: .claude/rules/commits.md는 "커밋(들)이 끝나면 docs/devlog/에 개발일지를 기록"하라고 정합니다. task.md:43-48의 커밋 계획에는 devlog 항목이 없습니다. 직전 작업은 `docs(devlog)` 커밋(f1f544b)을 따로 두었습니다.
- 수정: `docs(devlog)` 커밋을 추가하거나, 어느 커밋에 개발일지를 넣는지 명시합니다.

### 제안-1: 문구가 섞이는 원인이 생성기에 남아 있습니다
- 근거: run-ec2.py:422의 행 이름 "실패 응답인데 실제 반영된 이체"가 SIGINT로 끊긴 키(상태 0)까지 합산합니다. s2/r3/integrity.md:7의 "1 (0: 2건 중 반영 1)"이 이렇게 만들어졌습니다. REQ-02는 요약 문서만 고치므로 다음 측정의 integrity.md에도 같은 섞인 표현이 다시 나옵니다.
- 수정: 커밋 1에서 run-ec2.py를 어차피 고치니, 행 이름을 "실패 키 중 반영(끊긴 요청 포함)"처럼 바꾸는 것을 검토할 수 있습니다. 기존 integrity.md는 다시 만들지 않습니다(원자료 보존). 범위 밖으로 두려면 "하지 않을 일"에 이유를 적어 둡니다.

### 제안-2 (PERF-01): 순서 변경의 비교 영향과 로그 확인
- task.md:16은 순서 변경이 "측정 결과 해석을 바꾸지 않는다"고 적었습니다. 하지만 api를 멈추면 대기열 요청이 커밋되지 않고 끊깁니다. 그래서 다음 측정의 "실패 키 반영" 수는 기준선(bottleneck-analysis.md:65의 비교 기준 2,672건)보다 조금 작게 나올 수 있습니다. 기준선의 잔차가 모두 0 이상이라 영향은 작을 것으로 보입니다.
- 수정: 비교할 때 조건 차이로 적도록 README나 task에 한 줄을 남깁니다. 가능하면 k6 종료·stop-api·integrity 시각을 기록하도록 합니다.
- 12개 회차 모두 api_error_lines와 api_warn_lines가 0입니다(S3처럼 타임아웃 12,000건이 난 회차 포함). 그래서 api 로그 수집이 실제로 되는지는 기존 증거로 보이지 않습니다. REQ-05의 "정지된 api 로그 포함"은 이번 작업에서 정적 검사로만 확인되므로, 다음 드라이런의 확인 항목(api-full 줄 수 > 0)으로 명시할 것을 권합니다.

### 제안-3: S5 메모리 표기
- 단위가 10^6 바이트 기준 MB임을 명시하고, 회차 전체 최저 열의 대표값(중앙값 185MB)을 정해 둡니다. REQ-03의 "대표값 문장도 맞는다"가 무엇을 뜻하는지 불분명합니다.

## 기타 확인
- 질문 누락: 개선-1~5는 review.md가 수정 방향을 제시했으므로 질문하지 않은 근거는 타당합니다. 다만 드롭 수 표기 규칙(중요-1)은 원자료에서 바로 정해지지 않으니 계획에서 확정해야 합니다.
- REQ↔기준 연결: REQ-01은 PERF-07, REQ-02·03은 PERF-06, REQ-04~06은 PERF-01에 연결되어 있고, 일반 테스트(verify)도 연결되어 있습니다. REQ-04의 결함 목록(지표 공백, 재로그인 포화, 반영 수치 혼입, 배치 건수 누락)은 ec2-load-test/progress.md:58과 review.md:241에서 근거를 확인했습니다.
- 해시 대응: .git/logs/HEAD:617, :627에서 479eefc→b4f097a(amend, 메시지 변경), 51286d8→a870ee0(amend)이 확인됩니다. "내용 동일"은 트리 비교가 필요해서 확인하지 못했습니다.
- 이전 지적: 1회차라 없음.

(원문 마지막 줄) 최종 판정: 수정 필요

### 처리 내역

- 중요-1: 조사 사실에 확정 전체 수(k6-summary.json count, 없으면 0)와 추정치(analysis.json, increase() 추정·null)를 나눠 적고, REQ-01을 "확정값 표기 + 추정치는 정수 반올림·'추정' 표시 + null은 '수집 없음' + S3 외삽 과대 표시"로 교체. 대조 기준도 이 규칙으로.
- 중요-2: 커밋 계획에 `docs(devlog)` 추가(커밋 5).
- 제안-1: 반영. 정합성 표 실패 키 행 이름 변경을 REQ-05에 넣고 커밋 2로 분리. 기존 integrity.md는 재생성하지 않음.
- 제안-2: 조사 사실에 비교 영향과 api 로그 미확인을 적고, REQ-06에 README 비교 조건 차이·다음 드라이런 확인 항목(api-full 로그 줄 수 > 0) 추가. 시각은 commands.log에 명령마다 남는다.
- 제안-3: REQ-03에 단위(MB=10^6바이트)와 대표값(배치 구간 217MB, 전체 185MB 중앙값) 명시.

## 2회차

대상 계획 지문: 25b244dea7babf0d73046d6a8db41c9cd720b6000f47301e7efb1f3b43f0ea00
최종 판정: 통과 권고

### verifier 응답 원문

- 모드: 계획 리뷰 2회차(재리뷰 1/2). 작업 ID: ec2-load-test-followup. 대상: `.claude/tasks/ec2-load-test-followup/task.md`의 1회차 반영본.
- 읽은 자료:
  - `/Users/imjaehyeon/Documents/[01] - Project/j-bank/.claude/tasks/ec2-load-test-followup/task.md`, `plan-review.md`
  - `perf/run-ec2.py:246-250, 294-368, 400-444`
  - S1·S2·S3 전 회차 `loadgen/analysis.json`의 dropped_iterations
  - `k6-summary.json`의 dropped_iterations.count, 전 시나리오
  - `summary.md`·`bottleneck-analysis.md`에서 MB, "0 / 1", 드롭을 grep한 줄
  - `.git/logs/HEAD:608-628`
- 적용 기준: PERF-01, PERF-06, PERF-07. PERF-02~05를 해당 없음으로 둔 판단은 1회차와 같이 타당합니다.
- 확인하지 못한 범위:
  - stop-api 뒤 EC2에서의 실제 동작. 실행해야 알 수 있는 사실이며, 계획대로 다음 드라이런에서 확인합니다.
  - 해시 대응의 "내용 동일". 트리 비교가 필요합니다.

## 원자료 대조 (모두 일치)
- **확정 드롭 수(k6-summary.json count)**
  - S1 1,628 / 1,549 / 1,588 (s1/r1:79, r2:88, r3:46)
  - S2 r2 1,403 (:132), r3 99 (:168)
  - S3 19,620 / 19,685 / 19,635 (r1:176, r2:82, r3:126)
  - s2/r1과 s5/r1~r3는 파일은 있지만 dropped_iterations 항목이 없습니다. task.md:11과 같습니다.
- **추정치(analysis.json)**
  - S1 150 단계 1267.13 / 1048.89 / 1124.32, 100 단계 null×3
  - S2 r1 80 null, r2 100 778.15, r3 80 70.0
  - S3 spike_system 20985.1 / 20764.0 / 20834.8
  - 모두 task.md:12와 같고, 정수로 반올림해도 같습니다.
- S5 메모리와 S2 r1·r3 키 값은 1회차에서 확인했고, 이번에 새로 대조할 근거는 없습니다.

## 이전 지적 처리
- **중요-1: 해결.** task.md:10-12에서 확정값과 추정치를 나눴고, REQ-01(:41)에 반올림, "추정" 표시, null은 "수집 없음", S3 외삽 과대 표시, 불일치 시 실패 규칙이 들어갔습니다.
- **중요-2: 해결.** 커밋 5에 `docs(devlog)`가 있습니다(:54).
- **제안-1: 해결.** REQ-05(:45)에 행 이름 변경과 기존 integrity.md를 다시 만들지 않는다는 점이 들어갔고, 커밋 2(:51)로 분리했습니다.
- **제안-2: 해결.**
  - 조사 사실(:17)에 비교 영향과 api 로그 미확인이 적혔습니다.
  - REQ-06(:46)에 README 비교 조건 차이와 드라이런 확인 항목(api-full 줄 수 > 0)이 들어갔습니다.
  - REQ-05 순서(분석·export → stop-api → integrity → 실패 키 → 대사 → 로그)는 현재 run-ec2.py:346-355 구조에서 348~351을 352 뒤로 옮기면 그대로 됩니다. S5 noload 구간(:357-366)은 restore로 다시 시작하므로 영향이 없습니다.
- **제안-3: 해결.** REQ-03(:43)에 단위, 열 구분, 두 중앙값(217MB, 185MB)과 가설 E 서술 기준이 들어갔습니다. 현재 가설 E 서술은 bottleneck-analysis.md:25의 "S5 배치 구간 204~241MB"입니다.

## 새 지적 (차단·중요 없음, 모두 개선)
1. **개선 (PERF-07): 시점 표기가 커밋 번호와 어긋납니다.**
   - 근거: task.md:76의 PERF-07 시점은 "커밋 2 후"입니다. 그런데 커밋 계획이 바뀌어 드롭 수 문서 수정은 커밋 3(:52)이고, 커밋 2는 행 이름 변경입니다. 일반 테스트 표(:87)는 "커밋 3 후"로 적혀 있습니다.
   - 영향: 문서 수정 전에 대조할 수 있다는 기록상 혼동이 생깁니다.
   - 수정: "커밋 3 후"로 고칩니다.
2. **개선 (REQ-01): S2의 "한 단계 더 간 단계" 기대값이 조사 사실에 없습니다.**
   - 근거: REQ-01은 S1·S2 모두 "무너진 단계와 한 단계 더 간 단계"를 요구합니다. 그런데 task.md:12에는 S2의 무너진 단계만 있습니다.
   - 원자료 값: r1 100 단계 null, r2 120 단계 276.10(→276), r3 100 단계 null.
   - 영향: 규칙으로 뽑을 수 있어서 결함은 아니지만, 대조 기준을 미리 적어 두면 리뷰할 때 모호함이 없습니다.
   - 수정: 조사 사실에 위 세 값을 추가합니다.
3. **개선 (REQ-06): 해시 대응표의 "내용 동일"이 확인되지 않은 주장입니다.**
   - 근거: `.git/logs/HEAD`의 흐름은 다음과 같습니다.
     - :609 479eefc→a2f21f7 amend
     - :617 479eefc→b4f097a amend, 메시지가 "perf(results): 1차 측정 결과를 first-pass로 이동"으로 바뀜
     - :618 b4f097a 위에 51286d8 pick
     - :627 51286d8→a870ee0 amend
   - task.md:20의 "a2f21f7 → a870ee0"은 이 흐름과 맞습니다. 다만 amend로 내용이 바뀌었는지는 로그만으로 알 수 없습니다.
   - 수정: 구현할 때 `git diff 479eefc b4f097a`, `git diff a2f21f7 a870ee0`으로 트리를 비교합니다. 결과를 progress.md 대응표에 적거나, "메시지·기준 커밋 변경"으로만 적습니다.

## 기타
- 모순, 실행 불가, REQ 연결 누락은 없습니다. 각 연결은 다음과 같습니다.
  - REQ-01 → PERF-07
  - REQ-02·03 → PERF-06
  - REQ-04~06 → PERF-01, 그리고 일반 테스트 표
- 질문하지 않은 근거(:21)는 드롭 표기 규칙을 원자료의 성격에서 도출했으므로 타당합니다. 사용자가 정해야 할 제품 결정도 남아 있지 않습니다.

(원문 마지막 줄) 최종 판정: 통과 권고

### 처리 내역

task.md를 고치면 통과 지문이 무효가 되므로 명세는 그대로 두고 구현에서 따른다: PERF-07 대조는 커밋 3(문서 수정) 뒤에 한다, S2 한 단계 더 간 단계 기대값(r1 100 null, r2 120 276, r3 100 null)을 대조에 넣는다, 해시 대응은 git diff로 트리를 비교해 progress.md에 적는다.
