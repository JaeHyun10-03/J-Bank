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

locals {
  db_url = "jdbc:postgresql://${var.db_address}:5432/${var.db_name}"

  secret_payload = {
    DB_URL                   = local.db_url
    DB_USERNAME              = var.db_master_username
    DB_PASSWORD              = var.db_master_password
    REDIS_HOST               = var.redis_endpoint
    REDIS_PORT               = "6379"
    KAFKA_BOOTSTRAP_SERVERS  = var.kafka_bootstrap_servers
    PII_ENCRYPTION_KEY       = random_id.pii_key.b64_std
    RESIDENT_REG_NO_HASH_KEY = random_id.hash_key.b64_std
    JWT_SECRET               = random_password.jwt_secret.result
  }
}

resource "aws_secretsmanager_secret" "jbank_api" {
  for_each = var.namespaces

  name = "jbank-${var.environment}-${each.key}-api-secrets"
  tags = merge(var.tags, { Name = "jbank-${var.environment}-${each.key}-api-secrets" })
}

resource "aws_secretsmanager_secret_version" "jbank_api" {
  for_each = var.namespaces

  secret_id     = aws_secretsmanager_secret.jbank_api[each.key].id
  secret_string = jsonencode(local.secret_payload)
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
