# s5-r2 정합성 (REQ-10)

| 항목 | 값 | 판정 |
| --- | --- | --- |
| k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수 | 8407 vs 8407 | 같음 |
| 실패 이체 멱등키 기록 수 vs k6 이체 체크 실패 수(자기 검증) | 0 vs 0 | 같음 |
| 실패 응답인데 실제 반영된 이체(멱등키 대조) | 0 (실패 없음) |  |
| k6 중단 순간 처리 중이던 요청의 반영(잔차 = 새 완료 − 201 − 실패 중 반영) | 0 |  |
| 새 원장 행 수 vs 2 × 새 이체 수 | 16814 vs 16814 | 같음 |
| 새 원장 차변 합 = 대변 합 | 8407000.00 / 8407000.00 | 같음 |
| 핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합(DB) | 0.00 vs 0 | 같음 |
| 핫 계좌 잔액 증가분 vs k6 hot-transfer 201 × 1,000원 | 0.00 vs 0 | 같음 |
| 기준 대사 대비 새 불일치 계좌 | 0 | 0 |
| 기준 불일치 계좌 중 증가분 불일치 | 0 |  |
| 전체 차변·대변 증가분 | 8855000.00 / 8855000.00 | 같음 |
| 경계 이후 멱등키 중복 | 0 |  |
| 경계 이후 COMPLETED 아닌 이체 | 0 |  |

대사 잡 실행 기록:

```
batch job=ctrDetectionJob perfRun=s5-r2-load-ctrDetectionJob exit=0 seconds=48.1326 start=1790568115.270737928 end=1790568163.403374282
batch job=fdsDetectionJob perfRun=s5-r2-load-fdsDetectionJob exit=0 seconds=77.5497 start=1790568166.632599010 end=1790568244.182313545
batch job=ledgerReconciliationJob perfRun=s5-r2-load-ledgerReconciliationJob exit=0 seconds=52.3909 start=1790568248.354673669 end=1790568300.745563664
batch job=ledgerReconciliationJob perfRun=s5-r2-recon exit=0 seconds=38.3429 start=1790569090.897869089 end=1790569129.240785511
```
