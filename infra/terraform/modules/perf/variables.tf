variable "availability_zone" {
  description = "대상·부하 발생기를 함께 둘 AZ. 운영 인스턴스와 같은 AZ를 쓴다."
  type        = string
}

variable "target_instance_type" {
  description = "측정 대상. 운영(modules/ec2)과 같은 사양이어야 비교가 성립한다."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "대상 루트 볼륨. 운영과 같게 둔다."
  type        = number
  default     = 20
}

variable "loadgen_instance_type" {
  description = "부하 발생기. 비버스트형으로 두고, 측정 중 CPU가 80%를 넘으면 한 단계 올린다."
  type        = string
  default     = "c7i.large"
}

variable "github_repository" {
  description = "user_data가 clone할 \"owner/repo\"."
  type        = string
}

variable "git_ref" {
  description = "clone 후 체크아웃할 브랜치. perf 구성 파일이 있는 브랜치여야 한다."
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
