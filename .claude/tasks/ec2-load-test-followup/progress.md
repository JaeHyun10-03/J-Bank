# 진행 기록

- 2026-09-28: 사용자 요청("후속으로 남긴거 진행시켜")으로 작업 생성. 계획 리뷰 1회차 수정 필요(드롭 수 성격, devlog 커밋) → 반영 → 2회차 통과 권고(지문 25b244de…). start.
- 커밋: 9f18e2c(후처리 순서: api 정지 → 정합성 → 실패 키 → 대사, 로그 단계 전체 줄 수, README 비교 조건 차이), 9f0518a(정합성 표 실패 키 행 이름·반영 하한), 299ecc5(요약 드롭 확정·추정, S2 r1·r3 문구, S5 메모리 열), 1704dc5(first-pass 결함 머리말).
- 확인: run-ec2.py 문법, cmd_run 호출 순서(stop 56 < integrity 57 < failed-keys 60 < recon 61 < logs 63), s2/r3 사본으로 새 행 이름 표 생성(실패 키 2 중 반영 1, 하한 0), 문서 드롭·메모리 12항목 원자료 대조 ALL_MATCH, first-pass diff 6줄 추가·0줄 삭제, verify 통과.

## 커밋 해시 대응표 (브랜치 이력 정리, 2026-09-28)

ec2-load-test 작업 기록·개발일지에 적힌 해시는 이력 정리 전 값이다. 정리는 두 커밋의 메시지만 바꿨고, 트리 비교 결과 내용은 같다
(`git diff 479eefc b4f097a`, `git diff a2f21f7 a870ee0` 모두 차이 없음).

| 정리 전 | 정리 후 | 커밋 |
| --- | --- | --- |
| 479eefc | b4f097a | perf(results): 1차 측정 결과를 first-pass로 이동(메시지 정정) |
| a2f21f7 | a870ee0 | fix(perf): 1분보다 짧은 측정에서 분당 재로그인 조회를 건너뛰도록 수정(메시지의 479eefc 언급 삭제) |
| b1b2c1c | fb3cf7c | fix(perf): 실패 키 자기 검증 판정 방향 … |
| 0759923 | 00f0087 | perf(results): 재측정 원자료 |
| bd5e671 | f6b0fdf | docs(perf): 재측정 결과로 문서 갱신 |
| 49c97bc | f1f544b | docs(devlog): EC2 부하 테스트 기준선 기록 |
| eff7ccf | 575c634 | chore(harness): ec2-load-test 작업 기록 |

원격 반영은 강제 push가 필요하며, 하네스가 막아 사용자가 직접 실행한다(`git push --force-with-lease origin perf/ec2-load-test`).

다음 행동: 결과 리뷰 → complete → 개발일지(docs(devlog)) → 작업 기록(chore(harness)).
