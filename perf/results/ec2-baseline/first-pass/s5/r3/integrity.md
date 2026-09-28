# s5-r3 정합성 (REQ-10)

| 항목 | 값 | 판정 |
| --- | --- | --- |
| k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수 | 8540 vs 8540 | 같음 |
| 새 원장 행 수 vs 2 × 새 이체 수 | 17080 vs 17080 | 같음 |
| 새 원장 차변 합 = 대변 합 | 8540000.00 / 8540000.00 | 같음 |
| 핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합 | 0.00 vs 0 | 같음 |
| 기준 대사 대비 새 불일치 계좌 | 0 | 0 |
| 기준 불일치 계좌 중 증가분 불일치 | 0 |  |
| 전체 차변·대변 증가분 | 8983000.00 / 8983000.00 | 같음 |
| 경계 이후 멱등키 중복 | 0 |  |
| 경계 이후 COMPLETED 아닌 이체 | 0 |  |

대사 잡 실행 기록:

```
batch job=ctrDetectionJob perfRun=s5-r3-load-ctrDetectionJob exit=0 seconds=42.435 start=1790537336.191348751 end=1790537378.626393542
batch job=fdsDetectionJob perfRun=s5-r3-load-fdsDetectionJob exit=0 seconds=67.1068 start=1790537380.510005538 end=1790537447.616780545
batch job=ledgerReconciliationJob perfRun=s5-r3-load-ledgerReconciliationJob exit=0 seconds=53.0079 start=1790537451.678051463 end=1790537504.685909703
batch job=ledgerReconciliationJob perfRun=s5-r3-recon exit=0 seconds=38.336 start=1790538295.352204860 end=1790538333.688197380
```
