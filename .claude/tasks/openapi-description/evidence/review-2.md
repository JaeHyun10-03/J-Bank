# 검토 결과

## 검토 범위

결과 리뷰 2/3회차. 작업 시작 dc2179e → 9d0a26d(PR #14로 main 79d5ca1에 병합). 최신 검사 verify-002.
1회차(review fail, 배포 전 범위 통과 권고, 원문은 evidence/review-1.md)에서 남았던 PR CI·운영 확인을 logs/prod-check.md로 대조했다.

## 독립 검증 결과

- 호출: verifier, agentId acfed3ea99c0423c4, 2026-10-02(review-begin 2/3 직후).
- 대상 snapshot: 4bbbca1be6447e14414d236fd29b04f5639eddde9ea8a9b6d9bd354be5d06e5f
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로. 단, 원문이 인용한 운영 테스트 계정 비밀번호 값 한 곳은 `<비밀번호 가림>`으로 바꿨다):

> ## 결과 리뷰 2/3회차: openapi-description
>
> 결론: 코드 회귀는 없습니다. 요구사항도 모두 충족해 REQ-01~10을 "통과"로 등록할 수 있습니다. 다만 운영 기록 prod-check.md에 운영 계정의 비밀번호가 적혀 있습니다. 이 기록을 고치기 전에는 통과를 권고하지 않습니다(중요 1건).
>
> ### 검증 대상
> - snapshot `4bbbca1be6447e14414d236fd29b04f5639eddde9ea8a9b6d9bd354be5d06e5f`
>   - 출처: evidence/checks.json(verify-002). 이 값은 state.json의 `review_pending`과 같습니다.
> - git head 9d0a26d, dirty false, passed true, skipped_checks 없음
> - 1회차 이후 추가된 커밋: d9f81fe(devlog), 9d0a26d(harness 기록)
>
> ### 읽은 자료
> - 정책: docs/testing-policy.md
> - 작업 문서: task.md, review.md(1회차 원문과 지적별 처리), progress.md, state.json
> - 로그: logs/prod-check.md, logs/local-swagger.md
> - 증거: evidence/checks.json, verify-001/checks.json, verify-002/changes.txt, verify-002/check-5.log
> - 개발일지: docs/devlog/2026-10-02_Swagger설명과운영호출.md
> - 코드: SecurityConfig.java:77-88, apps/frontend/app/api/proxy/[...path]/route.ts(헤더 처리 부분), CustomerRegisterRequest.java
> - git reflog: .git/logs
>   - dc2179e는 ops-dashboard-panels 기록 커밋이라 PR #13은 하네스 기록 PR로 판단했습니다.
>   - fix/openapi-description의 마지막 push는 9d0a26d이고, origin/main은 79d5ca1입니다.
> - 적용 기준 ID: BE-02, SEC-04, OPS-02, E2E-02, E2E-06. 1회차에서 상세 기준 문서를 읽었고, 이번 회차에는 다시 읽지 않았습니다.
>
> ### 확인하지 못한 범위
> - 저는 명령을 하나도 실행하지 않았습니다(git, curl, gh, 브라우저 모두).
> - 아래 사실은 모두 prod-check.md에 메인이 적은 기록과 사용자가 전달한 내용으로만 확인했습니다.
>   - PR #14의 `openapi-drift` 성공(run 36974450740)
>   - backend-cd 배포 성공
>   - 운영 api-docs 값과 CORS curl 결과
>   - 운영 Swagger UI 화면
>   - 사용자의 실제 호출 결과
> - PR #13의 내용과 79d5ca1 병합 결과의 diff는 직접 보지 못했습니다. reflog로 간접 판단했습니다.
> - 운영 DB의 테스트 고객 존재 여부(SSM 조회)도 기록으로만 확인했습니다.
>
> ### 회귀 확인 (verify-002)
> - 6개 검사가 모두 exit 0입니다.
> - 백엔드 check-5는 `> Task :test UP-TO-DATE`입니다(5.1초). 테스트를 다시 실행한 것이 아니라 Gradle이 이전 통과 결과를 재사용했다는 뜻입니다.
>   - 15900e7 → 9d0a26d 사이에는 devlog와 하네스 기록만 바뀌었습니다. changes.txt의 코드 파일 목록도 1회차와 같습니다.
>   - 그래서 Gradle 입력이 같다는 판정 자체는 타당합니다.
>   - 실제 테스트 실행 근거는 verify-001(131.6초)과 PR CI `build` pass입니다.
> - 1회차 이후 회귀는 없습니다.
>
> ### REQ별 판정
>
> | REQ | 근거 | 판정 |
> | --- | --- | --- |
> | REQ-01 | 1회차 확인. 운영 `info.version` v1 | 통과 |
> | REQ-02 | 1회차 코드·테스트 대조. 운영 api-docs에 오래된 문구 없음, 필수 문구 일부 확인 | 통과 |
> | REQ-03 | 1회차 확인, local-swagger.md | 통과 |
> | REQ-04 | 1회차 확인. 운영 securitySchemes·전역 security 값, Authorize 버튼 | 통과 |
> | REQ-05 | 1회차 확인. 운영 `accessTokenExpiresAt`이 `Z`로 나와 "특정 오프셋을 단정하지 않는다"는 판단이 맞았음을 확인 | 통과 |
> | REQ-06 | 1회차 확인. 운영 servers `[{"url":"/"}]` | 통과 |
> | REQ-07 | contract-check.txt(로컬 diff 없음, api.ts 변화 없음), PR #14 `openapi-drift` pass | 통과 |
> | REQ-08 | local-swagger.md(403 COMMON_007 → Authorize 뒤 201) | 통과 |
> | REQ-09 | 아래 "REQ-09 세부" 참고 | 통과 |
> | REQ-10 | 1회차 AuthControllerTest. 운영에서도 api.j-bank.site는 400, evil.example은 403 | 통과 |
>
> REQ-09 세부:
> - api-docs가 REQ-01·02·04·06을 만족합니다. REQ-02의 필수 문구는 운영에서 4개만 확인했습니다. 하지만 같은 커밋으로 `openapi-drift`가 통과했고 version v1로 새 빌드가 배포된 것이 확인되므로 충분합니다.
> - Swagger UI: Authorize 버튼이 보이고, 요청 URL이 https 같은 출처입니다.
> - Origin을 붙인 빈 본문 로그인: 400 `COMMON_001`이 나왔습니다. 변경 전은 403이었습니다.
> - 사용자 호출: 로그인 200 → Authorize → logout 204.
>   - logout은 CSRF 예외 경로가 아닙니다(예외는 로그인·고객 등록 2개뿐). 그래서 204는 헤더 값이 쿠키와 맞아 CSRF 검사를 통과했다는 뜻입니다.
>   - 응답의 `access-control-allow-origin: https://api.j-bank.site`로 운영 CORS 경로도 확인됩니다.
>
> ### 기준 ID
> - BE-02, E2E-06: 1회차에서 충족했고, 이후 코드 변경이 없습니다.
> - OPS-02: 충족(로컬 diff와 PR `openapi-drift` pass).
> - SEC-04: 충족(테스트, 운영 허용·거절 curl, 운영 응답 헤더).
> - E2E-02: 충족. 로컬 전체 절차와 운영 사용자 호출이 있습니다.
> - 적용 제외 항목: 1회차 판단을 유지합니다. 새 코드 변경이 없어 다시 볼 사유가 없습니다.
>
> ### 사용자 전달 증거의 한계가 판정에 주는 영향
> - 운영 403 비교 단계를 전달받지 못한 점: 영향 없습니다.
>   - REQ-09에는 운영 403 단계가 요구사항에 없고 "로그인→Authorize→상태 변경 요청"만 있습니다.
>   - Authorize 없이 403이 나는 동작은 서버 코드가 바뀌지 않았고, 로컬 3단계와 CsrfDoubleSubmitFilterTest로 확인됩니다.
> - 예시 본문으로 가입한 점: 기능 판정에는 영향이 없습니다. 하지만 운영에 알려진 비밀번호를 가진 계정이 생겼고, 그 값이 기록에 남았습니다. 아래 지적 C2-1과 R2-1을 보세요.
>
> ### 지적 사항
>
> **[중요] C2-1. prod-check.md에 운영 계정 비밀번호가 적혀 있음**
> - 위치: `.claude/tasks/openapi-description/logs/prod-check.md` 46행 `<비밀번호 가림>`. 44행에는 loginId `string`이 적혀 있습니다.
> - 근거
>   - task.md 37행과 AC-04는 비밀번호 값을 기록하지 않기로 정했습니다.
>   - 같은 파일 50행도 "토큰·비밀번호 값은 기록하지 않음"이라고 적고 있어 46행과 서로 어긋납니다.
>   - CustomerRegisterRequest의 password는 `@NotBlank`만 검사합니다. 그래서 이 계정은 지금도 운영에서 그 비밀번호로 로그인되는 실제 자격 증명입니다(로그인 200 기록).
> - 예상 영향
>   - 이 파일은 아직 untracked라 지금은 유출되지 않았습니다.
>   - 그대로 후속 PR(커밋 계획 7)에 커밋되면 운영 계정 자격 증명이 저장소에 영구히 남습니다.
> - 필요한 수정
>   - 46행의 비밀번호를 지웁니다. 예: "Swagger 예시 본문 값으로 로그인".
>   - 커밋 전에 progress.md, review.md 등 다른 기록에도 같은 값이 없는지 확인합니다.
>   - 기록만 고치는 것이므로 코드 재테스트는 필요 없습니다. 3회차에서는 이 삭제와 코드 무변경만 확인하면 됩니다.
>
> **[권고] R2-1. 운영에 남은 테스트 고객 처리 결정**
> - 근거: 운영 DB에 loginId `string`(customerId 1, ACTIVE)이 남아 있습니다. 비밀번호가 Swagger 기본 예시값이라 누구나 추측할 수 있습니다.
> - Q-04에서 정한 것은 "본인 계정으로 확인"이었고, 이 계정은 그 결정에서 벗어나 생긴 부산물입니다.
> - 새로 생긴 노출은 아닙니다. 같은 가입은 curl로도 원래 가능했습니다.
> - 사용자 선택지
>   - 비활성화 또는 삭제
>   - 비밀번호 변경
>   - 이유를 적고 유지
> - 남은 한계나 후속 작업에 남기면 충분하고, 이번 작업의 완료를 막지는 않습니다.
> - 참고: 46행의 `01011111111`이 사용자 실제 번호라면 가리는 것이 좋습니다. 형식상 더미로 보입니다.
>
> **[권고] R2-2. 이번에 사실이 아닌 것으로 드러난 조사 내용 기록**
> - 근거: 아래 두 곳은 모두 "운영 프론트는 서버 프록시라 브라우저 Origin 문제가 없다"고 적고 있습니다. 하지만 prod-check.md 54행의 재현 결과로 사실이 아님이 확인됐습니다.
>   - task.md 17행 조사 사실
>   - SecurityConfig.java:77 주석 "배포 프론트는 서버 쪽 프록시라 CORS가 생기지 않는다"
> - 필요한 조치: review.md의 남은 한계에 기록합니다. 주석은 후속 작업(프록시에서 Origin 제거)을 마치면 다시 사실이 되므로, 그 작업에서 함께 확인하면 됩니다. 완료를 막지 않습니다.
>
> ### 기존 결함이 이번 완료를 막는지
>
> **1. 운영 프론트의 POST가 CORS 403 나는 문제: 막지 않습니다. 회귀가 아닙니다.**
> - 변경 전 허용 출처는 `http://localhost:3000` 하나였습니다. 그래서 `https://www.j-bank.site`는 변경 전에도 거절됐습니다.
> - 이번 변경은 허용 출처 하나를 추가했을 뿐입니다(SecurityConfig.java:82). 거절 범위는 넓어지지 않았습니다.
> - 프록시 route.ts는 변경 파일 목록에 없습니다. 이 파일은 `host`와 `content-length`만 지우고 Origin은 그대로 넘깁니다.
> - 이번 작업의 REQ에도 운영 프론트 동작은 없습니다. 후속 작업으로 분리한 것이 타당합니다.
>
> **2. 잘못된 입력에 500이 나는 문제(잘못된 JSON, `sort=["string"]`): 막지 않습니다. 회귀가 아닙니다.**
> - GlobalExceptionHandler와 정렬 처리는 이번 변경 범위 밖이고 수정되지 않았습니다.
> - 운영 Swagger가 열리면서 재현이 쉬워졌을 뿐, curl로도 같은 결과가 납니다. 후속 작업으로 분리한 것이 타당합니다.
>
> ### 이전 지적 처리
> | 지적 | 결과 | 근거 |
> | --- | --- | --- |
> | R1-1 | 해결 | review.md 181-185행: 중간 커밋 3개의 테스트 실행 기록(exit 0, 건수 증가가 커밋 내용과 맞음). 메인 기록 기준 |
> | R1-2 | 수용(남은 한계) | review.md 186행, devlog 32행에 기록. 선택 사항이라 타당 |
> | 1회차 대기(REQ-07 PR CI, REQ-09·AC-05) | 해결 | prod-check.md |
>
> 최종 판정: 수정 필요

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | OpenApiConfig 버전 | OpenApiConfigTest, 운영 api-docs | 통과 |
| REQ-02 | OpenApiConfig 설명 2·3번 | OpenApiConfigTest, 운영 api-docs | 통과 |
| REQ-03 | OpenApiConfig 설명 8번 | OpenApiConfigTest, logs/local-swagger.md | 통과 |
| REQ-04 | OpenApiConfig 보안 스킴 | OpenApiConfigTest, 운영 api-docs·Authorize | 통과 |
| REQ-05 | OpenApiConfig 설명 1·4~7번 | OpenApiConfigTest, 운영 응답 시각 `Z` | 통과 |
| REQ-06 | OpenApiConfig servers | OpenApiConfigTest, 운영 servers `/` | 통과 |
| REQ-07 | contracts/openapi/openapi.yaml | logs/contract-check.txt, PR #14 openapi-drift pass | 통과 |
| REQ-08 | 설정 결과 | logs/local-swagger.md | 통과 |
| REQ-09 | 운영 배포 | logs/prod-check.md | 기록 수정 필요(C2-1) |
| REQ-10 | SecurityConfig CORS | AuthControllerTest, 운영 Origin별 curl | 통과 |

## 지적별 처리

- C2-1(중요): 이 회차를 fail로 등록하고 구현 단계에서 logs/prod-check.md의 비밀번호 표기를 지운다(리뷰 단계에서는 hook이 기록 수정을 막는다). 다른 기록에 같은 값이 없음을 grep으로 확인했다(prod-check.md 46행 한 곳).
- R2-1(권고): 운영 테스트 고객 처리(비활성화·삭제·유지)를 사용자에게 묻는다.
- R2-2(권고): 남은 한계에 기록한다. 주석·task.md의 "운영 프론트는 Origin 문제 없음"은 사실이 아니었고, 다음 작업(프록시 Origin 제거)에서 바로잡는다.

## 판정과 이유

review fail(2/3). 요구사항은 모두 충족했지만 운영 기록에 운영 계정 비밀번호가 남은 중요 지적이 있어 통과로 등록하지 않는다. 기록 수정 뒤 3회차에서 통과를 등록한다.

## 확인하지 못한 부분

- verifier는 명령을 실행하지 않았고 기록을 읽어 판정했다.
