# s5-r3 정합성 (REQ-10)

| 항목 | 값 | 판정 |
| --- | --- | --- |
| k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수 | 8333 vs 8333 | 같음 |
| 실패 이체 멱등키 기록 수 vs k6 이체 체크 실패 수(자기 검증) | 0 vs 0 | 같음 |
| 실패 응답인데 실제 반영된 이체(멱등키 대조) | 0 (실패 없음) |  |
| k6 중단 순간 처리 중이던 요청의 반영(잔차 = 새 완료 − 201 − 실패 중 반영) | 0 |  |
| 새 원장 행 수 vs 2 × 새 이체 수 | 16666 vs 16666 | 같음 |
| 새 원장 차변 합 = 대변 합 | 8333000.00 / 8333000.00 | 같음 |
| 핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합(DB) | 0.00 vs 0 | 같음 |
| 핫 계좌 잔액 증가분 vs k6 hot-transfer 201 × 1,000원 | 0.00 vs 0 | 같음 |
| 기준 대사 대비 새 불일치 계좌 | 0 | 0 |
| 기준 불일치 계좌 중 증가분 불일치 | 0 |  |
| 전체 차변·대변 증가분 | 8784000.00 / 8784000.00 | 같음 |
| 경계 이후 멱등키 중복 | 0 |  |
| 경계 이후 COMPLETED 아닌 이체 | 0 |  |

대사 잡 실행 기록:

```
batch job=ctrDetectionJob perfRun=s5-r3-load-ctrDetectionJob exit=0 seconds=45.6397 start=1790570251.173891174 end=1790570296.813556835
batch job=fdsDetectionJob perfRun=s5-r3-load-fdsDetectionJob exit=0 seconds=74.0019 start=1790570299.080002391 end=1790570373.081852453
batch job=ledgerReconciliationJob perfRun=s5-r3-load-ledgerReconciliationJob exit=0 seconds=54.0863 start=1790570374.258005324 end=1790570428.344331841
batch job=ledgerReconciliationJob perfRun=s5-r3-recon exit=0 seconds=38.3615 start=1790571224.503005526 end=1790571262.864460896
```
