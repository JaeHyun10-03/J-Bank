# s5-r1 정합성 (REQ-10)

| 항목 | 값 | 판정 |
| --- | --- | --- |
| k6 이체 성공(201) 수 vs 새 COMPLETED 이체 수 | 8434 vs 8434 | 같음 |
| 새 원장 행 수 vs 2 × 새 이체 수 | 16868 vs 16868 | 같음 |
| 새 원장 차변 합 = 대변 합 | 8434000.00 / 8434000.00 | 같음 |
| 핫 계좌 잔액 증가분 vs 핫 계좌 성공 이체 금액 합 | 0.00 vs 0 | 같음 |
| 기준 대사 대비 새 불일치 계좌 | 0 | 0 |
| 기준 불일치 계좌 중 증가분 불일치 | 0 |  |
| 전체 차변·대변 증가분 | 8911000.00 / 8911000.00 | 같음 |
| 경계 이후 멱등키 중복 | 0 |  |
| 경계 이후 COMPLETED 아닌 이체 | 0 |  |

대사 잡 실행 기록:

```
batch job=ctrDetectionJob perfRun=s5-r1-load-ctrDetectionJob exit=0 seconds=45.7196 start=1790533149.067312176 end=1790533194.786941513
batch job=fdsDetectionJob perfRun=s5-r1-load-fdsDetectionJob exit=0 seconds=70.192 start=1790533196.790460976 end=1790533266.982418335
batch job=ledgerReconciliationJob perfRun=s5-r1-load-ledgerReconciliationJob exit=0 seconds=49.9294 start=1790533271.245742424 end=1790533321.175141215
batch job=ledgerReconciliationJob perfRun=s5-r1-recon exit=0 seconds=39.1018 start=1790534109.764817497 end=1790534148.866603642
```
