# 2026-08-22 Terraform 네트워크·RDS·ElastiCache 모듈

목표: `todo/W6.md` 목요일분 — Terraform 네트워크 모듈(4단 서브넷),
RDS·ElastiCache 모듈. 수요일분(복합 인덱스 반영·재측정)은 전날 세션에
이미 끝나 있었다.

## 준비

로컬에 Terraform CLI가 없어(`terraform` 커맨드 자체가 homebrew-core에서
빠져 있음) HashiCorp 공식 탭(`hashicorp/tap/terraform`)으로 1.15.8을
새로 설치했다. AWS 자격증명은 로컬에 없어 `terraform apply`는 애초에
스코프 밖 — `terraform fmt`, `terraform validate`, (가짜 변수값으로)
`terraform plan`까지만으로 "빌드 가능한 상태"를 확인했다.

## 모듈 구성

`docs/06_J-Bank_인프라아키텍처.md` 5·7절을 그대로 옮겼다. Phase 1
범위만 — ALB·WAF·ECR·EKS·원격 상태는 금요일분이라 뺐다.

### network 모듈

5.2절 4단 서브넷 표 그대로: public(ALB·NAT)/was(EKS 워커·배치)/db(RDS·
Redis·MSK, 인터넷 라우팅 없음)/mgmt(SSM, 인터넷 라우팅 없음). VPC
`/16`을 `/24`로 쪼개 계층별로 10칸씩 띄워 배정(public 0~, was 10~,
db 20~, mgmt 30~) — 나중에 서브넷이 늘어도 CIDR이 안 겹치게 여유를
뒀다. NAT Gateway는 `single_nat_gateway` 변수로 기본 1개 공유(비용
절감, 12절 근거)로 두되 필요하면 가용영역별로 늘릴 수 있게 했다.

### security 모듈

계층 간 화이트리스트: ALB(443 인바운드 전체 허용) → WAS(ALB SG에서만)
→ DB·Redis(WAS SG에서만). 관리존→DB존 직접 접속은 "점검 목적의 예외
규칙, 상시 접속은 차단"이라는 문서 문구를 그대로 `enable_mgmt_db_access`
변수(기본 false)로 옮겼다.

막힌 부분: AWS 보안그룹 `description` 필드가 정규식
`^[0-9A-Za-z_ .:/()#,@\[\]+=&;{}!$*-]*$`만 허용해서 한글·화살표(→)가
전부 거부됐다(`terraform validate` 통과 후 실제로는 `terraform plan`
단계에서나 걸릴 문제라 처음엔 안 보이다가, 자격증명 없이도 provider
스키마 검증에서 바로 잡혔다). 리소스 이름·주석은 한글 그대로 두고
`description` 값만 영문으로 바꿨다.

### data 모듈

RDS PostgreSQL Multi-AZ, gp3, `storage_encrypted = true`(AWS 기본
관리형 키 — 커스텀 KMS 키는 Phase 2 Secrets Manager 로테이션과 묶어서
나중에 하기로 사용자와 합의). ElastiCache Redis는 Phase 1 스코프대로
클러스터 모드 없이 단일 노드(`aws_elasticache_cluster`) — OTP·갱신
토큰 화이트리스트·로그인 실패 카운터가 만료 있는 휘발성 데이터라
유실돼도 재시도로 복구되는 성질이라는 문서 7절 근거를 그대로 반영해
지금은 복제본 없이도 충분하다고 판단했다.

dev·prod가 자주 파괴·재생성될 걸 감안해 `deletion_protection`,
`skip_final_snapshot`을 변수로 빼고 dev는 각각 false/true(파괴 쉽게),
prod는 호출부에서 뒤집을 수 있게 했다.

### envs/dev

network → security → data 순서로 엮었다. 상태 파일은 아직 로컬
백엔드 — 금요일분에서 S3+DynamoDB로 바꿀 자리를 `versions.tf`에
주석으로 남겨뒀다. RDS 마스터 비밀번호는 `terraform.tfvars.example`만
커밋하고 실제 값은 gitignore 대상인 `terraform.tfvars`로 분리했다
(`.gitignore`에 `terraform.tfvars`, `*.auto.tfvars` 패턴 추가).

## 검증

- 모듈 4개(`network`/`security`/`data`/`envs/dev`) 전부
  `terraform fmt`, `terraform init -backend=false`(모듈) 또는
  `terraform init`(envs/dev), `terraform validate` 통과.
- `envs/dev`에서 가짜 비밀번호로 `terraform plan` 시도 — 설정 파싱과
  변수 해석까지는 정상적으로 지나가고, 예상대로 "No valid credential
  sources found"에서 멈춘다. 실제 AWS 계정을 붙이기 전까지 여기까지가
  로컬에서 확인 가능한 최대치다.

## 커밋

network → security → data → envs/dev 연결 → todo 체크, 5개로
나눴다. 각 모듈이 독립적으로 validate 가능한 단위라 자연스럽게
갈렸다.

## 다음

`todo/W6.md` 금요일분(ALB·WAF·ECR·EKS 모듈, 원격 상태 S3+DynamoDB,
plan/apply 파이프라인 분리)이 남았다. 토요일분(비용 통제 워크스페이스
분리, 파괴 후 재생성 검증)은 실제 AWS 계정이 있어야 끝까지 확인
가능하다는 점을 다음 세션에 미리 인지해둘 필요가 있다.
