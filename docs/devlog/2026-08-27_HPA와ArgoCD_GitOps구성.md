# 2026-08-27 HPA와 ArgoCD GitOps 구성

목표: `todo/W7.md` 수요일분 — HPA(CPU+RPS), ArgoCD GitOps.

작업 전 두 가지 스코프 결정을 사용자에게 확인받고 진행했다.

## HPA는 CPU만

TODO엔 "CPU + 초당 요청수" 기준이라고 적혀있는데, RPS 기준은
`metrics-server`만으로 안 되고 `custom.metrics.k8s.io`를 채워줄
Prometheus Adapter가 클러스터에 따로 있어야 한다. 지금 observability
스택(Prometheus/Grafana/Loki)은 docker-compose에만 있고 EKS엔 배포
안 돼 있어서, 오늘은 CPU 기준만 넣고 RPS는 TODO에 별도 하위 항목으로
남겼다.

`jbank-api` 차트에 `templates/hpa.yaml` 추가(`autoscaling.enabled`로
켜고 끔). Deployment의 `replicas` 필드는 `autoscaling.enabled`일 때
아예 안 넣도록 조건부 처리했다 — 안 그러면 매 `helm upgrade`마다
`replicaCount` 값으로 되돌아가면서 HPA와 파드 수를 놓고 계속
충돌한다. dev는 `autoscaling.enabled: false`(replicaCount: 1 고정),
prod만 `enabled: true`(min 2 / max 5 / CPU 70%)로 켰다.

## ArgoCD — 별도 prod 클러스터 없이 네임스페이스로 분리

`envs/prod` 디렉터리는 빈 스텁이고 실제로는 dev EKS 클러스터 하나만
운영 중이다(인프라아키텍처 문서 12절에 이미 "동일 계정 내 네임스페이스
분리"가 개인 프로젝트 규모의 fallback으로 적혀있다). 그래서 ArgoCD
`Application`을 두 개 만들어 같은 차트를 다른 values 파일·네임스페이스로
가리키게 했다:

- `jbank-api-dev` → `values-dev.yaml`, 네임스페이스 `jbank-dev`,
  `syncPolicy.automated`(prune+selfHeal) — push하면 바로 반영
- `jbank-api-prod` → `values-prod.yaml`, 네임스페이스 `jbank-prod`,
  `automated` 블록 없음 — `argocd app sync jbank-api-prod`로 수동
  승인해야 반영(인프라아키텍처 문서 219행의 변경관리위원회 심의를
  간소화한 승인 게이트)

## 설치 방식 — Terraform helm_release + kubectl_manifest

ArgoCD 자체 설치(`helm_release`)와 Application 매니페스트
(`kubectl_manifest`, `alekc/kubectl` provider)를 새 `modules/gitops`
모듈로 분리했다. 이 레포는 이미 EKS·RDS를 Terraform으로 파괴·재생성
반복하는 워크플로라(08/24 devlog), ArgoCD도 같은 `terraform apply`
한 번에 같이 복구되게 하는 게 일관적이다.

helm/kubectl provider 인증은 `data.aws_eks_cluster_auth` 데이터소스
대신 `exec`(aws eks get-token) 플러그인을 썼다 — 데이터소스는 plan
시점에 아직 존재하지 않는 클러스터를 읽으려다 최초 apply에서 실패하는
문제가 있는데, exec 플러그인은 apply 시점에만 토큰을 계산해서 이
문제를 피한다(terraform-aws-modules/eks 공식 예제 패턴).

hashicorp/helm 최신(v3.2)에서 `set`/`kubernetes` 블록이 전부 속성
할당(`=`)으로 스키마가 바뀐 걸 `terraform validate`가 걸러줘서 그
자리에서 고쳤다(`set { ... }` → `set = [{ ... }]`,
`kubernetes { ... }` → `kubernetes = { ... }`).

ArgoCD 서버는 아직 Ingress를 안 붙여서 `configs.params."server.insecure"
= true`로 띄운다 — 접근은 `kubectl -n argocd port-forward
svc/argocd-server`로. ALB 연결은 필요해지면 별도 작업.

## 검증

클러스터가 destroy 상태라 실제 apply는 못 하고, 정적 검증까지만:

- `helm template`/`helm lint` — HPA on/off 두 값 파일(dev/prod) 각각
  렌더링, `replicas` 필드 조건부 처리 확인.
- `terraform validate` — `modules/compute`, `modules/gitops`,
  `envs/dev` 세 곳 모두 통과(gitops/envs-dev는 helm 3.x 스키마
  이슈 수정 후 통과).
- `terraform fmt -recursive` 통과.

## 커밋

1. `feat(infra): compute 모듈에 EKS CA 인증서 output 추가`
2. `feat(infra): jbank-api Helm 차트에 HPA(CPU 기준) 추가`
3. `feat(infra): gitops Terraform 모듈 추가 (ArgoCD Helm 설치)`
4. `feat(infra): ArgoCD Application 매니페스트 추가 (dev 자동·prod 수동 동기화)`
5. `feat(infra): envs/dev에 gitops 모듈 연결 (helm·kubectl provider 설정)`
6. `docs(todo): W7 수요일분(HPA·ArgoCD) 완료 체크`

## 남은 일

- HPA RPS 기준 — Prometheus Adapter 배포 필요(별도 작업).
- 실제 클러스터에 `terraform apply`해서 ArgoCD 설치·Application
  동기화·dev/prod 네임스페이스 분리가 의도대로 동작하는지 실측 검증 —
  클러스터를 다시 올리는 세션에서. 08/25(화)분 k6 무중단 배포 검증과
  묶어서 한 번에 하면 클러스터 기동 비용을 아낄 수 있다.
- 목요일(08/28)분: Secrets Manager+External Secrets Operator, 분산 락
  도입.
