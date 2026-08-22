variable "environment" {
  description = "환경 이름(dev/prod). 리소스 이름과 태그에 쓰인다."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR 블록."
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "서브넷을 분산할 가용영역 목록. 인프라아키텍처 문서 6절 — 최소 2개 가용영역."
  type        = list(string)

  validation {
    condition     = length(var.azs) >= 2
    error_message = "가용영역은 최소 2개 이상이어야 한다(무중단·이중화 원칙, 인프라아키텍처 문서 2절)."
  }
}

variable "single_nat_gateway" {
  description = "true면 NAT Gateway를 1개만 만들어 모든 가용영역이 공유한다(비용 절감, 인프라아키텍처 문서 12절). false면 가용영역마다 하나씩 만들어 NAT 단일장애점을 없앤다."
  type        = bool
  default     = true
}

variable "tags" {
  description = "모든 리소스에 공통으로 붙일 태그."
  type        = map(string)
  default     = {}
}
