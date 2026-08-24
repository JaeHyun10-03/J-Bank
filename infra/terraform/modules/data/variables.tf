variable "environment" {
  description = "환경 이름(dev/prod). 리소스 이름과 태그에 쓰인다."
  type        = string
}

variable "db_subnet_ids" {
  description = "RDS·ElastiCache를 배치할 isolated(DB존) 서브넷 id 목록. 최소 2개 가용영역."
  type        = list(string)
}

variable "db_security_group_id" {
  description = "RDS에 붙일 보안그룹 id(security 모듈의 db_sg_id)."
  type        = string
}

variable "redis_security_group_id" {
  description = "ElastiCache에 붙일 보안그룹 id(security 모듈의 redis_sg_id)."
  type        = string
}

variable "db_engine_version" {
  description = "RDS PostgreSQL 엔진 버전. 16.4는 AWS에서 더 이상 제공하지 않아(2026-08-24 확인) 16.x 최신 패치로 둔다."
  type        = string
  default     = "16.15"
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스. 시연 목적 최소 사양이 기본값 — 부하가 커지면 조정."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS 할당 스토리지(GB)."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "초기 생성할 데이터베이스 이름."
  type        = string
  default     = "jbank"
}

variable "db_master_username" {
  description = "RDS 마스터 사용자명."
  type        = string
  default     = "jbank_admin"
}

variable "db_master_password" {
  description = "RDS 마스터 비밀번호. 기본값 없음 — tfvars나 -var로 직접 넘긴다. Secrets Manager 자동 로테이션 연동은 Phase 2(인프라아키텍처 문서 11절)."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.db_master_password) >= 12
    error_message = "RDS 마스터 비밀번호는 최소 12자 이상이어야 한다."
  }
}

variable "multi_az" {
  description = "RDS Multi-AZ 여부. 인프라아키텍처 문서 7절 — 무중단 원칙상 기본 true."
  type        = bool
  default     = true
}

variable "backup_retention_days" {
  description = "RDS 자동 스냅샷 보존 일수(인프라아키텍처 문서 7절)."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "RDS 삭제 방지. 시연·개발 환경은 자주 파괴·재생성하므로 기본 false, prod는 true로 덮어쓴다(인프라아키텍처 문서 12절 파괴 습관)."
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "삭제 시 최종 스냅샷 생략 여부. 시연·개발 환경은 재생성 검증이 잦아 기본 true, prod는 false로 덮어쓴다."
  type        = bool
  default     = true
}

variable "redis_node_type" {
  description = "ElastiCache 노드 타입. Phase 1은 단일 노드(클러스터 모드는 Phase 3, 인프라아키텍처 문서 7절)."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_engine_version" {
  description = "ElastiCache Redis 엔진 버전."
  type        = string
  default     = "7.1"
}

variable "tags" {
  description = "모든 리소스에 공통으로 붙일 태그."
  type        = map(string)
  default     = {}
}
