# GitHub Actions OIDC provider는 AWS 계정당 하나만 있어야 한다(URL이 유일 식별자라
# 중복 생성하면 충돌한다). 상태 버킷과 마찬가지로 계정당 한 번만 만드는 리소스라
# 이 부트스트랩 스택에 둔다 — 다른 프로젝트가 만든 provider가 이미 있다면 이 리소스는
# import해서 쓰거나, envs/*의 compute 모듈에 넘기는 github_oidc_provider_arn 값만
# 그 provider의 ARN으로 바꿔서 쓰면 된다.
#
# 이 provider를 신뢰하는 역할 세 개를 여기서 같이 만든다:
#   - terraform_plan: 읽기 전용, PR에서 도는 `terraform plan`용(안전하게 넓게 열어도 됨)
#   - terraform_apply: 실제 인프라를 만들고 바꾸는 apply용(PowerUserAccess 수준,
#     main 브랜치 push에서만, GitHub Environment 수동 승인 뒤에만 실행됨)
#   - compute 모듈의 ecr_push 역할은 여기서 안 만든다 — ECR 리포지토리 자체가
#     compute 모듈 안에 있어서, 그 역할도 계속 compute 모듈에 둔다. 이 provider의
#     ARN만 compute 모듈에 변수로 넘겨준다(중복 생성 방지).

data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]

  tags = { Project = "j-bank", ManagedBy = "terraform", Purpose = "github-oidc" }
}

data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository}:pull_request",
        "repo:${var.github_repository}:ref:refs/heads/main",
      ]
    }
  }
}

resource "aws_iam_role" "terraform_plan" {
  name               = "jbank-github-terraform-plan"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json

  tags = { Project = "j-bank", ManagedBy = "terraform", Purpose = "terraform-plan-ci" }
}

resource "aws_iam_role_policy_attachment" "terraform_plan_readonly" {
  role       = aws_iam_role.terraform_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role" "terraform_apply" {
  name               = "jbank-github-terraform-apply"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json

  tags = { Project = "j-bank", ManagedBy = "terraform", Purpose = "terraform-apply-ci" }
}

# ponytail: PowerUserAccess는 IAM 리소스 관리를 뺀 광범위한 쓰기 권한이다. 여기서
# 만드는 모듈들(EKS·RDS·ElastiCache 등)이 IAM 역할·정책도 만들어야 해서 그 부분만
# jbank- 접두사로 스코프를 좁혀 별도 인라인 정책으로 더한다. 서비스별 세밀한 액션
# 목록으로 더 좁히는 건 실제로 어떤 액션이 막히는지 apply 로그로 확인하면서 하는 게
# 맞다 — 지금 추측만으로 좁히면 하다가 막혀서 다시 넓히는 순환이 된다.
resource "aws_iam_role_policy_attachment" "terraform_apply_poweruser" {
  role       = aws_iam_role.terraform_apply.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "terraform_apply_iam_scoped" {
  statement {
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:GetRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:CreateInstanceProfile",
      "iam:DeleteInstanceProfile",
      "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:PassRole",
    ]
    resources = [
      "arn:aws:iam::*:role/jbank-*",
      "arn:aws:iam::*:instance-profile/jbank-*",
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "iam:CreateOpenIDConnectProvider",
      "iam:GetOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "terraform_apply_iam_scoped" {
  name   = "iam-jbank-scoped"
  role   = aws_iam_role.terraform_apply.id
  policy = data.aws_iam_policy_document.terraform_apply_iam_scoped.json
}
