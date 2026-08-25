# 2026-08-25 Helm 차트와 readiness/liveness 분리

목표: `todo/W7.md` 월요일분 — Helm 차트(API+배치 CronJob), 환경별
값 파일, probe 분리.

## readiness/liveness 헬스 그룹 분리

`management.endpoint.health.probes.enabled=true` + readiness
그룹엔 `db`를 포함하고 liveness 그룹엔 상태 인디케이터만 남기는
설정을 추가했다. 같은 엔드포인트를 쓰면 DB 지연만으로 컨테이너가
통째로 재시작되는 문제가 생긴다는 게 todo의 지적이었는데, 실제로
분리해보니 그 전에 더 근본적인 문제가 있었다 — `/actuator/health`
가 `SecurityConfig`의 공개 경로 목록에 없어서 kubelet이 애초에
인증 없이 못 찌르는 상태였다. `/actuator/health/**`를 공개 경로에
추가했다.

postgres 컨테이너를 내려서 readiness=503/DOWN, liveness=200/UP로
분리 동작을 로컬에서 직접 확인했다.

## Helm 차트 골격

`infra/helm/jbank-api/`에 Chart.yaml, Deployment+Service,
values.yaml. probe는 위에서 나눈 두 엔드포인트로 각각 연결했다.
DB/Redis/Kafka 접속정보와 시크릿은 Secret 리소스 참조로만 받는다
— 실제 Secret 생성(ESO 연동)은 목요일 몫이라 이번 범위 밖.

## 배치 CronJob — 로컬 실행에서 걸린 문제

CronJob 템플릿을 쓰기 전에 실제 배치 실행 커맨드(`--spring.
profiles.active=batch --spring.batch.job.name=... runDate=...`)를
로컬 jar로 먼저 돌려봤는데, 잡이 COMPLETED로 끝나도 프로세스가 안
죽었다. `application-batch.yml`엔 `spring.batch.job.enabled: true`
뿐이라 내장 톰캣이 그대로 뜨고, Kafka 리스너·스케줄러의 비-데몬
스레드가 컨텍스트 종료 후에도 JVM을 붙잡고 있었다 —
`restartPolicy: Never`인 CronJob 파드가 절대 Completed로 안 넘어가고
영원히 Running으로 남는, 발견 안 했으면 배포하고서야 알았을 문제.

두 가지로 고쳤다:
- `application-batch.yml`에 `spring.main.web-application-type:
  none` 추가 — 배치 모드에서 톰캣 자체를 안 띄움.
- `JbankApiApplication`의 main에서 `batch` 프로파일일 때만
  `SpringApplication.exit()` + `System.exit()`로 명시적으로
  종료. API 모드는 이 조건에 안 걸려서 영향 없다.

세 잡(`interestMaturityJob`, `ctrDetectionJob`,
`ledgerReconciliationJob`) 모두 로컬 jar로 재검증 — runDate
필요/불필요 케이스 각각 COMPLETED 후 프로세스 자연 종료 확인.
API 모드(local 프로파일)도 다시 띄워서 readiness/liveness가
여전히 200으로 응답하는 것까지 재확인했다.

CronJob 템플릿은 세 잡을 `values.batchJobs` 목록으로 관리하고,
runDate가 필요한 잡만 `command`에서 `$(date +%F)`로 오늘 날짜를
계산해 넘긴다. `concurrencyPolicy: Forbid`로 같은 잡이 겹쳐 도는
것을 막았다.

## 환경별 values 파일

`values-dev.yaml` / `values-prod.yaml`은 replicaCount·리소스
요청량·`springProfilesActive`·`secretName`만 갈린다.
`image.repository`/`tag`는 이 파일들에 안 둔다 — ECR 레지스트리
URL(계정ID 포함)과 커밋 SHA 태그는 `backend-cd.yml`이 배포
시점에 `--set`으로 넘기는 값이라, 여기 박아두면 실제 값과
어긋난다.

## 검증

- `helm lint`, `helm template`(기본값·values-dev·values-prod
  각각)로 렌더링 검증. 사설 EKS 클러스터라 `kubectl` dry-run은
  네트워크상 불가 — helm 쪽 검증으로 대체.
- 백엔드: `/actuator/health/readiness`·`/liveness` 분리 동작,
  배치 3잡 로컬 실행 후 정상 종료, API 모드 정상 기동 — 총 5건
  수동 확인.

## 커밋

1. `feat(backend): actuator readiness/liveness 헬스 그룹 분리`
2. `feat(infra): Helm 차트 골격 추가 (jbank-api Deployment/Service)`
3. `fix(backend): 배치 프로파일 실행 후 JVM이 종료되지 않는 문제 수정`
4. `feat(infra): 배치 CronJob 템플릿 추가`
5. `feat(infra): 환경별 Helm values 파일 분리 (dev/prod)`

원래 계획은 4개 커밋이었는데, CronJob을 실제로 검증하는 과정에서
발견한 버그(3번)를 별도 원자 커밋으로 끼워 넣었다.

## 남은 일 (화 08/25 몫)

롤링 업데이트 종료 유예 시간(`terminationGracePeriodSeconds`)과
PodDisruptionBudget, 무중단 배포 k6 검증.
