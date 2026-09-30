# 진행 기록

- 2026-09-29: 원인 조사(CD 실패 = 2026-09-17 root 수동 중지) 후 사용자 요청으로 작업 생성. Q-01~05 결정(평일 09~18시, 꺼진 시간 CD 건너뛰고 부팅 때 반영, 프론트 운영 시간 안내, 배치 부팅 직후, CTR·FDS 빠진 날짜 모두).
- 계획 리뷰 1회차 수정 필요(동시 배포·설치 경로·배치 재실행·부팅 실패·프록시 시간 제한·검사 명령) → 반영. 2회차 수정 필요(잠금 범위·SEC-07 문구·교체 경로 실검증·실제 DB 검증) → 반영. 3회차 수정 필요(잠금 디렉터리 선생성 E) → 반영.
- 2026-09-29 사용자: 계획 리뷰 1회 추가 허락, 공휴일 괜찮음(Q-06). review-extend는 하네스가 '한도 남음'으로 거부해 연장 없이 4회차 진행.

다음 행동: verifier 4회차 → 통과면 start → 커밋 1 구현.
- 계획 리뷰 4회차 통과 권고. 구현 반영할 개선: 커밋 4 설명에 concurrency·잠금 디렉터리 선생성 포함, OPS-05 ② 전 GHCR fa97aef 태그 존재 확인(없으면 실제 이전 sha로 바꾸고 기록), BE-01 실제 DB 정리 SQL은 `systemctl is-active jbank-boot` inactive·실행 중 잡 없음 확인 후 실행.
- 커밋 1: terraform fmt·validate 통과, plan 4 add·1 change(배포 역할 정책에 ec2:DescribeInstances·ssm:DescribeInstanceInformation)·0 destroy, 스케줄러 정책 ec2:StartInstances·StopInstances(인스턴스 ARN).
- 커밋 3: batch_dates self-test ok, boot-test 27/27(amazonlinux:2023 컨테이너, 정상·두 번째 부팅·라벨 없음·같은 revision·pull 실패·git 실패·준비 초과·따라잡기 중간 실패·재시도·기록 없음·CD 경합 2방향), install-host 컨테이너 실행(설치·소유자·cron 제거·재실행). 출력: scratchpad boot-test.out 내용을 review에 옮김.
- 커밋 4: cd-skip-test 15/15(stopped·stopping 건너뜀, running+Online 배포, 잠금 디렉터리→flock→deploy→install 순서, SSM 미등록·조회 실패·배포 실패·폴링 초과는 실패), backend-cd yaml 파싱 ok.
- 커밋 5: jest 29/29(프록시 연결 오류 4종→503, 6초 걸린 POST 201 그대로, 400·500·502 그대로, 기타 예외 전파 / 상태 경로 200·502·503→online, 연결 실패·3초 초과→offline / 안내 alert 표시·미표시·상태 확인 실패 시 미표시 / API 실패 undefined·502·503·504만 상태 확인), lint·tsc 통과.
  - 로컬 확인(BACKEND_API_URL=http://10.255.255.1:8080, next dev :3100): /api/server-status 3.1초 {"online":false}, 프록시 POST 10.5초 503 SERVER_OFFLINE(undici 연결 시간 초과 10초 — Vercel 함수 제한이 더 짧으면 504가 나가며 화면은 504에서도 상태를 확인).
  - FE-04: 375px 모바일에서 /login 진입 시 안내 두 줄로 잘림 없음. 스크린샷 ~/.claude/projects/-Users-imjaehyeon-Documents--01----Project-j-bank/d5ed947b-b26d-4519-9013-a0057d622dd1/tool-results/mcp-Claude_Browser-blob-1790651325684-o2v9ue.jpg
  - E2E-04: 로그인 시도 → "로그인에 실패했습니다. 잠시 후 다시 시도하세요." 표시, 이동 없음(/login/id 유지), 안내 유지. 스크린샷 …/tool-results/mcp-Claude_Browser-blob-1790651349004-orud5e.jpg
  - FE-05: role=alert, 글자 rgb(255,255,255) / 배경 rgb(15,23,43) 대비 17.83:1(canvas로 계산).
- 커밋 6: ADR 0011, 인프라아키텍처 문서, README.
- verify 통과(12:11). terraform apply(12:12): 4 added·1 changed·0 destroyed. 스케줄: start `cron(0 9 ? * MON-FRI *)`, stop `cron(0 18 * * ? *)`, Asia/Seoul, ENABLED.
- 일회성 at() 확인: 역할 생성 직후 CreateSchedule이 "execution role must allow Scheduler to assume" 거부 → IAM 반영 뒤 재시도 성공(신뢰 정책 SourceAccount 조건 그대로). CloudTrail StopInstances 12:14:16·StartInstances 12:18:10, 세션 발급 역할 jbank-dev-scheduler. 일회성 스케줄 자동 삭제. 12:20 health 200.
- PR #8 생성(infra/ec2-weekday-schedule → main).
- 대기: PR #8 운영 시간 중 머지. 머지 후 할 일: 머지 CD 배포·호스트 설치 확인 → GHCR fa97aef 태그 존재 확인 → 수동 재부팅 2회(부팅 로그·이자 건너뜀·cron 없음) → BE-01 실제 DB(jbank-boot inactive 확인 후) → 18시 전 fa97aef로 되돌림 → 18시 이후 CD 수동 실행(건너뛰기) → 다음 날 09시 부팅 교체 확인 → 결과 리뷰·complete.
- 머지(16:25, 513796b)·CD 성공. 호스트: /usr/local/lib/jbank 3파일, /var/lib/jbank/deploy.lock(ec2-user), jbank-boot enabled·inactive, /etc/cron.d/jbank 없음(0hourly만), IMAGE_TAG=513796b…, GHCR fa97aef 태그 존재.
- 1차 재부팅(17:21): 부팅 작업 08:21:20Z 시작 → api 준비 36초 → 최신과 같음(513796b) 교체 안 함 → 중단 기록 정리 → interest(9/29) → CTR 마지막 완료 없음 → 9/28 → 대사 → FDS 9/28 → 끝(실패 0), 2분. 배치 기록은 이 부팅이 처음(9/17 생성 당일 저녁 수동 중지로 cron 미실행).
- 2차 재부팅(17:23): 교체 안 함, interestMaturityJob "오늘 이미 완료 — 건너뜀", CTR·FDS 마지막 완료 9/28이라 따라잡기 없음, 대사만(파라미터 없는 잡 재실행 성공), 실패 0, cron 없음(0hourly만).
- BE-01 실제 DB(jbank-boot inactive, 실행 중 잡 0 확인 후): fdsDetectionJob runDate=2026-01-01 실행 COMPLETED(exec 6) → STARTED·END_TIME NULL로 조작 → 재실행 JobExecutionAlreadyRunningException(문제 재현) → 정리 SQL → FAILED/abandoned at boot → 재실행 성공(exec 7 COMPLETED). 마지막 완료일 조회 = 2026-09-28(조회 동작 확인. FAILED 기록이 더 이른 날짜라 FAILED 제외 조건은 이 사례로 구별되지 않고 코드 검토로 확인).
- 17:3x 이미지 되돌림: flock 잠금 안에서 IMAGE_TAG=fa97aef…로 deploy.sh 성공(GHCR 태그 존재). 다음 09시 부팅에서 :latest(513796b) 교체 확인 예정. README 개편은 별도 worktree(main)에서 커밋·push(ae97e50, 10c2c54) — backend-cd 미발동 확인.
- 2026-09-29 18:00:22 StopInstances, 2026-09-30 09:00:43 StartInstances(스케줄러 역할 세션 16bf888e…, CloudTrail). 전날 걸어 둔 18시 이후 CD 수동 실행 백그라운드는 세션 종료로 실행되지 않음.
- 09:00 부팅(교체 경로 실검증): 00:00:59Z 시작 → 00:01:36Z 교체 fa97aef → 513796b → 준비 재대기 → 중단 기록 정리 → interest 9/30 → CTR 9/29 → 대사 → FDS 9/29 → 끝(실패 0). IMAGE_TAG=513796b, api healthy.
- CD 건너뛰기 실검증(12:20): 수동 중지 → `gh workflow run backend-cd --ref main`(run 36663923093, 5c21e20) success, 경고 "인스턴스가 stopped 상태라 배포를 건너뜀 — 다음 부팅 때 :latest(5c21e20…)로 반영된다".
- 재기동(12:22): 부팅 작업이 513796b → 5c21e20 교체, interest "오늘 이미 완료 — 건너뜀", CTR·FDS 마지막 완료 9/29라 따라잡기 없음, 대사, 실패 0. health 200.
- 남은 일: verify → 결과 리뷰 → complete → 개발일지·작업 기록 커밋.
- 결과 리뷰 1회차 수정 필요(SEC-05 기록 누락) → SEC-05 실행(적중 0), bash -n 7개·terraform fmt·validate·plan No changes 재기록. 후속: last_completed 조회 실패 시 날짜 누락 가능, 프론트 안내가 새로고침 전까지 남음, 주말 미기동 CloudTrail 확인(다음 토요일).
