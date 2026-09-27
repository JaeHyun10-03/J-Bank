# 계획 리뷰

(하네스는 아래 `대상 계획 지문`·`최종 판정` 줄을 한 번만 읽는다. 지난 회차는 회차 이름을 붙여 보존했다.)

## 1회차

1회차 계획 지문: 7a1ed05813bf4a2c77e187c4c851a7c5c1186c9cc5b9a07de635a20ffdf448e8
1회차 판정: 수정 필요

### verifier 응답 (원문 요지 보존)

- [차단] 1. "미사용" SVG가 실제로 사용 중 — `apps/frontend/app/products/[productCode]/_details/j-kids.tsx:102`가 `` `/products/j-kids/icon-chevron-${open ? "up" : "down"}.svg` ``로 경로를 조립한다. 지우면 j-kids 아코디언 펼침 시 404. `git grep icon-chevron-up`과 lint·tsc·Jest·build 모두 이를 못 잡아 거짓 통과. 삭제 대상에서 제외하고 사용자에게 알릴 것.
- [중요] 2. `tests/test_workflow.py:294`의 `'python3 -m unittest discover -s tests'` 문자열이 조사 목록에 없고 REQ-09 검색에 걸림. `-s .claude/tests`로 바꾸는 것을 커밋 범위에 추가할 것(Bash Hook 허용 확인 입력일 뿐 의미 불변).
- [중요] 3. REQ-09 명령이 표 안에서 `\|`로 이스케이프돼 그대로 쓰면 아무것도 못 찾는 거짓 통과 위험, `\b`는 macOS git grep -E 지원 불확실. 표 밖 코드 블록에 실제 명령을 적고, 이동 전에 알려진 위치가 잡히는지 양성 대조를 추가할 것.
- [제안] 4. README.md:81, docs/README.md:137의 "infra/ … Dockerfile" 문구는 자동 검색 밖 — 수동 확인 항목으로 명시.
- [제안] 5. "트리가 실제 구조와 맞는다"는 기준이 넓음 — 이번에 옮기거나 지운 경로 반영으로 범위를 좁힐 것.
- [제안] 6. OPS-01 이미지 ID 기록, OPS-02 PyYAML 설치 확인, 원격 CI 미실행은 최종 보고에 미확인으로 남길 것.
- 참조 조사 누락은 294행 하나뿐. ROOT `parents[2]`, Docker 컨텍스트, CI paths, perf ROOT_DIR, terraform validate 가능성은 타당. OPS-03~08·성능·보안·AI·E2E 해당 없음 타당.

### 처리

1. 수용. SVG 삭제를 범위에서 제외(REQ-02를 프론트 회귀 확인으로 변경, 커밋 계획에서 삭제). 최종 보고에서 사용자에게 점검 오류를 알린다.
2. 수용. 조사 사실과 커밋 6(번호 재정렬 후) 범위에 294행 변경 추가.
3. 수용. REQ-09 명령을 코드 블록으로 분리, `\b` 대신 `(^|[^-a-z])todo/` 사용, 이동 전 양성 대조를 완료 기준에 추가.
4. 수용. REQ-09 시나리오에 수동 확인 항목으로 명시.
5. 수용. REQ-09를 이번 변경 경로로 한정.
6. 수용. PyYAML 설치 확인(`yaml ok`) 완료, 이미지 ID 기록과 원격 CI 미확인 보고를 완료 기준에 추가.

### 1회차 반영 후 추가 확인 (메인)

- REQ-09 검색 명령 이동 전 양성 대조 실행: backend-cd.yml:8·40, harness-ci.yml:10·28, README.md:82·263, docs/10:163, docs/11:30, docs/README.md:138, harness-guide.md:151, testing-policy.md:71, docker-compose.yml:32, perf/README.md:20·78·159·237·337, perf/run-10m.sh:114, scripts/perf.sh:5·6, tests/test_workflow.py:294 검출.
- checks.json의 `"-s", "tests"` 배열 표기가 잡히지 않아 패턴에 추가 → 재실행 22건(checks.json:3 포함).
- 검색이 못 잡는 표기는 수동 확인 목록으로 task.md에 추가.

## 2회차 (이어진 대화로 수행, 하네스 상태 미등록)

2회차 판정: 통과 권고 (verifier 응답 요지: 1회차 지적 1~6 모두 해결, 새 차단·중요 없음. docs/10:180·206은 패턴 밖이지만 수동 확인 대상. 제안: SVG 제외 사실을 start 전 사용자에게 알릴 것.)
주의: SendMessage로 이어서 받은 응답이라 Agent Hook이 호출을 기록하지 않았다. 그래서 같은 계획으로 3회차 정식 호출을 했다.

## 3회차 (정식 호출, 재리뷰 2/2)

3회차 계획 지문: a904c88064b0dfb88a5954243a7747f35174e9b87b2c1673cfb300422e7ae4ab
3회차 판정: 통과 권고

### verifier 응답 요지

- 1회차 지적 1~6 모두 해결 재확인(j-kids.tsx:102 사용 확인, 294행 새 문자열은 BASH_DENY 미해당, REQ-09 이동 후 새 표기는 패턴 미검출).
- 회귀 없음: copytree가 .claude/tests를 복사해도 영향 없음(실행으로 확인 예정), compose `dockerfile: Dockerfile`, backend-cd paths `apps/jbank-api/**`가 새 위치 포함, perf 스크립트 ROOT_DIR 동일.
- REQ-01~10 모두 시나리오·명령·시점 연결. Q-01~04 사용자 답변 반영, 임의 가정 없음. 적용 제외 모두 타당.
- 차단·중요 없음. [제안] 사용자가 terraform 잔여 폴더를 지웠으면 `ls infra/terraform/modules` 결과를 최종 보고에 덧붙일 것.

### 처리

- 제안 수용: 최종 보고에 반영. SVG 제외는 구현 전 사용자에게 알린다.

## 4회차 (사용자 허락으로 한도 1회 추가)

사유: start가 `요청 해석:` 같은 줄 내용 누락으로 거부. 같은 줄에 한 줄 요약만 추가(요구사항·기준 불변). 계획 지문이 바뀌어 재호출. 사용자 허락: "추가 1회 허용".

4회차 계획 지문: 84bc4c5d2d73e1b1a9b49ab04ebc9cc75dba475d9d1cf18202007b68f9ee1d7a
4회차 판정: 통과 권고

### verifier 응답 요지

- 요청 해석 줄이 workflow.py:219-222 형식 조건 충족, 원문 요청과 세부에 맞고 새 결정 없음.
- REQ-01~10, Q-01~04, 커밋 계획, 적용 영역 판단이 3회차 기록과 같음. 차단·중요 없음.
- 한계: 3회차 원본이 없어 줄 단위 대조는 못 하고 기록 요지와 비교함.

## 5회차 (사용자 허락으로 한도 1회 추가)

사유: 커밋 5(46ec79d)에서 계획에 적힌 대로 `docs/testing-policy.md:71`의 `scripts/perf.sh`를 `perf/run-transfer.sh`로 고쳤다. 이 파일이 계획 지문 입력이라 지문이 바뀌어 코드 수정 Hook이 막혔다. task.md는 불변. 사용자 허락: "추가 1회 허용".

대상 계획 지문: 5e054ab8d64f1aefe6453cfce26b9a9e81f3da49d66ec1eecbbe26bc149ca3e4
최종 판정: 통과 권고

### verifier 응답 요지

- testing-policy.md:71은 성능 명령 경로만 바뀐 순수 갱신. 영역·시점·판정 기준 불변, 커밋 계획 5번 범위 안.
- perf/run-transfer.sh 존재, 사용법·exit 1·ROOT_DIR 유지, scripts/perf.sh 없음. task.md는 4회차 통과본과 같고 커밋 6~8 계획 유효.
- 차단·중요 없음. 제안: 결과 리뷰에 `git show 46ec79d -- docs/testing-policy.md` diff 전달, testing-policy가 계획 지문 입력이라는 점을 보고에 기록.
