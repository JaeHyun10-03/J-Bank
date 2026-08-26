variable "argocd_namespace" {
  description = "ArgoCD 컨트롤플레인이 설치될 네임스페이스."
  type        = string
  default     = "argocd"
}

variable "argocd_chart_version" {
  description = "argo/argo-cd Helm 차트 버전."
  type        = string
  default     = "10.4.0"
}

variable "repo_url" {
  description = "ArgoCD Application이 동기화 대상으로 볼 Git 저장소 URL."
  type        = string
}

variable "target_revision" {
  description = "동기화 기준 브랜치."
  type        = string
  default     = "main"
}

variable "chart_path" {
  description = "저장소 내 jbank-api Helm 차트 경로."
  type        = string
  default     = "infra/helm/jbank-api"
}
