# ADR 0010: EKS·ArgoCD 배포 구성을 EC2 단일 인스턴스 + Docker Compose로 낮춤

## 상태

승인됨. 2026-09-17. ADR 0006(GitOps 이미지 태그 전달), ADR 0008(EKS 노드 보안그룹
결함)은 이 결정으로 **폐기**된다 — 두 문서가 다루는 구성 자체가 사라졌다. 기록으로는
유지하며, 원래 구성 전체는 `v1.0.0` 태그에 있다.

## 배경

W6~W7에 인프라아키텍처 문서 v1.x대로 Terraform 88개 리소스(VPC 4단 서브넷·NAT,
EKS, RDS, ElastiCache, ALB·WAF, KMS, Secrets Manager + ESO, ArgoCD)를 올리고 실제
클러스터에 배포해 롤링 업데이트 중 실패 요청 0건까지 검증했다(perf/README W7,
devlog 2026-08-31). 그 뒤 두 가지가 문제가 됐다.

**비용.** 시연 구성(단일 AZ, 노드 1대)으로도 월 $200 수준이었다 — EKS 컨트롤플레인
$73, NAT Gateway $32가 각각 앱이 실제로 쓰는 컴퓨트보다 컸다. "작업 끝나면 destroy"
운영 원칙(v1.x 12절)을 지키면 과금은 막지만, 그 대가로 **살아 있는 배포가 없는**
포트폴리오가 된다. 실제로 이 ADR을 쓰는 시점에 `demo` 워크스페이스는 state 0건이었다.

**초점.** W7 devlog가 기록한 문제 대부분이 앱이 아니라 플랫폼 것이었다 — 노드 1대의
max-pods 17개를 ArgoCD·ESO·CoreDNS가 먼저 차지해 롤링에 필요한 서지 여유가 없던 것,
클러스터 보안그룹과 노드 보안그룹의 차이로 파드가 RDS에 못 붙던 것, GitOps selfHeal이
CI가 넣은 이미지 태그를 되돌리던 것. 각각 배울 게 있었고 ADR로 남겼지만, 이 프로젝트의
심화 주제는 원장 정합성·동시성·계좌 경합 성능이고 그쪽에 쓸 시간이 이 문제들에 갔다.

한편 앱은 구조적으로 이미 단순해진 상태였다 — 상품 서비스를 모놀리스로 통합하고
Kafka를 걷어낸 직후라, 배포 단위가 이미지 하나·프로세스 하나다. 그 앱을 위한 인프라가
클러스터·서비스 메시급이어야 할 이유가 없었다.

## 결정

EC2 `t3.small` 한 대 위에 Docker Compose로 caddy·api·postgres·redis·prometheus·grafana를
띄운다. 상세는 인프라아키텍처 문서 v2.0.

| v1.x | 현재 | 비고 |
|---|---|---|
| EKS + Helm + HPA/PDB | Compose `docker-compose.prod.yml` | 앱은 무상태. 인스턴스를 늘려야 할 때 확장 경로는 남아 있다 |
| RDS Multi-AZ, ElastiCache | 같은 호스트 컨테이너 + EBS 볼륨 | 백업·장애조치를 잃는다. 백업은 다음 단계(6절) |
| ALB + ACM + WAF | Caddy(Let's Encrypt 자동) | 규칙 없는 WAF는 비용만 있었다 |
| VPC 4단 + NAT + 엔드포인트 | 기본 VPC 퍼블릭 서브넷, SG 80/443 | 노출 면은 어차피 caddy 하나 |
| Secrets Manager + ESO | `.env`(chmod 600) | 소비자가 프로세스 하나 |
| ECR + ArgoCD GitOps | GHCR + SSM Run Command | "클러스터 상태 = Git"이 GitOps의 가치인데 클러스터가 없다 |
| k8s CronJob 4개 | 호스트 crontab + `run-batch.sh` | 같은 이미지·같은 인자 |
| Loki | 제거 | 1대 로그는 `docker logs`. 대시보드는 Prometheus만 씀 |
| SSM Session Manager, OIDC, Terraform 상태 S3 | 유지 | 바꿀 이유 없음 |

유지한 앱 쪽 요소: Actuator readiness/liveness와 graceful shutdown(Compose healthcheck에
그대로 쓰임), Redisson 분산락(ADR 0005 — 인스턴스가 늘어도 코드 변경 없이 같은 보장).

## 대안

- **Terraform까지 전부 삭제, 로컬 Compose만.** 살아 있는 배포가 없어진다. 프론트가
  Vercel에 있으므로 백엔드도 어딘가에 있어야 한다.
- **ECS Fargate.** 컨트롤플레인 비용은 없지만 ALB($16+)와 NAT 또는 퍼블릭 IP 과금이
  붙고, 데이터 계층은 여전히 RDS·ElastiCache가 필요해 $60~80 수준. Compose 파일을
  task definition으로 다시 써야 한다. 지금 규모에서 얻는 것(오케스트레이션)이 없다.
- **EC2 + RDS + ElastiCache(관리형 데이터만 유지).** 백업·장애조치를 얻지만 월 $50~70.
  데이터 유실이 실제 손해로 이어지는 시점이 오면 이 경로로 올리는 것이 맞다. 지금은
  아니다.
- **ARM(t4g.small)로 $3/월 더 절약.** Dockerfile이 이미지 안에서 gradle 빌드를 하므로
  CI에서 QEMU 크로스 빌드가 필요해 빌드 시간이 몇 배 늘어난다. x86 유지.

## 결과

- 월 고정비 ~$200 → ~$20. 상시 배포 유지 가능.
- `infra/` 1,964줄 삭제, 약 300줄 추가. Terraform 모듈 6개 → 1개.
- 잃은 것: 자동 백업, AZ 장애조치, 무중단 배포(api 재기동 동안 수 초 503), 비밀값
  중앙 관리. 각각 필요해지는 조건과 다음 단계를 인프라아키텍처 문서 6절에 적었다.
- 얻은 것: 인프라 문제에 쓰는 시간이 사라져 원장·동시성 심화에 집중할 수 있다.
  면접에서는 "왜 EKS를 썼고, 왜 걷어냈는가"를 v1.0.0 태그·ADR 0006/0008·이 문서로
  양쪽 다 설명할 수 있다.
