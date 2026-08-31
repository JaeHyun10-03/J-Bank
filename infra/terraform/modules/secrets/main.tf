# PII_ENCRYPTION_KEY/RESIDENT_REG_NO_HASH_KEY는 애플리케이션이 Base64.getDecoder()로
# 32바이트 원시 키를 복원한다(PiiEncryptionKeyHolder/HmacKeyHolder) — random_id의
# b64_std가 표준 Base64(URL-safe 아님)라 그대로 맞는다.
resource "random_id" "pii_key" {
  byte_length = 32
}

resource "random_id" "hash_key" {
  byte_length = 32
}

# JWT 비밀키는 UTF-8 원문 바이트를 그대로 서명키로 쓴다(JwtTokenProvider) — Base64
# 디코딩이 없으므로 특수문자 없는 랜덤 문자열이면 충분하다.
resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# jbank-api ↔ jbank-product 서비스 간 호출(InternalApiKeyFilter)이 확인하는
# 공유 비밀키. 두 서비스가 같은 값을 넣어야 하므로 시크릿 하나로 관리한다.
resource "random_password" "internal_api_key" {
  length  = 48
  special = false
}

locals {
  db_url = "jdbc:postgresql://${var.db_address}:5432/${var.db_name}"

  common_secret_payload = {
    DB_URL                   = local.db_url
    DB_USERNAME              = var.db_master_username
    DB_PASSWORD              = var.db_master_password
    REDIS_HOST               = var.redis_endpoint
    REDIS_PORT               = "6379"
    KAFKA_BOOTSTRAP_SERVERS  = var.kafka_bootstrap_servers
    PII_ENCRYPTION_KEY       = random_id.pii_key.b64_std
    RESIDENT_REG_NO_HASH_KEY = random_id.hash_key.b64_std
    JWT_SECRET               = random_password.jwt_secret.result
    INTERNAL_API_KEY         = random_password.internal_api_key.result
  }

  # 서비스 간 호출 대상 URL은 네임스페이스마다 다르다(dev↔dev, prod↔prod) —
  # Helm 차트 fullname 규칙("{릴리스 이름}-{차트 이름}")과 ArgoCD Application
  # 이름(jbank-api-{key}/jbank-product-{key})이 곧 릴리스 이름이라는 전제로
  # 서비스 DNS를 조립한다.
  secret_payload = {
    for key, ns in var.namespaces : key => merge(
      local.common_secret_payload,
      {
        ACCOUNT_SERVICE_INTERNAL_URL = "http://jbank-api-${key}-jbank-api.${ns.namespace}.svc.cluster.local:8080"
        PRODUCT_SERVICE_INTERNAL_URL = "http://jbank-product-${key}-jbank-product.${ns.namespace}.svc.cluster.local:8080"
      }
    )
  }
}

resource "aws_secretsmanager_secret" "jbank_api" {
  for_each = var.namespaces

  name = "jbank-${var.environment}-${each.key}-api-secrets"
  tags = merge(var.tags, { Name = "jbank-${var.environment}-${each.key}-api-secrets" })
}

# 위 이름은 "jbank-dev-dev-api-secrets"/"jbank-dev-prod-api-secrets"처럼
# 보인다 — 앞의 dev는 이 Terraform 환경(envs/dev), 뒤의 dev/prod는
# ArgoCD가 배포하는 애플리케이션 네임스페이스로 서로 다른 축이다.

resource "aws_secretsmanager_secret_version" "jbank_api" {
  for_each = var.namespaces

  secret_id     = aws_secretsmanager_secret.jbank_api[each.key].id
  secret_string = jsonencode(local.secret_payload[each.key])
}

# ---- External Secrets Operator IRSA ----
# ESO 컨트롤러 파드가 이 역할을 맡아 각 네임스페이스의 시크릿을 읽는다.
# 컨트롤러는 클러스터에 하나뿐이라 서비스어카운트도 하나 — 네임스페이스별로
# 나누지 않고 위 두 시크릿 ARN을 모두 허용한다.
data "aws_iam_policy_document" "eso_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_issuer_url, "https://", "")}:sub"
      values   = ["system:serviceaccount:${var.eso_namespace}:${var.eso_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${replace(var.oidc_issuer_url, "https://", "")}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eso" {
  name               = "jbank-${var.environment}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.eso_assume_role.json

  tags = merge(var.tags, { Name = "jbank-${var.environment}-external-secrets" })
}

data "aws_iam_policy_document" "eso_permissions" {
  statement {
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = [for s in aws_secretsmanager_secret.jbank_api : s.arn]
  }
}

resource "aws_iam_role_policy" "eso" {
  name   = "read-jbank-secrets"
  role   = aws_iam_role.eso.id
  policy = data.aws_iam_policy_document.eso_permissions.json
}
