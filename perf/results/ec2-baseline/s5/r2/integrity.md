# s5-r2 정합성 (REQ-10)

| 항목 | 값 | 판정 |
| --- | --- | --- |
| k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수 | 8333 vs 8333 | 같음 |
| 새 원장 행 수 vs 2 × 새 이체 수 | 16666 vs 16666 | 같음 |
| 새 원장 차변 합 = 대변 합 | 8333000.00 / 8333000.00 | 같음 |
| 핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합 | 0.00 vs 0 | 같음 |
| 기준 대사 대비 새 불일치 계좌 | 0 | 0 |
| 기준 불일치 계좌 중 증가분 불일치 | 0 |  |
| 전체 차변·대변 증가분 | 8817000.00 / 8817000.00 | 같음 |
| 경계 이후 멱등키 중복 | 0 |  |
| 경계 이후 COMPLETED 아닌 이체 | 0 |  |

대사 잡 실행 기록:

```
batch job=ctrDetectionJob perfRun=s5-r2-load-ctrDetectionJob exit=0 seconds=43.084 start=1790535248.110490258 end=1790535291.194531222
batch job=fdsDetectionJob perfRun=s5-r2-load-fdsDetectionJob exit=0 seconds=64.3025 start=1790535292.418569327 end=1790535356.721092847
batch job=ledgerReconciliationJob perfRun=s5-r2-load-ledgerReconciliationJob exit=0 seconds=57.3195 start=1790535360.356718470 end=1790535417.676236290
batch job=ledgerReconciliationJob perfRun=s5-r2-recon exit=0 seconds=36.592 start=1790536207.603739107 end=1790536244.195705270
```
