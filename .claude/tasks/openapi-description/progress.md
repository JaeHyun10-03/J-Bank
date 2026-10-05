# 진행 기록

작업 생성. 다음 행동: 요구사항과 완료 기준 작성.

## 2026-10-02 계획

- 끝낸 일: Q-01~05 사용자 결정, 계획 리뷰 3회(3회차 통과 권고), 커밋 계획 1~7 사용자 승인.
- 다음 행동: start → 커밋 1~4 구현(각 커밋마다 로컬 기동·스냅샷 diff) → 로컬 Swagger 확인 → verify → 결과 리뷰.
- 커밋 1~4 완료(8071a47, 894eeba, 406a4b9, 15900e7). logs/contract-check.txt·local-swagger.md 저장. 로컬 Swagger 403→201 확인. 다음: verify → 결과 리뷰 → 개발일지·작업 기록 → PR.
- 결과 리뷰 1/3: 배포 전 범위 통과 권고, PR CI·운영 증거 대기로 review fail 등록. R1-1 중간 커밋 테스트 통과 기록. 개발일지 커밋. 다음: 작업 기록 커밋 → PR → CI openapi-drift 확인 → 병합 → 운영 확인(사용자 실제 호출) → 리뷰 2회차.
- PR JaeHyun10-03/J-Bank#14 생성. 다음: CI openapi-drift 결과 확인 → 사용자 병합(#13 먼저) → 운영 확인.
- 2026-10-02 PR #13·#14 병합, CI openapi-drift pass, 배포 성공. 운영 api-docs·CORS·Swagger UI 확인(logs/prod-check.md). 사용자 실제 호출 확인 대기. 후속 PR 브랜치 chore/openapi-description-record.
- 2026-10-02 사용자 운영 Swagger 확인 완료(가입 201, 로그인 200, Authorize 뒤 logout 204). 프론트 프록시 Origin 전달로 운영 프론트 상태 변경 요청이 CORS 403인 기존 결함 발견, 다음 작업으로. 다음: verify → 결과 리뷰 2/3 → complete → 후속 PR.
- 결과 리뷰 2/3: REQ 모두 충족, 중요 C2-1(prod-check.md에 운영 테스트 계정 비밀번호 표기)로 fail 등록. 기록에서 값 제거. 다음: verify → 리뷰 3/3.
- 결과 리뷰 3/3 통과 권고, review pass·complete. 다음: 운영 테스트 고객 처리 사용자 결정 → 후속 PR(커밋 7) → 프록시 Origin 제거 새 작업.
- 2026-10-02 사용자 결정(삭제)으로 운영 테스트 고객 삭제. 참조 확인(위험도 이력 1, 계좌·계약·CTR·감사 로그 0, Redis 갱신 토큰 없음) 뒤 SSM 1972e43d로 행 수 검증 트랜잭션 실행: history 1·customers 1 삭제, 남은 고객 0. 남은 한계의 테스트 계정 위험 해소.
