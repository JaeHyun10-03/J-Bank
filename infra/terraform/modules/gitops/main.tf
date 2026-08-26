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
