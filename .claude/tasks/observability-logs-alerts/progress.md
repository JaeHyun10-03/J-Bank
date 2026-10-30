# 진행 기록

- 작업 생성. 운영·로컬 관측 구성 조사(로그 수집기·postgres-exporter·알림 없음, 운영 메모리 여유 부족 기록 확인).
- 사용자 결정 Q-01~Q-08 반영, task.md 명세 확정·커밋 계획 작성.
- 다음 행동: verifier 계획 리뷰 → 통과 시 사용자 승인 후 start.
- 계획 리뷰 1회차: 수정 필요(B-1, I-1~I-5, 권고 10). plan-review.md에 원문 보존.
- 사용자 결정: Q-09 소켓 프록시 사용.
- 운영 유휴 메모리 측정(2026-10-29 07:39Z, SSM 읽기 전용): available 944MB, api 502MiB·grafana 101MiB·postgres 63MiB·prometheus 44MiB·caddy 27MiB·redis 10MiB, swap 없음.
- 사용자가 인스턴스 사양 상향(t3.small → t3.medium)을 검토 중. 결정되면 Q-10으로 기록하고 범위(Terraform 변경 여부) 갱신 후 task.md 수정 → 계획 재리뷰.
- 사용자 결정: Q-10 t3.small 유지, 600MB 미만이면 운영 로그 수집 3종 내림.
- 운영 부팅 api 수집 실패 구간 실측 15~30초(운영 Prometheus 조회).
- 1회차 지적 전부 task.md 반영. 다음 행동: 계획 리뷰 2회차.
- 계획 리뷰 2회차: 수정 필요(N-1~N-3, R-1~R-6). plan-review.md에 원문 보존.
- 사용자 결정: Q-11 18시 이후 병합, Q-12 배치 중 최저 가용 150MB 기준, Q-13 다운 알림을 api·exporter 수집 실패로 넓힘, Q-14 수집기 inspect 남는 위험 수용.
- logs/는 계획 단계에서 쓸 수 없어 운영 측정 원본을 아래 부록에 둔다. start 뒤 `logs/prod-measure.md`로 옮긴다.

## 부록: 운영 사전 측정 원본 (SSM AWS-RunShellScript, 읽기 전용, i-0b71df18394cf8010)

SSM 명령 ID는 실행 당시 출력하지 않아 남기지 못함.

### 1. 유휴 메모리 (2026-10-29T07:39:35Z, 부팅 배치 종료 후)

명령: `date -u; uptime; free -m; grep -E "MemAvailable|SwapTotal" /proc/meminfo; docker stats --no-stream --format "{{.Name}} {{.MemUsage}} {{.CPUPerc}}"; df -h /`

```text
Thu Oct 29 07:39:35 UTC 2026
 07:39:35 up  7:38,  0 users,  load average: 0.08, 0.09, 0.02
               total        used        free      shared  buff/cache   available
Mem:            1913         803         369          16         741         944
Swap:              0           0           0
MemAvailable:     966908 kB
SwapTotal:             0 kB
compose-api-1 502MiB / 1.869GiB 0.36%
compose-caddy-1 27.1MiB / 1.869GiB 0.00%
compose-redis-1 9.688MiB / 1.869GiB 0.29%
compose-postgres-1 63.41MiB / 1.869GiB 0.12%
compose-grafana-1 101.3MiB / 1.869GiB 0.06%
compose-prometheus-1 44.3MiB / 1.869GiB 0.00%
Filesystem      Size  Used Avail Use% Mounted on
/dev/nvme0n1p1   20G  4.8G   16G  25% /
```

### 2. compose 버전

명령: `docker compose version; docker version --format {{.Server.Version}}`

```text
Docker Compose version v2.29.7
25.0.16
```

### 3. 부팅 직후 api 수집 실패 구간

명령(날짜별): `docker exec compose-prometheus-1 wget -qO- "http://localhost:9090/api/v1/query_range?query=up&start=<날짜>T00:00:00Z&end=<날짜>T00:10:00Z&step=15s"`
원본 JSON을 요약(첫 표본 시각·값, 처음 up=1 시각, 0 표본 수):

```text
2026-10-26 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
2026-10-27 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
2026-10-28 first sample 00:01:30 0 first up=1 00:02:00 zero samples 2
2026-10-29 first sample 00:01:30 0 first up=1 00:01:45 zero samples 1
```

Prometheus도 부팅 때 함께 시작하므로 첫 표본 이전은 관측되지 않는다. `journalctl -u jbank-boot`: 부팅 작업은 00:00:57~00:01:04에 시작해 2~3.5분 뒤 종료.

## 계획 리뷰 3회차 이후

- 3회차: 통과 권고(차단·중요 없음, 권고 9). 한도 도달로 task.md는 3회차 지문 그대로 유지, 권고는 구현 산출물에 반영(plan-review.md 지적 처리).
- 남은 사용자 선택: 권고 6(`pg_up==0` 다운 규칙 추가 여부. 추가 시 명세 변경 → review-extend 필요).
- 다음 행동: 사용자에게 커밋 계획 승인 요청 → 승인 시 start.

## 구현 (2026-10-29)

- 브랜치 feat/observability-logs-alerts, start 완료. 커밋 1~6 완료: 1471841 로컬 수집, db437dc exporter, b0ed529 락 대기 로그, ba0c1b3 Loki·Alloy, 3ae63ad 알림, 5419093 promtool 테스트.
- 구현 중 결정: socket-proxy 허용 목록에 GET `/networks` 추가. alloy의 discovery.docker가 네트워크 라벨 계산에 쓰고(403으로 실패 확인), 네트워크 목록에는 비밀값이 없다. REQ-11 판정 항목(POST·archive 거부, 타 컨테이너 접근 불가)은 그대로.
- 운영 측정 원본을 logs/prod-measure.md로 옮김. config-check·promtool 증거 기록.
- 다음 행동: 로컬 통합 확인(local-e2e) → 운영 구성 사전 검증(prod-preflight) → secret-check → 문서 커밋 → verify → 결과 리뷰.
- 로컬 통합 확인 스크립트(e2e) 백그라운드 실행 중. 문서 초안 작성: ADR 0014, docs/runbook/observability.md, docs/06 v2.2, docs/10. 문서 커밋은 runbook의 amtool 명령을 로컬에서 확인한 뒤.
- 다음 행동: e2e 결과를 logs/local-e2e.md로 기록 → prod-preflight → secret-check → 문서 커밋 → verify.
- e2e 1차: 실패 2(재기동 중복 판정 방식, 프록시 GET에 낮은 API 버전 사용) + 정리 명령(prune)이 멈춘 exporter 컨테이너를 지워 IndexError. 원인·원본은 logs/local-e2e.md. 2차: 프록시·상한·로그 표본 통과, 중복 판정은 해석 필요해 3차 독립 재검사 실행 중.
- 주의: config-check·e2e 정리에 `docker container prune -f`를 썼다. 이 로컬 Docker의 다른 멈춘 컨테이너도 지워졌을 수 있다(볼륨·이미지는 유지). 사용자에게 보고할 것. 이후 정리는 이름 지정 rm만 쓴다.
- e2e 3차(재기동 중복) 통과, prod-preflight 통과(api 재생성·Alertmanager health 미지원·amtool 따옴표를 runbook에 반영). 커밋 7 f1900d5 문서.
- 다음 행동: verify → verifier 결과 리뷰 → 개발일지 커밋 → complete.
- verify-001 실패: check-1(하네스 unittest)이 300초 시간 초과, 이후 검사 미실행. 직접 실행 결과 37건 중 2건 FAIL(718초): `검사 대상은 일반 파일이어야 합니다: .claude/worktrees/wizardly-proskuriakova-fd2557`. 저장소 안의 다른 git worktree(main 34ea684, 약 300MB, .git/info/exclude로 git 제외)를 테스트가 .claude 복사에 포함해 느려지고 실패한다. 이번 변경과 무관한 실행 환경 문제.
- 그 worktree는 다른 세션의 것일 수 있어 지우거나 옮기지 않음. block 기록 후 사용자 결정 대기.
- verify 도중 개발일지 초안(docs/devlog/2026-10-29_관측스택로그와알림.md, README 목록)을 수정했다. 다음 verify 전에는 파일을 건드리지 않는다. 개발일지는 아직 커밋 전.
- 재개 조건: .claude/worktrees 아래 worktree가 정리(또는 저장소 밖으로 이동)된 뒤 verify 재실행 → verifier 결과 리뷰 → 개발일지 커밋 → complete.
- worktree를 저장소 밖(../j-bank-worktrees/)으로 이동(사용자 결정). 개발일지 커밋. start 후 verify 재실행.

## 결과 리뷰 1회차 이후 (2026-10-29)

- 결과 리뷰 1회차: 수정 필요(중요 I-1 pg_up 원복 해제 미관찰, I-2 secret-check에 메일 아이디, I-3 prune 부작용 미보고, 권고 9). review fail 등록.
- 처리: secret-check 패턴 가림, 문서 보정 커밋 c500c5b(ADR 403/405, runbook socket-proxy 메모리·postgres 여러 줄 조회, 개발일지 테스트 수), alloy fmt 전체 출력 추가.
- 세션 재시작 뒤 Docker 컨텍스트가 desktop-linux로 바뀌어 있었고 OrbStack은 컨테이너·볼륨이 비어 있었다(이 작업이 지운 것 아님). 전역 설정은 바꾸지 않고 명령마다 DOCKER_CONTEXT=orbstack으로 실행, 로컬 스택 재빌드.
- 4차 재검사(e2e4: pg_up 원복·force-recreate 재발송·로그 비밀 검색·교착 쿼리 줄·재시작) 실행 중.
- 다음 행동: e2e4 결과 기록 → secret-check 2절(작업 폴더 포함 재검사) → verify(DOCKER_CONTEXT=orbstack) → 결과 리뷰 2회차.
- 4차 재검사 통과(logs/local-e2e.md), secret-check 2·3절 추가. 다음: verify(DOCKER_HOST=OrbStack 소켓) → 결과 리뷰 2회차.
- verify-003 통과(gradle test는 verify-002 결과 재사용, 이후 문서만 변경). 결과 리뷰 2회차 통과 권고 → review pass.
- 남은 일(작업 완료와 별도): 작업 기록 커밋 직전 비밀 재검사(R-1), PR, 18시 이후 병합, runbook대로 운영 반영·배포 후 점검(Gmail 수신, 유휴 600MiB·배치 중 150MiB, 첫 부팅 pg_up 구간, socket-proxy·loki 사용률).
- 사용자 보고 필수: prune 내역, 세션 재시작 뒤 OrbStack 비어 있음(원인 미확인)·Docker 컨텍스트 desktop-linux(전역 설정 안 바꿈), socket-proxy GET /networks 추가.
