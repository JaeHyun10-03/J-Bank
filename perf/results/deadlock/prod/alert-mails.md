# 운영 알림 메일 시각 (REQ-07)

2026-11-25 사용자 Gmail을 사용자 허락을 받아 에이전트가 검색했다(제목으로 검색, 날짜는 그날). 메일 주소는 저장하지 않았다. 본문은 교착 메일 1건(15:37:52)만 열었고, 그 안의 한 구절(`최근 5분 교착 증가량 9.5`) 외에는 저장하지 않았다.

- 시각은 KST다. "확인" 표시는 메일 헤더 시각이고, 나머지는 메일 ID에서 계산한 시각이다. 계산값은 헤더 시각을 확인한 5건과 1초 이내로 맞았다(계산값은 초 단위로 0~1초 늦거나 빠르다).
- 1차 실행(14:04~14:38)은 계좌 잔액이 0원이라 완료 이체가 없다. 보고서 비교에는 쓰지 않고 메일 시각만 남긴다.

## 2차 실행 (15:22~15:55)

| 알림 | 상태 | 시각 | 관련 회차 |
| --- | --- | --- | --- |
| DeadlockDetected | 발생 | 15:37:52 (확인) | ① 동시 50 |
| DeadlockDetected | 해제 | 15:47:52 (확인) | ① 동시 50 |
| DeadlockDetected | 발생 | 15:52:52 (확인) | ① 동시 100 |
| DeadlockDetected | 해제 | 16:02:52 (확인) | ① 동시 100 |
| AccountLockWaitHigh | 발생 | 15:37:52 | ① 동시 50 |
| AccountLockWaitHigh | 해제 | 15:47:52 | ① 동시 50 |
| AccountLockWaitHigh | 발생 | 15:52:52 | ① 동시 100 |
| AccountLockWaitHigh | 해제 | 15:57:52 | ① 동시 100 |
| TransferServerErrorRate | 발생 | 15:40:52 | ① 동시 50 |
| TransferServerErrorRate | 해제 | 15:45:52 | ① 동시 50 |
| TransferServerErrorRate | 발생 | 15:52:52 | ① 동시 100 |
| TransferServerErrorRate | 해제 | 16:02:52 | ① 동시 100 |

## 1차 실행 (14:04~14:38)

| 알림 | 상태 | 시각 |
| --- | --- | --- |
| DeadlockDetected | 발생 | 14:20:52 (확인) |
| DeadlockDetected | 해제 | 14:30:52 |
| DeadlockDetected | 발생 | 14:35:53 |
| DeadlockDetected | 해제 | 14:45:51 |
| AccountLockWaitHigh | 발생·해제 | 14:20:52 · 14:25:52, 14:35:52 · 14:40:52 |
| TransferServerErrorRate | 발생·해제 | 14:22:53 · 14:27:52, 14:35:53 · 14:45:52 |

- `TargetDown`은 검색했지만 온 메일이 없었다.
- 5xx 비율 알림은 이체 5xx 비율이다. 교착 500은 원인 코드로 구분되지 않는다.
