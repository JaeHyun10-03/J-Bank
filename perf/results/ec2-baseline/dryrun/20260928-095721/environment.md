# dryrun 측정 조건 (PERF-01)

- 시나리오: s1, 회차: 0, S5 요청률: 0
- 부하 발생기 인스턴스 유형: c7i.large
- 로컬 저장소 커밋: a2f21f753c242d860a70fe491d03d6d6c38019f1
- 시작(KST): 2026-09-28T09:57:21+09:00
- k6 구간(UTC epoch): 1790557292.0 ~ 1790557313.0
- 회차 시작 조건: PG 복사본 복원 → OS 페이지 캐시 비움 → 스택 재기동(redis 새 컨테이너) → readiness → 예열(초당 20건 2분, 별도 k6 실행) → 측정 경계 기록
- 대상·이미지 digest: ../../env/target/environment-target.md, 인프라: ../../env/infra.md
- CloudWatch 크레딧(5분 단위 min/max): {"CPUCreditBalance": [["2026-09-28T09:56:00+09:00", 0.0, 0.0]], "CPUSurplusCreditsCharged": [["2026-09-28T09:56:00+09:00", 0.0, 0.0]], "CPUSurplusCreditBalance": [["2026-09-28T09:56:00+09:00", 6.5606428333333335, 6.5606428333333335]]}
