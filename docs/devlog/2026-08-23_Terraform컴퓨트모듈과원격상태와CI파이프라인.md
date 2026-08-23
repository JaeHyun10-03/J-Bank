# 2026-08-23 Terraform 컴퓨트 모듈·원격 상태·CI 파이프라인

목표: `todo/W6.md` 금요일분 — Terraform ALB·WAF·ECR·EKS 모듈, 원격
상태(S3+DynamoDB), plan/apply 파이프라인. 목요일분(네트워크·RDS·
ElastiCache)은 전날 세션에 이미 끝나 있었다.

## 시작 전 결정 두 가지

사용자에게 물어서 정했다.

- **EKS**: 클러스터·관리형 노드그룹·IRSA용 OIDC provider·관련 IAM
  역할을 전부 손으로 짜면 리소스가 수십 개로 늘어난다. 이미 검증된
  `terraform-aws-modules/eks` 커뮤니티 모듈로 감싸기로 함.
- **ALB HTTPS**: 도메인·ACM 인증서가 아직 없어서, 지금은 HTTP(80)
  리스너 + 고정 503 응답만 두고 HTTPS·실제 EKS 타깃그룹 연결은 도메인이
  생기는 다음 단계로 미루기로 함.

## compute 모듈

### ECR + GitHub OIDC

`backend-cd.yml`이 이미 `jbank-api` 리포지토리 이름과
`secrets.AWS_ECR_PUSH_ROLE_ARN`을 가정하고 있었다 — 그 가정을 실제로
채웠다. GitHub Actions OIDC로 장기 액세스키 없이 main 브랜치 push에서만
ECR 푸시 권한을 위임한다.

### EKS

`terraform-aws-modules/eks ~> 20.0`. 워커노드는 WAS(private) 서브넷에
배치하고 security 모듈의 `was_sg`를 추가로 붙였다. API 엔드포인트는
시연 편의상 기본 퍼블릭 허용으로 뒀다 — 실제 운영이면 `false`로 좁히고
관리존 SSM·VPN 경유로 바꿀 자리를 변수로 남겨뒀다.

### ALB

인터넷 대면 ALB + HTTP 리스너, 기본 액션은 고정 503. 타깃그룹은
`target_type = "ip"`로 만들어 나중에 AWS Load Balancer Controller가
파드 IP를 직접 등록하는 방식과 맞춰뒀다.

## security 모듈 — WAFv2

AWS 관리형 룰그룹(Common, KnownBadInputs, SQLi)으로 OWASP Top 10과
SQL Injection을 1차 방어. `aws_wafv2_web_acl_association`(실제 ALB에
붙이는 연결)은 security도 compute도 아닌 **루트(envs/dev)**에 뒀다 —
compute가 이미 security의 보안그룹을 참조하는 방향이 있어서, security
쪽에서 compute의 ALB arn을 되받으면 순환참조가 생기기 때문이다. 각
모듈은 자기가 만든 리소스의 출력값만 내놓고, 둘을 엮는 건 둘 다 아는
루트의 몫으로 남겨뒀다.

## 원격 상태 부트스트랩

상태를 담을 S3 버킷·잠금용 DynamoDB 테이블을, 그 상태를 쓸 envs/*
안에서 만들 수는 없다(만들 대상을 자기 자신의 backend로 쓰는
닭-달걀 문제). `infra/terraform/bootstrap`을 별도 스택으로 분리했다
— 계정당 한 번만 apply하는 스택이라 로컬 상태로 충분하다고 판단.

작업 도중 설계를 한 번 바꿨다: 처음엔 GitHub OIDC provider를 compute
모듈이 직접 만들거나(`create_github_oidc_provider` 토글) 기존 걸
재사용하는 두 갈래로 짰는데, bootstrap에 terraform plan/apply 역할을
추가하면서 OIDC provider 자체도 "계정당 한 번만 만드는 리소스"라는
같은 성격이라는 게 명확해졌다. 토글을 걷어내고 OIDC provider를
bootstrap으로 옮기는 리팩터 커밋을 따로 냈다 — compute 모듈은 이제
provider ARN을 필수 변수로만 받는다.

plan 역할은 `ReadOnlyAccess`(PR에서 안전하게 넓게 열어도 됨), apply
역할은 `PowerUserAccess` + `jbank-*` 접두사로 좁힌 IAM 관리 인라인
정책 조합이다. 서비스별로 액션을 하나하나 골라 더 좁히는 건 실제
apply 로그로 뭐가 막히는지 보면서 하는 게 맞다고 판단해 지금은 넓게
잡아두고 ponytail 주석을 남겼다.

## CI 파이프라인

`.github/workflows/infra-cd.yml`. PR과 main push 둘 다 `plan`을
돌리고(읽기 전용 역할), `apply`는 main push에서 `plan` 성공 후에만,
그리고 GitHub Environment(`infra-apply`)를 거쳐 실행된다 — job
트리거는 자동이지만 실행 직전 사람 승인을 기다리는 지점이 있다는 게
"apply는 수동 승인" 요구사항의 핵심이다. Environment의 필수 리뷰어
설정 자체는 저장소 설정에서 수동으로 해야 해서 워크플로 파일만으로는
안 끝난다는 점을 남겨둔다.

bootstrap이 실제로 apply되고 필요한 시크릿(`AWS_TERRAFORM_PLAN_ROLE_ARN`,
`AWS_TERRAFORM_APPLY_ROLE_ARN`, `AWS_GITHUB_OIDC_PROVIDER_ARN`,
`TF_VAR_DB_MASTER_PASSWORD`)과 변수(`TF_STATE_BUCKET`)가 채워지기
전까지는 `backend-cd.yml`의 `ECR_DEPLOY_READY`와 같은 방식으로
`TERRAFORM_CI_READY` 게이트 뒤에 숨겨뒀다.

## 검증

- 모든 Terraform 디렉터리(`bootstrap`, 모듈 4개, `envs/dev`) `terraform
  fmt`, `terraform init -backend=false`(또는 envs/dev는 `s3` 백엔드
  블록 문법만 `-backend=false`로 파싱 검증), `terraform validate` 통과.
- `envs/dev`에서 가짜 값으로 `terraform plan` 시도 — 설정 파싱까지는
  정상, 예상대로 자격증명 단계에서 멈춤(로컬에 AWS 계정 없음, 지난
  세션과 같은 한계).
- `.github/workflows/infra-cd.yml`은 `actionlint`로 별도 설치해서
  검사 — 통과. 기존 `backend-ci.yml`의 무관한 shellcheck 경고 하나가
  같이 잡혔는데 내가 건드린 파일이 아니라 손 안 댔다.

## 커밋

ECR+OIDC → EKS → ALB → WAF → envs/dev 연결(compute) → 원격 상태
부트스트랩(OIDC provider·plan/apply 역할 포함) → compute 리팩터(OIDC
토글 제거) → CI 파이프라인 → todo 체크, 9개로 나눴다. 리팩터를 발견한
시점에 기존 커밋을 고치지 않고 새 리팩터 커밋으로 뺐다.

## 다음

`todo/W6.md` 토요일분(비용 통제 워크스페이스 분리, 인프라 전체 파괴
후 재생성 검증)이 남았다. 재생성 검증은 실제 AWS 계정이 있어야 끝까지
확인 가능하다는 한계가 이번에도 똑같이 걸린다 — bootstrap과
envs/dev를 실제로 apply해볼 수 있는 시점에 다시 열어야 한다.
