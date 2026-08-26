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
