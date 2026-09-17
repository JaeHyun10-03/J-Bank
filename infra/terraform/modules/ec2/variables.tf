variable "environment" {
  type = string
}

variable "instance_type" {
  description = "api·postgres·redis·prometheus·grafana가 한 대에 산다. 2GB(t3.small)에서 시작하고 OOM이 보이면 t3.medium으로 올린다."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "루트 볼륨 크기. postgres 데이터·prometheus TSDB·도커 이미지가 전부 여기 산다."
  type        = number
  default     = 20
}

variable "github_repository" {
  description = "\"owner/repo\". user_data의 git clone 대상이자 배포 역할의 OIDC 신뢰 조건."
  type        = string
}

variable "github_owner_id" {
  description = "GitHub 소유자(사용자/조직) 숫자 ID. OIDC 토큰의 sub는 이름 뒤에 @ID를 붙인 형식(repo:owner@ID/repo@ID:...)이라 신뢰 정책에 필요하다. `curl -s https://api.github.com/repos/<owner>/<repo>` 의 owner.id."
  type        = number
}

variable "github_repository_id" {
  description = "GitHub 저장소 숫자 ID. 같은 API 응답의 id."
  type        = number
}

variable "github_oidc_provider_arn" {
  description = "bootstrap 스택이 만든 GitHub Actions OIDC provider ARN."
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
