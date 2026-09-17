# J-Bank 코어시스템 인프라 아키텍처

## 버전 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| v1.0 | 2026-07-21 | 최초 작성 - 전체 인프라 아키텍처, AWS 계정 구조, 네트워크/컴퓨트/데이터/보안 계층 설계 |
| v1.1 | 2026-07-21 | 프론트엔드 호스팅을 Vercel로 분리하는 결정을 13절에 반영. 원장·개인정보 처리 영역은 AWS에 유지하는 경계를 명시 |
| v1.2 | 2026-07-26 | 프로젝트명을 J-Bank로 변경. 배치 워크로드를 API와 같은 이미지로 운영하는 방식을 명시 |
| v2.0 | 2026-09-17 | EKS·RDS·ElastiCache·ALB·ArgoCD 구성을 단일 EC2 + Docker Compose로 교체(ADR 0010). v1.x의 설계 원문은 `v1.0.0` 태그의 이 문서에 있다 |

## 관련 문서

- J-Bank_요구사항명세서.md
- J-Bank_구현계획.md
- J-Bank_폴더구조.md
- adr/0010-ec2-single-instance.md

---

## 1. 문서 개요

이 문서는 J-Bank 코어시스템이 **현재** 어떤 환경에서 어떻게 운영되는지를 정의한다. v1.x는 실제 금융권 운영계 관례(망분리, 이중화, GitOps, 관리형 데이터 계층)를 AWS 위에 최대한 재현하는 설계였고, W7에 실제 EKS 클러스터에 배포해 무중단 배포까지 검증했다. 그 뒤 두 가지 이유로 구성을 낮췄다.

첫째, 비용이다. EKS 컨트롤플레인·워커노드·RDS·ElastiCache·ALB·NAT Gateway를 합치면 시연 구성(단일 AZ, 노드 1대)으로도 월 $200 수준의 고정비가 나갔다. 둘째, 초점이다. 앱 하나를 위해 플랫폼(클러스터 용량, ArgoCD·ESO 파드, 노드 보안그룹) 문제를 다루는 시간이 거래 코어의 정합성·동시성이라는 본래 심화 주제를 잠식했다. 두 판단의 근거와 트레이드오프는 ADR 0010에 있다.

v1.x 설계에서 배운 것(GitOps에서 이미지 태그를 어떻게 전달하는가 — ADR 0006, 클러스터 보안그룹과 노드 보안그룹의 차이 — ADR 0008)은 ADR과 개발일지에 그대로 남아 있다.

## 2. 전체 구성

```
                          ┌──────────────────────────────────────────────┐
  고객 브라우저            │ EC2 t3.small · ap-northeast-2 · 기본 VPC       │
      │                   │  Docker Compose (infra/compose/docker-compose.prod.yml)
      ▼                   │                                              │
  Vercel (Next.js) ──┐    │   caddy :80/:443 ── api.j-bank.site ──▶ api :8080
  www.j-bank.site    │    │      │             grafana.j-bank.site ─▶ grafana :3000
      │              │    │      │                                       │
      │ /api/proxy   └────┼──────┘                        api ──▶ postgres :5432
      │ (서버사이드)        │                              api ──▶ redis :6379
      └────HTTPS──────────▶│                       prometheus ──▶ api /actuator/prometheus
                          │                          grafana ──▶ prometheus
                          └──────────────────────────────────────────────┘
                                     ▲ SSM Run Command (배포)     ▲ SSM Session (운영 접근)
  GitHub Actions ─── GHCR 이미지 푸시 ┘                            운영자 ┘
```

한 대의 EC2 위에서 여섯 컨테이너가 돈다. 외부에서 들어오는 경로는 caddy의 80/443 하나뿐이고, 나머지 서비스는 Compose 내부 네트워크로만 통신한다. 프론트엔드는 Vercel에 있고 브라우저가 API를 직접 부르지 않는다 — Next.js 라우트(`/api/proxy`)가 서버사이드에서 `BACKEND_API_URL`로 프록시하므로 브라우저 기준 동일 출처이고 CORS가 발생하지 않는다(v1.x와 같은 구조).

## 3. 컴포넌트

### 3.1 컴퓨트 — EC2 한 대

- `t3.small`(2 vCPU, 2GB) Amazon Linux 2023 x86_64, gp3 20GB 암호화 루트 볼륨. `infra/terraform/modules/ec2`.
- 메모리 예산: api(JVM 힙 512m) ~600MB, postgres ~150MB, prometheus ~200MB, grafana ~120MB, redis·caddy ~60MB. OOM이 보이면 `instance_type` 변수만 `t3.medium`으로 올린다.
- user_data가 최초 부팅에 docker·compose 플러그인을 설치하고 저장소를 `/opt/jbank`에 clone한다. AMI가 갱신돼도 인스턴스를 교체하지 않는다(`ignore_changes = [ami, user_data]`) — 데이터가 루트 볼륨에 있다.
- 배치 잡(이자·정합성대사·CTR·FDS)은 k8s CronJob 대신 호스트 crontab(`/etc/cron.d/jbank`, KST 01·02·03·04시)이 `infra/compose/run-batch.sh`를 부른다. 스크립트는 api 서비스의 이미지·env_file을 그대로 쓰는 일회성 컨테이너에 `--spring.profiles.active=prod,batch --spring.batch.job.name=<job>`을 넘긴다 — v1.x CronJob과 같은 방식이다. 인스턴스가 하나라 Redisson 분산락(ADR 0005)은 지금은 사실상 로컬 락으로 동작하지만, 인스턴스를 늘려도 코드 변경 없이 같은 보장을 유지하기 위해 그대로 둔다.

### 3.2 데이터 — Compose 안의 PostgreSQL·Redis

RDS·ElastiCache 대신 같은 호스트의 컨테이너를 쓴다. 데이터는 Docker named volume(`postgres-data`)에, 즉 EBS 루트 볼륨에 있다. 관리형 서비스가 해주던 것 중 지금 없는 것: 자동 백업, Multi-AZ 장애조치, 마이너 버전 자동 패치. 백업은 6절의 다음 단계다.

### 3.3 진입점과 TLS — Caddy

`infra/compose/Caddyfile`. `api.j-bank.site`, `grafana.j-bank.site` 두 호스트에 Let's Encrypt 인증서를 자동 발급·갱신하고 HTTP를 HTTPS로 리다이렉트한다. ALB + ACM + WAF가 하던 자리다. 두 호스트의 A 레코드가 EIP를 가리켜야 발급이 된다.

### 3.4 네트워크·보안

- 기본 VPC의 퍼블릭 서브넷. NAT Gateway, 프라이빗 서브넷, VPC 엔드포인트 없음.
- 보안그룹 인바운드는 80/443만. **SSH 포트는 열지 않는다.** 운영 접근은 SSM Session Manager(`aws ssm start-session --target <instance-id>`), 배포는 SSM Run Command.
- IMDSv2 강제, EBS 암호화, 인스턴스 역할은 `AmazonSSMManagedInstanceCore`만.
- 비밀값(DB 비밀번호, PII 암호화 키, JWT 시크릿, Grafana 비밀번호)은 `infra/compose/.env`(chmod 600, gitignore)에 둔다. Secrets Manager + ESO가 하던 자리다. 키 로테이션은 수동.
- GitHub Actions → AWS 인증은 v1.x와 같이 OIDC(`infra/terraform/bootstrap/oidc.tf`). 배포 역할은 "이 인스턴스에 `AWS-RunShellScript` 보내기 + 결과 조회"만 허용한다.

### 3.5 관측 — Prometheus·Grafana

api의 `/actuator/prometheus`를 15초마다 스크랩하고(보존 15일), Grafana는 `grafana.j-bank.site`로 노출한다(자체 로그인). 대시보드·datasource 프로비저닝 파일은 로컬 Compose와 같은 `infra/compose/observability/provisioning`을 쓴다. Loki는 제거했다 — 인스턴스 한 대의 로그는 `docker logs`로 충분하고, Grafana 대시보드는 Prometheus만 쓴다.

### 3.6 CI/CD와 IaC

| 워크플로 | 트리거 | 하는 일 |
|---|---|---|
| `backend-ci.yml` | PR (백엔드 경로) | 컴파일, 단위·ArchUnit·Testcontainers 테스트, Spotless, OpenAPI 스냅샷 드리프트 검사 |
| `frontend-ci.yml` | PR (프론트 경로) | lint, tsc, next build |
| `backend-cd.yml` | main push (백엔드·Dockerfile·compose 경로) | 이미지 빌드 → GHCR 푸시(`ghcr.io/jaehyun10-03/jbank-api:<sha>`) → SSM으로 EC2에서 `git reset --hard origin/main && infra/compose/deploy.sh` 실행. api 컨테이너만 재기동 |
| `infra-cd.yml` | PR: plan / main push: apply(Environment 수동 승인) | `infra/terraform/envs/dev` |

Terraform은 두 스택이다. `bootstrap`(상태 버킷·잠금 테이블·GitHub OIDC provider·Terraform용 역할, 계정당 1회)과 `envs/dev`(`ec2` 모듈 하나). 상태는 S3, 잠금은 DynamoDB.

## 4. 최초 구축 절차

```bash
# 1. bootstrap (이미 apply돼 있으면 건너뜀)
cd infra/terraform/bootstrap && terraform init && terraform apply

# 2. EC2
cd ../envs/dev
cp terraform.tfvars.example terraform.tfvars   # github_oidc_provider_arn 채움
terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket)"
terraform apply
terraform output   # public_ip, instance_id, deploy_role_arn

# 3. DNS: api.j-bank.site, grafana.j-bank.site → public_ip (A 레코드)

# 4. 비밀값 배치 (SSH 없음, SSM 세션)
aws ssm start-session --target <instance_id>
  sudo -iu ec2-user
  cd /opt/jbank/infra/compose && cp .env.example .env && chmod 600 .env
  # openssl rand -base64 32 로 각 값을 채운 뒤
  docker compose -f docker-compose.prod.yml up -d

# 5. GitHub 저장소 설정
#   Secrets:   AWS_DEPLOY_ROLE_ARN, EC2_INSTANCE_ID
#   Variables: EC2_DEPLOY_READY=true
# 6. Vercel 프로젝트 env: BACKEND_API_URL=https://api.j-bank.site
```

이후 main에 백엔드 변경이 머지되면 backend-cd가 새 이미지를 배포한다. 인프라 변경은 PR에서 plan, 머지 후 승인을 거쳐 apply된다.

## 5. 비용

| 항목 | 월 추정 |
|---|---|
| EC2 t3.small (온디맨드, 서울) | ~$15 |
| EBS gp3 20GB | ~$2 |
| 퍼블릭 IPv4 (EIP) | ~$4 |
| S3·DynamoDB (Terraform 상태) | ~$0 |
| GHCR (public 이미지) | $0 |
| **합계** | **~$20** |

v1.x 시연 구성(EKS 컨트롤플레인 $73 + t3.medium 노드 + RDS db.t3.micro 단일 AZ + ElastiCache cache.t3.micro + ALB + NAT Gateway $32)은 ~$200/월이었다. 상시 구동 구성(Multi-AZ, 노드 2대)은 그 두 배다.

## 6. 한계와 다음 단계

| 한계 | 지금 상태 | 다음 단계(필요해질 때) |
|---|---|---|
| 백업 없음 | postgres 데이터가 EBS 한 장에만 있다 | 일 1회 `pg_dump` → S3 (cron 컨테이너 또는 호스트 crontab). EBS 스냅샷 라이프사이클 |
| 단일 장애점 | 인스턴스·AZ 하나 | 앱 무상태이므로 인스턴스 2대 + ALB로 수평 확장 가능. DB는 그때 RDS로 |
| 비밀값 수동 관리 | `.env` 파일, 로테이션 수동 | SSM Parameter Store(SecureString)에서 배포 시 읽기 |
| 배포 중 중단 | api 컨테이너 재기동 동안 수 초 503 | 인스턴스 2대가 되면 롤링. 1대에서는 Caddy 앞 blue/green 컨테이너 |
| 로그 검색 | `docker logs` | 인스턴스가 늘면 Loki 재도입 |
| WAF·Shield Advanced 없음 | Caddy 기본 + Shield Standard | 트래픽이 생기면 CloudFront + WAF를 앞에 |

## 7. 실제 금융권 방식과의 차이

v1.x 13절의 표를 현재 구성 기준으로 갱신한 것이다. "실제 방식(A)을 알고 있으면서 제약 조건에서 어떤 대안(B)을 골랐는가"라는 취지는 같다.

| 항목 | 실제 금융권 방식(A) | v1.x 대안 | 현재 대안(B) | 낮춘 이유 |
|---|---|---|---|---|
| 망분리 | 물리적 망분리 | VPC 4단 서브넷 + NAT + VPC 엔드포인트 | 퍼블릭 서브넷 1개, 보안그룹으로 80/443만 개방, SSH 폐쇄 | NAT $32/월이 앱 전체보다 비쌌다. 노출 면은 caddy 하나로 동일 |
| 컴퓨트 이중화 | 다중 AZ, 자동 장애조치 | EKS 노드 2대 + HPA + PDB | 인스턴스 1대 | 트래픽이 없는 단계의 이중화는 비용만 낸다. 앱은 무상태라 확장 경로는 열려 있다 |
| 데이터 계층 | 전용 DBMS 이중화 | RDS Multi-AZ, ElastiCache | 같은 호스트의 컨테이너 | 관리형의 가치(백업·패치·장애조치)를 지금은 쓰지 않는다 |
| 키·비밀 관리 | HSM | KMS + Secrets Manager + ESO | EBS 암호화 + `.env` | 소비자가 프로세스 하나라 배포 체계가 필요 없다 |
| 배포 | 변경관리위원회 승인 | ArgoCD GitOps + Environment 승인 | GitHub Actions → SSM. 인프라 apply는 Environment 승인 유지 | GitOps는 "클러스터 상태 = Git"이 가치인데 클러스터가 없다 |
| 운영 접근 감사 | 화면 녹화 | SSM Session Manager | SSM Session Manager (동일) | — |
| 경계 방어 | 전용 장비 | ALB + WAF + Shield Standard | Caddy + Shield Standard | 규칙 없는 WAF는 비용만 있었다 |
| 프론트엔드 | 계정계와 같은 인프라 | Vercel | Vercel (동일) | — |
