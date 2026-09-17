# 단일 EC2 위에 Docker Compose(infra/compose/docker-compose.prod.yml)로 전체 스택을
# 띄운다. EKS·RDS·ElastiCache·ALB·NAT를 쓰던 v1.0.0 구성은 앱 규모 대비 월 $200 수준의
# 고정비가 나가 이 구성으로 낮췄다(docs/adr/0010). 기본 VPC의 퍼블릭 서브넷을 그대로
# 쓰고, 인바운드는 caddy가 받는 80/443만 연다. SSH 포트는 열지 않고 운영 접근은
# SSM Session Manager로 한다.

locals {
  github_owner = split("/", var.github_repository)[0]
  github_repo  = split("/", var.github_repository)[1]
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# Amazon Linux 2023: SSM Agent 내장, dnf로 docker 설치 가능.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

resource "aws_security_group" "app" {
  name        = "jbank-${var.environment}-app"
  description = "caddy(80/443) only; SSH closed, use SSM"
  vpc_id      = data.aws_vpc.default.id
  tags        = var.tags

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP (ACME challenge, HTTPS redirect)"
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 인스턴스 역할: SSM으로 명령을 받고 세션을 열기 위한 권한만.
data "aws_iam_policy_document" "instance_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "jbank-${var.environment}-instance"
  assume_role_policy = data.aws_iam_policy_document.instance_assume_role.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "instance_ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance" {
  name = "jbank-${var.environment}-instance"
  role = aws_iam_role.instance.name
  tags = var.tags
}

resource "aws_instance" "app" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.instance_type
  subnet_id                   = data.aws_subnets.default.ids[0]
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.instance.name
  associate_public_ip_address = true

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required" # IMDSv2 강제
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    github_repository = var.github_repository
  })

  # AMI가 새로 나와도 인스턴스를 갈아엎지 않는다 — 데이터가 루트 볼륨에 있다.
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-app" })
}

resource "aws_eip" "app" {
  domain = "vpc"
  tags   = merge(var.tags, { Name = "jbank-${var.environment}-app" })
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}

# GitHub Actions(backend-cd)가 맡는 배포 역할. 이미지는 GHCR에 올리므로 AWS 쪽
# 권한은 "이 인스턴스에 SSM 명령 보내고 결과 읽기"만 있으면 된다.
data "aws_iam_policy_document" "deploy_assume_role" {
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
    # GitHub의 sub 클레임은 "repo:owner@ownerId/repo@repoId:ref:..." 형식이다 — 이름만 쓴
    # "repo:owner/repo:ref:..."는 매칭되지 않아 'Not authorized to perform
    # sts:AssumeRoleWithWebIdentity'가 난다(2026-09-17 첫 배포에서 확인). ID가 붙어
    # 있어 저장소 이름이 바뀌거나 재생성돼도 다른 저장소가 이 역할을 못 쓴다.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_owner}@${var.github_owner_id}/${local.github_repo}@${var.github_repository_id}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "jbank-${var.environment}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_assume_role.json
  tags               = var.tags
}

data "aws_iam_policy_document" "deploy" {
  statement {
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = [aws_instance.app.arn]
  }
  statement {
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:*::document/AWS-RunShellScript"]
  }
  statement {
    effect    = "Allow"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "ssm-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
