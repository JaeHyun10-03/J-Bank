# 작업 명세

## 요구사항 구체화

명세 상태: 확정
원문 요청: 1,2 지우고 3번에서 todo는 docs안으로 옮기자. 3번에 있는 나머지는 진행시켜. 문제없도록 해줘.
(직전 점검 보고의 1번=로컬 잔여물, 2번=Git 안 불필요 파일, 3번=구조 정리 제안을 가리킨다.)
요청 해석: 직전 점검 보고의 1·2번을 정리하고 3번의 이동을 모두 수행하되, 참조를 갱신해 동작이 깨지지 않게 한다. 세부는 아래와 같다.
- 1번: Git 밖 로컬 잔여물 삭제 — `infra/terraform/modules/`의 compute·data·gitops·network·secrets·security(`.terraform` 캐시·lock만 남은 v1.0.0 흔적), 빈 `contracts/bruno/`, `.local-logs/pr-infra-ec2-body.md`, `.DS_Store`.
- 2번: `apps/frontend/README.md`(create-next-app 기본문) 정리, `infra/terraform/bootstrap/oidc.tf` 82행 부근의 EKS 전제 주석 현행화.
- 3번: `todo/` → `docs/roadmap/`, `tests/test_workflow.py` → `.claude/tests/`, `infra/docker/jbank-api/Dockerfile` → `apps/jbank-api/Dockerfile`, `scripts/perf.sh` → `perf/run-transfer.sh`.
- "문제없도록": 옮긴 경로를 참조하는 실행 설정(compose·CI·checks.json·스크립트)과 살아 있는 문서를 모두 갱신하고 실제 실행으로 확인한다.
조사한 사실:
- 참조 위치(개발일지 제외): `infra/compose/docker-compose.yml:32`(dockerfile), `.github/workflows/backend-cd.yml:8,40`, `.github/workflows/harness-ci.yml:10,28`, `.claude/checks.json`(unittest `-s tests`), `perf/run-10m.sh:114`, `scripts/perf.sh:5-6`(사용법), `perf/README.md:20,27,78,159,237,337`, `docs/testing-policy.md:71`, `docs/harness-guide.md:114,151`, `.claude/README.md:151`와 구조 트리, `docs/10_J-Bank_폴더구조.md`(트리의 bruno·docker·scripts/perf.sh), `docs/11_J-Bank_알려진한계와개선과제.md:30`, `README.md:81-82,263`, `docs/README.md:137-138`.
- `tests/test_workflow.py`는 `ROOT = Path(__file__).resolve().parents[1]`로 저장소 루트를 잡는다. `.claude/tests/`로 옮기면 `parents[2]`가 루트다.
- `workflow.py` 스냅샷 제외는 `.claude/tasks`뿐이라 `.claude/tests`는 검사 입력 지문에 포함된다.
- `docker-compose.prod.yml`은 GHCR 이미지를 쓰고 Dockerfile을 참조하지 않는다. EC2 배포 경로는 영향 없음.
- `scripts/perf.sh`는 `ROOT_DIR=$(dirname)/..`을 쓴다. `perf/` 역시 루트 한 단계 아래라 그대로 동작한다.
- 하네스 Hook(`BASH_DENY`)과 settings.json은 `rm -r` 계열을 금지한다. terraform 잔여 폴더는 하위에 `.terraform/` 디렉터리가 있어 재귀 삭제가 필요하다.
- Docker Desktop(`desktop-linux` 컨텍스트)이 현재 꺼져 있다. verify·이미지 빌드 전에 켜야 한다.
- 정정: 점검 보고에서 미사용이라 한 `apps/frontend/public/products/j-kids/icon-chevron-up.svg`는 `j-kids.tsx:102`가 `icon-chevron-${open ? "up" : "down"}.svg`로 조립해 사용 중이다(계획 리뷰 1회차). 삭제하지 않는다.
- `tests/test_workflow.py:294`의 Bash Hook 허용 입력 문자열 `python3 -m unittest discover -s tests`도 옛 경로 표기다.
- PyYAML 설치 확인(`yaml ok`).

| 질문 ID | 결정 사항과 영향 | 사용자 답변·근거 | 상태 |
| --- | --- | --- | --- |
| Q-01 | todo를 옮길 docs 하위 폴더 이름 | `docs/roadmap/` | 해결 |
| Q-02 | 개발일지(`docs/devlog/`) 속 옛 경로 표기 치환 여부 | 그대로 둔다(당시 기록 보존, 링크 아닌 글자 표기). 같은 이유로 과거 체크리스트인 `docs/roadmap/W*.md` 본문도 수정하지 않는다 | 해결 |
| Q-03 | `scripts/perf.sh`의 새 이름 | `perf/run-transfer.sh` | 해결 |
| Q-04 | 재귀 삭제가 필요한 terraform 잔여 폴더 처리 | 하네스가 `rm -r`을 금지하므로 우회하지 않는다. 사용자에게 실행할 명령을 안내하고 사용자가 직접 실행한다 | 해결 |

## 목표

J-Bank 저장소에서 쓰지 않는 파일과 잔여물을 없애고, 어색한 위치의 파일을 제자리로 옮기되 빌드·배포·하네스·성능 스크립트가 옮기기 전과 똑같이 동작하게 한다.

## 범위

- 로컬 잔여물 삭제(Git 기록 무관): `contracts/bruno/`, `.local-logs/pr-infra-ec2-body.md`, 저장소 안 `.DS_Store`(node_modules 제외). terraform 잔여 6개 폴더는 사용자 실행 명령 안내.
- Git 안: 프론트 README 교체, oidc.tf 주석 현행화. (SVG는 사용 중이라 제외)
- 이동: todo·하네스 테스트·Dockerfile·perf 스크립트와 모든 실행 설정·살아 있는 문서 참조 갱신.

## 요구사항

| ID | 조건·입력 | 기대 동작·실패 조건 |
| --- | --- | --- |
| REQ-01 | 로컬 잔여물 정리 후 | `contracts/bruno/`, `.local-logs/pr-infra-ec2-body.md`, node_modules 밖 `.DS_Store`가 없다. terraform 잔여 6개 폴더 삭제 명령이 사용자에게 안내된다. 남아 있으면 실패 |
| REQ-02 | 프론트 회귀 | `apps/frontend/public/products/j-kids/icon-chevron-up.svg`가 그대로 남아 있고(사용 중), 프론트 lint·tsc·Jest·build가 통과한다. 파일이 사라지거나 검사 실패면 실패 |
| REQ-03 | `apps/frontend/README.md` | create-next-app 기본 문구가 없고 프론트 실행·테스트 명령과 루트 README 안내를 담는다 |
| REQ-04 | `infra/terraform/bootstrap/oidc.tf` | EKS·RDS·ElastiCache를 만드는 모듈이 있다고 읽히는 주석이 현재 구성(ec2 모듈)에 맞게 바뀐다. 코드(리소스·정책) 변경 없음, `terraform fmt -check`와 `validate` 통과 |
| REQ-05 | `docs/roadmap/` | W1~W7이 내용 변경 없이 이동(Git rename)하고 루트 `todo/`가 없다. README·perf/README·docs/11의 `todo/` 참조가 `docs/roadmap/`을 가리킨다. 개발일지와 roadmap 본문은 수정하지 않는다 |
| REQ-06 | 하네스 테스트 실행 | `.claude/tests/test_workflow.py`가 저장소 루트를 올바르게 찾고(허용 입력 문자열도 `-s .claude/tests`로 갱신) `python3 -m unittest discover -s .claude/tests -v`가 전부 통과한다. checks.json·harness-ci.yml(경로 필터와 실행 명령)·harness-guide·.claude/README가 새 경로를 가리킨다. 루트 `tests/`가 없다 |
| REQ-07 | 백엔드 이미지 빌드 | `apps/jbank-api/Dockerfile`로 로컬 compose(`--profile api`)와 backend-cd의 `docker build`가 같은 이미지를 만든다. `infra/docker/`가 없고 backend-cd paths 필터가 Dockerfile 변경을 계속 잡는다. 빌드 실패·옛 경로 참조가 남으면 실패 |
| REQ-08 | 이체 부하 스크립트 | `perf/run-transfer.sh`가 인자 부족 시 사용법을 출력하고 종료 코드 1을 낸다. `perf/run-10m.sh`가 새 경로를 호출한다. `scripts/perf.sh`가 없다 |
| REQ-09 | 참조 무결성 | 개발일지·roadmap·작업 기록을 제외한 추적 파일에 `infra/docker`, `scripts/perf.sh`, `todo/`(디렉터리 참조), `-s tests`, `"tests/**"`, `bruno`, `수동 호출 컬렉션` 표기가 남지 않는다. README.md·docs/README.md의 infra 설명에서 Dockerfile이 빠지고 apps/jbank-api 쪽에 적힌다. docs/10 트리에 이번에 옮기거나 지운 경로(infra/docker 제거·apps/jbank-api/Dockerfile·contracts/bruno 제거·scripts/perf.sh 제거·perf/run-transfer.sh·docs/roadmap)가 반영된다(트리의 다른 기존 편차는 범위 밖) |
| REQ-10 | 기존 필수 검사 | `workflow.py verify`(checks.json 전체: 하네스 테스트·프론트 lint/tsc/Jest/build·백엔드 test+spotlessCheck)가 통과한다 |

## 완료 기준

- REQ-01~10이 아래 명령의 실제 결과로 확인된다.
- REQ-09 검색 명령은 이동 전 양성 대조에서 알려진 위치(backend-cd.yml, perf/run-10m.sh, harness-ci.yml, checks.json, README.md 263행)를 잡아야 유효하다.
  (이동 전 1차 대조에서 checks.json의 JSON 배열 표기 `"-s", "tests"`가 안 잡혀 패턴을 보강했다.)
- 검색 밖 수동 확인: `perf/README.md:27`의 `perf.sh` 단독 표기, `docs/harness-guide.md:114`·`.claude/README.md:151`의 Hook 설명 속 `tests/`, README·docs/README의 infra 설명 속 Dockerfile, docs/10 트리.
- OPS-01 빌드 이미지 ID를 progress.md에 남긴다.
- 원격 GitHub Actions 실제 실행은 이번 작업에서 확인하지 못한 항목으로 최종 보고에 남긴다(통과로 적지 않음).
- 커밋 계획대로 원자 커밋이 나뉘고 각 커밋 후 빌드 가능한 상태다.
- `docs/devlog/2026-09-27_저장소정리.md` 개발일지가 있다.

## 커밋 계획

1. `docs(frontend): create-next-app 기본 README를 프로젝트 안내로 교체`
2. `chore(infra): bootstrap OIDC 주석에서 EKS 전제 제거`
3. `docs: todo를 docs/roadmap으로 이동하고 참조 갱신`
4. `build(api): Dockerfile을 apps/jbank-api로 이동` (compose·backend-cd·문서 트리 포함)
5. `chore(perf): scripts/perf.sh를 perf/run-transfer.sh로 이동` (run-10m·perf/README·testing-policy·폴더구조 포함)
6. `chore(harness): 하네스 테스트를 .claude/tests로 이동` (test_workflow.py ROOT·294행 문자열, checks.json·harness-ci·harness-guide·.claude/README 포함)
7. `docs: contracts 트리에서 bruno 컬렉션 표기 제거`
8. `docs(devlog): 저장소 정리 기록` (작업 기록 포함)

로컬 잔여물 삭제는 Git 변경이 없어 커밋하지 않는다.

## 하지 않을 일

- 개발일지·roadmap 본문의 옛 경로 치환(Q-02).
- `rm -r` 우회(find -delete 등)로 terraform 잔여 폴더 삭제(Q-04).
- 백엔드 `common`/`global` 등 코드 구조 변경, 설계 문서 내용 개편.
- GitHub Actions 실제 실행(원격 push 필요). 로컬에서 YAML 파싱과 명령 재현으로 대신한다.

## 적용 영역과 상세 기준

- 프론트: 적용 — README 문서만 변경(정적 자산 삭제는 계획 리뷰로 제외). 화면 동작 변경 없음. 필수 검사(lint·tsc·Jest·build)로 회귀 확인. 화면 상세 기준(testing/frontend.md) 항목은 UI 동작 변경이 없어 해당 없음.
- 백엔드·데이터: 소스 변경 없음. Dockerfile 위치만 바뀌므로 verify의 백엔드 테스트로 회귀만 확인.
- AI: 해당 없음 — 제품 AI 기능 없음.
- DevOps: 적용 — 빌드·CI 경로 변경.
- 보안: 해당 없음 — 권한·비밀·외부 입력 변경 없음(`.env` 등 비밀 파일은 건드리지 않음).
- 성능: 해당 없음 — 스크립트 위치만 바뀌고 측정 로직 불변.
- 전체 흐름(E2E): 해당 없음 — 사용자 기능 변경 없음.

| 기준 ID | 완료 기준·시나리오 | 기대 결과·수치 목표 | 실행 명령 | 시점 | 증거 위치 |
| --- | --- | --- | --- | --- | --- |
| OPS-01 | REQ-07 새 Dockerfile 경로로 깨끗한 빌드 | 이미지 빌드 성공, compose 설정이 새 경로로 해석 | `docker build -f apps/jbank-api/Dockerfile -t jbank-api:cleanup-check apps/jbank-api`, `docker compose -f infra/compose/docker-compose.yml --profile api config` | 구현 후 | progress.md 실행 기록 |
| OPS-02 | REQ-06·07 CI 워크플로 경로 변경 | 두 YAML이 파싱되고 실행 명령·paths가 새 경로. 실패 차단 로직(종료 코드) 불변. 원격 CI 실제 실행은 push 후 확인 대상으로 남김 | `python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/workflows/*.yml`, `python3 -m unittest discover -s .claude/tests -v` | 구현 후 | progress.md |
| OPS-03 | 설정·비밀 | 해당 없음 — 설정 값·비밀 주입 변경 없음 | - | - | - |
| OPS-04 | 기동·트래픽 | 해당 없음 — 프로브·기동 설정 변경 없음 | - | - | - |
| OPS-05 | 배포·복구 | 해당 없음 — EC2 배포는 GHCR 이미지 사용(compose.prod 불변). 빌드 입력 경로만 REQ-07로 확인 | - | - | - |
| OPS-06 | 리소스·확장 | 해당 없음 | - | - | - |
| OPS-07 | 관측·알림 | 해당 없음 | - | - | - |
| OPS-08 | 백업·복원 | 해당 없음 | - | - | - |

## 일반 테스트 방법

| 완료 기준 | 시나리오·예상 결과 | 테스트 명령·근거 위치 | 실행 시점 |
| --- | --- | --- | --- |
| REQ-01 | 삭제 대상이 존재하지 않음 | `ls -d contracts/bruno .local-logs/pr-infra-ec2-body.md` 실패, `find . -name .DS_Store -not -path '*/node_modules/*'` 빈 출력 | 구현 후 |
| REQ-02 | SVG 유지 + 프론트 필수 검사 통과 | `test -f apps/frontend/public/products/j-kids/icon-chevron-up.svg`, `workflow.py verify` | 구현 후 |
| REQ-03 | 기본 문구 제거 | `grep -c create-next-app apps/frontend/README.md` 가 0 | 구현 후 |
| REQ-04 | 주석만 변경, 형식·구성 유효 | `git diff` 확인, `terraform -chdir=infra/terraform/bootstrap fmt -check`, `terraform -chdir=infra/terraform/bootstrap validate` | 구현 후 |
| REQ-05 | 순수 이동 + 참조 갱신 | `git diff --cached -M --stat`에서 rename 100%, `ls todo` 실패 | 커밋 3 전 |
| REQ-06 | 하네스 테스트 통과(정상), 옛 경로 부재(실패 조건) | `python3 -m unittest discover -s .claude/tests -v`, `ls tests` 실패 | 커밋 6 전 |
| REQ-07 | 이미지 빌드·compose 해석 | OPS-01 명령 | 커밋 4 전 |
| REQ-08 | 인자 부족 경계 | `perf/run-transfer.sh; echo $?` → 사용법, 1. `bash -n perf/run-transfer.sh perf/run-10m.sh` | 커밋 5 전 |
| REQ-09 | 옛 경로 잔존 없음(양성 대조 후) + 수동 확인(README·docs/README infra 설명, docs/10 트리 해당 경로) | 아래 "REQ-09 검색 명령" — 이동 전 알려진 위치 출력, 이동 후 빈 출력 | 이동 전·구현 후 |
| REQ-10 | 필수 검사 전체 | `python3 .claude/hooks/workflow.py verify` | 구현 후 |

REQ-09 검색 명령(이스케이프 없이 그대로 실행):

```bash
git grep -n -E 'infra/docker|scripts/perf\.sh|(^|[^-a-z])todo/|-s tests|"-s", "tests"|"tests/\*\*"|bruno|수동 호출 컬렉션' -- ':!docs/devlog' ':!docs/roadmap' ':!todo' ':!.claude/tasks'
```

## 성능 테스트

- 필요 여부와 이유: 불필요. 부하 스크립트는 위치·이름만 바뀌고 측정 로직·조건이 같다. 앱 코드 변경 없음.

## 추가 맥락

- 사용자가 직접 실행할 terraform 잔여 폴더 삭제 명령(최종 보고에 안내):
  `rm -rf infra/terraform/modules/{compute,data,gitops,network,secrets,security}`
- 브랜치: `chore/repo-cleanup`.
