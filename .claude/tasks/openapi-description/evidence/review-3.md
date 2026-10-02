# 검토 결과

## 검토 범위

결과 리뷰 3/3회차. 작업 시작 dc2179e → 9d0a26d(PR #14로 main 79d5ca1에 병합). 최신 검사 verify-003.
1회차(배포 전 범위 통과 권고, evidence/review-1.md)·2회차(REQ 충족, 중요 C2-1로 fail, evidence/review-2.md) 이후 코드 변경 없이 운영 기록의 비밀번호 표기만 지웠다.

## 독립 검증 결과

- 호출: verifier, agentId a962a9386ae8013d0, 2026-10-02(review-begin 3/3 직후).
- 대상 snapshot: 4bbbca1be6447e14414d236fd29b04f5639eddde9ea8a9b6d9bd354be5d06e5f
- 응답 원문(인용 표시 `> `만 붙이고 내용 그대로):

> ## 결과 리뷰 3/3회차(마지막): openapi-description
>
> 결론부터 말하면 C2-1은 해결됐습니다. 코드 변경과 회귀도 없고, R2-1·R2-2는 완료를 막지 않습니다. REQ-01~10은 모두 "통과"로 등록할 수 있습니다.
>
> ### 검증 대상
> - snapshot `4bbbca1be6447e14414d236fd29b04f5639eddde9ea8a9b6d9bd354be5d06e5f`
>   - 출처는 evidence/checks.json(archive verify-003/checks.json)입니다.
>   - 이 값은 verify-002 snapshot, state.json의 `review_pending`, verifier_calls attempt 3의 snapshot과 모두 같습니다.
> - git head 9d0a26d, dirty false, passed true, unchanged_during_checks true, skipped_checks 없음
> - 검사 6개 모두 exit 0
>
> ### 읽은 자료
> - 작업 기록: logs/prod-check.md, review.md, progress.md, task.md 10~38행, state.json
> - 증거: evidence/checks.json, verify-002/changes.txt, verify-003/changes.txt, verify-003/check-5.log
> - 코드: CustomerRegisterRequest.java
> - grep: 작업 폴더 전체에서 `password|비밀번호`, `string|01011111111|Origin|남은 한계`를 찾았습니다.
> - 적용 기준 ID: BE-02, SEC-04, OPS-02, E2E-02, E2E-06. 코드 변경이 없어 상세 기준 문서는 다시 읽지 않았습니다.
>
> ### 확인하지 못한 범위
> - 저는 명령을 하나도 실행하지 않았습니다(git, gradle, curl 모두).
> - 2회차 verifier의 응답 원문은 볼 수 없었습니다. 그래서 "비밀번호 한 곳만 가렸다"는 말은 원문과 대조하지 못했습니다. 확인한 것은 다음 두 가지뿐입니다.
>   - review.md와 evidence/review-2.md의 인용 행이 grep 결과에서 서로 같습니다.
>   - 가린 곳은 94행 `<비밀번호 가림>` 한 곳입니다.
> - 지운 비밀번호의 실제 값을 모릅니다. 그래서 같은 문자열이 다른 의미로(예: 아이디) 남아 있는지는 판단할 수 없었습니다.
> - 운영 DB에 테스트 고객이 있는지는 기록으로만 확인했습니다.
>
> ### 확인 결과
>
> 1. **C2-1 해결**
>    - prod-check.md 46행은 이제 "Swagger 예시 본문의 아이디·비밀번호(값은 기록하지 않음)"입니다. 44행에서도 아이디 값이 빠지고 "예시값"으로만 적혀 있습니다. 2회차에 권고한 문구 방향과 같습니다.
>    - 46행에 있던 전화번호 `01011111111`도 prod-check.md에서 빠졌습니다.
>    - 작업 폴더 전체 grep에서 비밀번호라는 단어가 나오는 곳은 모두 다음 셋 중 하나입니다. 비밀번호 값을 적은 행은 없습니다.
>      - 원칙: task.md 37·72행, local-swagger.md 6행, prod-check.md 50행
>      - 2회차 인용: review.md·review-2.md
>      - 진행 기록: progress.md 14행
>    - prod-check.md 50행("기록하지 않음")과 46행이 이제 서로 맞습니다.
> 2. **코드 무변경, 회귀 없음**
>    - verify-003/changes.txt와 verify-002/changes.txt가 완전히 같습니다(7개 파일, 223+/23-).
>    - snapshot도 같습니다.
>    - check-5의 `:test UP-TO-DATE`는 Gradle 입력이 같아 이전 결과를 재사용했다는 뜻이며, snapshot이 같은 것과 맞습니다. 실제 테스트 실행 근거는 이전 회차와 같이 verify-001과 PR CI `build` pass입니다.
> 3. **R2-1·R2-2는 완료를 막지 않습니다.** 둘 다 이번 REQ 밖의 운영 부산물과 조사 사실 정정입니다. 다만 아래 권고 R3-1의 기록 조건이 있습니다.
> 4. **REQ-01~10 모두 통과로 등록할 수 있습니다.**
>    - 2회차 판정의 근거가 바뀌지 않았습니다.
>    - REQ-09의 "기록 수정 필요(C2-1)"는 위 1번으로 해소됐습니다.
>
> ### 지적 사항
> - 차단: 없음
> - 중요: 없음
> - **[권고] R3-1. R2-1·R2-2를 최종 review.md의 "남은 한계"에 명시**
>   - 근거
>     - R2-1(운영 테스트 고객 처리)은 지금 prod-check.md 51행에만 "사용자 결정 사항으로 남김"으로 적혀 있습니다.
>     - R2-2는 review.md 164행에 처리 계획만 있습니다.
>     - task.md 17행의 "운영 프론트는 Vercel 서버에서 프록시하므로 브라우저 Origin 문제가 없다"는 사실이 아닌데 그대로 남아 있습니다.
>   - 필요한 조치: 3회차 review.md "남은 한계"에 아래를 적습니다.
>     - 테스트 고객이 운영에 ACTIVE로 남아 있고, 예시값이라 추측할 수 있다는 점
>     - 처리는 사용자가 결정한다는 점
>     - task.md 17행과 SecurityConfig 주석의 사실 오류는 다음 작업(프록시 Origin 제거)에서 바로잡는다는 점
>   - 완료를 막지 않습니다.
> - **[권고] R3-2. 기록에서 지운 것은 값뿐이고, 계정 위험은 남아 있음**
>   - review.md 94·108행의 2회차 인용에는 loginId `string`이 그대로 있습니다. 또 기록은 "비밀번호가 Swagger 기본 예시값"이라고 적고 있습니다.
>   - 그래서 비밀번호 값을 지운 뒤에도 아이디와 비밀번호를 추측할 수 있습니다. 이 위험은 기록 때문이 아니라 계정이 존재하기 때문에 생깁니다(Swagger 기본값은 누구나 압니다). 그래서 실제 대책은 R2-1의 계정 처리입니다.
>   - 인용 원문은 보존해야 하므로 고칠 필요는 없습니다.
>   - 다만 후속 PR로 이 기록을 커밋하기 전에, 사용자가 R2-1(비활성화·삭제·비밀번호 변경·유지)을 정하도록 먼저 알리는 것이 좋습니다. 완료를 막지 않습니다.
>
> ### 적용 제외 항목
> 1·2회차 판단을 유지합니다. 코드 변경이 없어 다시 볼 사유가 없습니다.
>
> ### 이전 지적 처리
> | 지적 | 결과 | 근거 |
> | --- | --- | --- |
> | C2-1(중요) | 해결 | prod-check.md 44·46·50행. 작업 폴더 grep에서 비밀번호 값 없음 |
> | R2-1(권고) | 수용(사용자 결정으로 남김) | prod-check.md 51행. 최종 남은 한계 기록 필요(R3-1) |
> | R2-2(권고) | 처리 예정 | review.md 164행에 계획이 있음. 최종 남은 한계 기록 필요(R3-1) |
> | R1-1, R1-2, 1회차 대기 | 해결(2회차에 확인) | 변경 없음 |
>
> 최종 판정: 통과 권고

## 요구사항별 검증

| 요구사항 ID | 구현 위치 | 테스트·실행 증거 | 판정 |
| --- | --- | --- | --- |
| REQ-01 | OpenApiConfig 버전 | OpenApiConfigTest, 운영 api-docs(logs/prod-check.md) | 통과 |
| REQ-02 | OpenApiConfig 설명 2·3번 | OpenApiConfigTest, 운영 api-docs | 통과 |
| REQ-03 | OpenApiConfig 설명 8번 | OpenApiConfigTest, logs/local-swagger.md | 통과 |
| REQ-04 | OpenApiConfig 보안 스킴·전역 security | OpenApiConfigTest, 운영 api-docs·Authorize, 운영 사용자 호출(헤더 자동 첨부) | 통과 |
| REQ-05 | OpenApiConfig 설명 1·4~7번 | OpenApiConfigTest, 운영 응답 시각 `Z` | 통과 |
| REQ-06 | OpenApiConfig servers | OpenApiConfigTest, 운영 servers `/`, 운영 요청 URL https | 통과 |
| REQ-07 | contracts/openapi/openapi.yaml | logs/contract-check.txt, PR #14 openapi-drift pass | 통과 |
| REQ-08 | 설정 결과 | logs/local-swagger.md(403 COMMON_007 → Authorize 뒤 201) | 통과 |
| REQ-09 | 운영 배포(79d5ca1) | logs/prod-check.md(api-docs, Origin별 curl, Swagger UI, 사용자 로그인 200·Authorize 뒤 logout 204) | 통과 |
| REQ-10 | SecurityConfig CORS 허용 출처 | AuthControllerTest CORS 3사례, 운영 Origin별 curl(api 400·evil 403) | 통과 |

## 지적별 처리

- 1회차 R1-1: 중간 커밋 3개 테스트 통과 기록(evidence/review-1.md 이후 2회차 review.md에 기록, review-2.md 보존).
- 1회차 R1-2: 남은 한계로 기록(아래).
- 2회차 C2-1(중요): logs/prod-check.md에서 비밀번호·아이디 값 표기를 지움. 3회차에서 해결 확인.
- 2회차 R2-1·R2-2, 3회차 R3-1·R3-2: 아래 남은 한계에 기록하고, 운영 테스트 고객 처리는 후속 PR 커밋 전에 사용자에게 묻는다.

## 완료 기준별 근거

- AC-01: OpenApiConfigTest(verify-001 실행, verify-002·003 동일 입력)
- AC-02: AuthControllerTest CORS·기존 테스트, FullFlowIntegrationTest, SecurityConfig 변경은 허용 출처 값과 관련 주석뿐
- AC-03: logs/contract-check.txt, PR #14 openapi-drift pass
- AC-04: logs/local-swagger.md
- AC-05: logs/prod-check.md
- 하네스 verify: verify-003 통과

## 성능 테스트 확인

불필요(task.md 성능 테스트 절). 요청 처리 경로 변경 없음.

## 발견한 문제

- 운영 프론트 프록시가 `Origin`을 백엔드로 넘겨 운영 프론트의 모든 상태 변경 요청이 CORS 403(기존 결함, 다음 작업).
- 잘못된 JSON 본문·없는 정렬 필드가 500 `COMMON_006`(기존 결함, 후속 작업 칩).

## 판정과 이유

review pass(3/3). REQ-01~10이 코드·테스트·로컬·운영 증거로 확인됐고 차단·중요 지적이 없다.

## 확인하지 못한 부분 / 남은 한계

- 운영에 Swagger 예시값으로 만든 테스트 고객 1명이 ACTIVE로 남아 있어 아이디·비밀번호를 추측할 수 있다. 처리(비활성화·삭제·비밀번호 변경·유지)는 사용자 결정 사항이다.
- task.md 조사한 사실의 "운영 프론트는 Vercel 서버에서 프록시하므로 브라우저 Origin 문제가 없다"와 `SecurityConfig` CORS 주석의 "배포 프론트는 서버 쪽 프록시라 CORS가 생기지 않는다"는 사실이 아니었다(프록시가 Origin을 넘김). 다음 작업(프록시 Origin 제거)에서 주석이 다시 사실이 되도록 바로잡는다. task.md는 계획 지문 유지를 위해 고치지 않는다.
- 쿠키가 하나도 없는 상태 변경 요청은 401이 아니라 CSRF 403이 먼저 난다. 설명의 401 문구는 GET 기준이다(1회차 R1-2).
- 운영 Swagger에서 Authorize 전 403 비교 단계는 사용자 결과를 받지 못했고 로컬 확인으로 대신했다.
- verifier는 명령을 실행하지 않고 기록을 읽어 판정했다.
