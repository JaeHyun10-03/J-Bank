# ArgoCD 자체는 helm_release로 설치한다 — argo-cd Helm 차트가 공식적으로
# 관리되고, EKS 클러스터를 파괴·재생성할 때마다 다른 스택과 같은
# `terraform apply` 한 번으로 같이 복구된다.
#
# ArgoCD UI/서버 앞에 아직 ALB를 안 붙였다(W7 스코프 밖 — Ingress 연결은
# 필요해지면 별도 작업). 지금은 `kubectl -n argocd port-forward
# svc/argocd-server 8080:443`로만 접근하므로 서버를 insecure(TLS 종료
# 없이 평문 HTTP) 모드로 띄운다.
resource "helm_release" "argocd" {
  name             = "argocd"
  repository       = "https://argoproj.github.io/argo-helm"
  chart            = "argo-cd"
  version          = var.argocd_chart_version
  namespace        = var.argocd_namespace
  create_namespace = true

  set = [
    {
      name  = "configs.params.server\\.insecure"
      value = "true"
    }
  ]
}

# 별도 prod 클러스터/계정이 없는 개인 프로젝트 규모라(인프라아키텍처 문서
# 12절 fallback), 같은 EKS 클러스터 안에서 네임스페이스로 dev/prod를
# 나눈다 — Application 두 개가 같은 차트를 다른 values 파일·네임스페이스로
# 가리키는 구조.
#
# dev는 automated(prune+selfHeal)로 즉시 동기화하고, prod는 syncPolicy에
# automated 블록을 빼서 `argocd app sync`로 수동 승인해야만 반영되게
# 한다(인프라아키텍처 문서 219행 — 변경관리위원회 심의를 간소화한 승인
# 게이트).
resource "kubectl_manifest" "app_dev" {
  yaml_body = templatefile("${path.module}/templates/application.yaml.tpl", {
    name             = "jbank-api-dev"
    argocd_namespace = var.argocd_namespace
    namespace        = "jbank-dev"
    values_file      = "values-dev.yaml"
    repo_url         = var.repo_url
    target_revision  = var.target_revision
    chart_path       = var.chart_path
    automated_sync   = true
  })

  depends_on = [helm_release.argocd]
}

resource "kubectl_manifest" "app_prod" {
  yaml_body = templatefile("${path.module}/templates/application.yaml.tpl", {
    name             = "jbank-api-prod"
    argocd_namespace = var.argocd_namespace
    namespace        = "jbank-prod"
    values_file      = "values-prod.yaml"
    repo_url         = var.repo_url
    target_revision  = var.target_revision
    chart_path       = var.chart_path
    automated_sync   = false
  })

  depends_on = [helm_release.argocd]
}
