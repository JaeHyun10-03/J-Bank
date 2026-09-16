# backend-cd.yml이 이 리포지토리 이름("jbank-api")과 secrets.AWS_ECR_PUSH_ROLE_ARN을
# 그대로 가정하고 있다 — apply 후 이 모듈의 ecr_push_role_arn 출력값을 그 시크릿에
# 수동으로 등록해야 backend-cd.yml의 ECR_DEPLOY_READY 게이트를 켤 수 있다.

resource "aws_ecr_repository" "jbank_api" {
  name                 = "jbank-api"
  image_tag_mutability = "MUTABLE" # backend-cd.yml이 latest 태그를 매 배포마다 덮어씀

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-ecr-jbank-api" })
}

resource "aws_ecr_lifecycle_policy" "jbank_api" {
  repository = aws_ecr_repository.jbank_api.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "untagged 이미지는 ${var.ecr_untagged_image_expiry_days}일 후 정리"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.ecr_untagged_image_expiry_days
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "전체 이미지는 최근 ${var.ecr_max_image_count}개만 보관"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_max_image_count
        }
        action = { type = "expire" }
      }
    ]
  })
}

# ---- GitHub Actions OIDC — ECR 푸시 전용 역할 ----
# OIDC provider 자체는 여기서 안 만든다 — 계정당 하나만 있어야 하는 리소스라
# bootstrap 스택에서 만들고, 그 ARN을 var.github_oidc_provider_arn으로 받는다.

data "aws_iam_policy_document" "ecr_push_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # main 브랜치 push(배포 워크플로 트리거)에서만 이 역할을 가정할 수 있다 — PR 워크플로나
    # 다른 브랜치에서는 못 씀.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "ecr_push" {
  name               = "jbank-${var.environment}-github-ecr-push"
  assume_role_policy = data.aws_iam_policy_document.ecr_push_assume_role.json

  tags = merge(var.tags, { Name = "jbank-${var.environment}-github-ecr-push" })
}

data "aws_iam_policy_document" "ecr_push_permissions" {
  statement {
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
    ]
    resources = [aws_ecr_repository.jbank_api.arn]
  }
}

resource "aws_iam_role_policy" "ecr_push" {
  name   = "ecr-push"
  role   = aws_iam_role.ecr_push.id
  policy = data.aws_iam_policy_document.ecr_push_permissions.json
}
