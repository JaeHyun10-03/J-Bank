variable "environment" {
  description = "환경 이름(dev/prod). 리소스 이름과 태그에 쓰인다."
  type        = string
}

variable "vpc_id" {
  description = "보안그룹을 붙일 VPC id."
  type        = string
}

variable "app_port" {
  description = "ALB가 WAS로 프록시하는 애플리케이션 포트."
  type        = number
  default     = 8080
}

variable "enable_mgmt_db_access" {
  description = "관리 서브넷(운영자)에서 DB존으로의 직접 접속 허용 여부. 인프라아키텍처 문서 5.2절 — 점검 목적의 예외 규칙이라 기본값은 차단(false), 점검할 때만 잠깐 true로 켠다."
  type        = bool
  default     = false
}

variable "tags" {
  description = "모든 리소스에 공통으로 붙일 태그."
  type        = map(string)
  default     = {}
}
