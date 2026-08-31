# External Secrets Operator 컨트롤러 자체는 helm_release로 설치한다 —
# ArgoCD와 같은 이유로, 클러스터를 파괴·재생성해도 같은 apply 한 번에
# 같이 복구되게 하기 위해서다.
resource "helm_release" "external_secrets" {
  name             = "external-secrets"
  repository       = "https://charts.external-secrets.io"
  chart            = "external-secrets"
  version          = var.eso_chart_version
  namespace        = var.eso_namespace
  create_namespace = true

  # 컨트롤러 자신의 서비스어카운트에 IRSA 역할을 붙인다 — 별도 전용
  # 서비스어카운트를 안 두는 이유는, 이 클러스터엔 ESO 컨트롤러가
  # 하나뿐이고 ClusterSecretStore도 이 하나만 쓰기 때문이다.
  set = [
    {
      name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
      value = aws_iam_role.eso.arn
    }
  ]
}

# ClusterSecretStore — auth 블록을 생략하면 컨트롤러 파드 자신의 자격증명(IRSA로
# 발급받은 임시 자격증명)을 그대로 쓴다. 네임스페이스별 SecretStore를 따로 두지
# 않는 이유는 시크릿 소스가 계정 하나·리전 하나뿐이라 이 편이 더 단순하기 때문.
resource "kubectl_manifest" "cluster_secret_store" {
  yaml_body = <<-YAML
    apiVersion: external-secrets.io/v1
    kind: ClusterSecretStore
    metadata:
      name: aws-secrets-manager
    spec:
      provider:
        aws:
          service: SecretsManager
          region: ${var.aws_region}
  YAML

  depends_on = [helm_release.external_secrets]
}

# jbank-dev/jbank-prod 네임스페이스를 여기서 먼저 만든다 — ArgoCD
# Application도 syncOptions.CreateNamespace=true로 같은 네임스페이스를
# 만들지만, 동기화는 비동기라 이 apply 시점엔 아직 없을 수 있다.
# ExternalSecret은 네임스페이스가 존재해야 생성되므로 여기서 먼저
# 만들어두면 두 경로 중 어느 쪽이 먼저 와도 문제없다(이미 있으면 그대로
# 유지).
resource "kubectl_manifest" "namespace" {
  for_each = var.namespaces

  yaml_body = <<-YAML
    apiVersion: v1
    kind: Namespace
    metadata:
      name: ${each.value.namespace}
  YAML
}

# 네임스페이스별 ExternalSecret — dataFrom.extract가 Secrets Manager
# 시크릿의 JSON 키를 그대로 K8s Secret 키로 복사한다. 그러면 jbank-api
# Helm 차트의 envFrom.secretRef가 기대하는 DB_URL/JWT_SECRET 등의 키
# 이름과 1:1로 맞아 떨어진다.
resource "kubectl_manifest" "external_secret" {
  for_each = var.namespaces

  yaml_body = <<-YAML
    apiVersion: external-secrets.io/v1
    kind: ExternalSecret
    metadata:
      name: jbank-api-secrets
      namespace: ${each.value.namespace}
    spec:
      secretStoreRef:
        name: aws-secrets-manager
        kind: ClusterSecretStore
      target:
        name: ${each.value.secret_name}
        creationPolicy: Owner
      refreshInterval: 1h
      dataFrom:
        - extract:
            key: ${aws_secretsmanager_secret.jbank_api[each.key].name}
  YAML

  depends_on = [kubectl_manifest.cluster_secret_store, kubectl_manifest.namespace]
}
