# 기여 가이드

## 브랜치 전략

트렁크 기반이다. `main`은 항상 배포 가능한 상태를 유지한다. 작업은 `feat/`, `fix/` 등 커밋 type과 같은 접두어를 붙인 짧은 수명 브랜치에서 하고 Pull Request로 병합한다. git-flow의 릴리스 브랜치 관리는 도입하지 않는다.

브랜치명 예: `feat/api-account-open`, `fix/api-idempotency-key-race`

네이밍, 병합 방식(rebase + fast-forward, squash 안 씀), 태그 시점, main 보호 규칙은 `docs/브랜치전략.md` 참고.

## Atomic Commit

하나의 커밋은 하나의 논리적 변경만 담는다. 기능 구현, 리팩토링, 버그 수정, 테스트, 설정 변경을 같은 커밋에 섞지 않는다.

- 작업을 시작하기 전에 커밋 단위를 먼저 쪼개 계획한다.
- 각 커밋이 끝난 시점에 빌드 가능한 상태여야 한다. 중간 상태로 쪼개서 커밋하지 않는다.
- 다음 커밋으로 넘어가기 전에 현재 커밋이 완료됐는지(컴파일·테스트·포맷 검사 통과) 확인한다.
- 리뷰 중 지적된 수정은 새 커밋으로 쌓지 말고 대상 커밋에 합친다(rebase). 병합된 뒤의 커밋은 새로 쌓는다.

## 커밋 메시지

[Conventional Commits](https://www.conventionalcommits.org/) 형식을 따른다.

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### type

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 변경 |
| `chore` | 빌드 설정, 의존성, 잡무 |
| `ci` | CI 파이프라인 변경 |
| `perf` | 성능 개선(측정 수치를 커밋 본문에 남긴다) |
| `style` | 포맷팅 등 동작에 영향 없는 변경 |

### scope

저장소가 하나이므로 범위 표기로 변경 영역을 구분한다.

| scope | 영역 |
|---|---|
| `api` | `apps/jbank-api` |
| `frontend` | `apps/frontend` |
| `infra` | `infra/` |
| `contracts` | `contracts/` |
| `perf` | `perf/` |
| `docs` | 설계 문서, ADR |

예: `feat(api): add account opening endpoint`, `fix(frontend): preserve balance query on OTP success`

### description

- 명령형, 소문자 시작, 마침표 없음
- "무엇을"보다 "왜"가 본문에서 드러나야 한다. 설계 판단이 걸린 커밋은 본문에 근거를 남긴다.

근거: `docs/07_J-Bank_구현계획.md` 3.2절, `docs/10_J-Bank_폴더구조.md`

## Pull Request

PR 템플릿(`.github/pull_request_template.md`)을 채운다. `backend-ci` 또는 `frontend-ci`가(경로 필터에 따라 해당하는 쪽이) 통과해야 병합 가능하다.

## 아키텍처 결정

락 순서, 발신함 패턴, 패키지 의존 방향처럼 되돌리는 비용이 큰 판단이나, 이번 Tailwind v4 적용처럼 설계 문서와 실제 구현이 갈라지는 지점은 `docs/adr/`에 번호를 붙여 기록한다. 형식은 기존 ADR(`docs/adr/0001-spring-boot-version.md`)을 따른다.

## 코드 컨벤션

`docs/코드컨벤션.md` 참고.
