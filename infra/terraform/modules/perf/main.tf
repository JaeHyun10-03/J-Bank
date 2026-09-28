# 부하 테스트 전용 환경(docs: perf/README.md "EC2 부하 테스트" 절). 측정할 때만 apply하고 끝나면
# destroy한다. 대상(target)은 운영(modules/ec2)과 같은 사양·AMI 계열·디스크·크레딧 모드로 두고,
# 부하 발생기(loadgen)는 같은 AZ의 비버스트형 인스턴스로 둔다.
#
# 운영 모듈과 다른 점: 공개 인바운드·EIP·GitHub 배포 역할·배치 cron이 없다. 대상 인바운드는
# 부하 발생기 보안그룹에서 오는 측정(443)·수집(exporter) 포트뿐이다. 두 인스턴스 모두
# SSH 없이 SSM으로만 접근한다.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnet" "selected" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = var.availability_zone
  default_for_az    = true
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

resource "aws_security_group" "loadgen" {
  name        = "jbank-perf-loadgen"
  description = "perf load generator; no inbound, use SSM"
  vpc_id      = data.aws_vpc.default.id
  tags        = var.tags

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "target" {
  name        = "jbank-perf-target"
  description = "perf target; inbound only from loadgen"
  vpc_id      = data.aws_vpc.default.id
  tags        = var.tags

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 443: caddy(측정 경로). 9091: api 지표 프록시. 9100·8081·9187: node_exporter·cAdvisor·postgres_exporter.
resource "aws_vpc_security_group_ingress_rule" "target_from_loadgen" {
  for_each = toset(["443", "9091", "9100", "8081", "9187"])

  security_group_id            = aws_security_group.target.id
  referenced_security_group_id = aws_security_group.loadgen.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
  description                  = "from perf loadgen"
  tags                         = var.tags
}

data "aws_iam_policy_document" "assume_ec2" {
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
  name               = "jbank-perf-instance"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance" {
  name = "jbank-perf-instance"
  role = aws_iam_role.instance.name
  tags = var.tags
}

resource "aws_instance" "target" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.target_instance_type
  subnet_id                   = data.aws_subnet.selected.id
  vpc_security_group_ids      = [aws_security_group.target.id]
  iam_instance_profile        = aws_iam_instance_profile.instance.name
  associate_public_ip_address = true # 이미지 pull·git clone용 아웃바운드. 인바운드는 SG가 막는다.

  # 운영 인스턴스와 같은 모드(2026-09-27 확인: unlimited). 크레딧 고갈이 무너짐 원인으로 섞이지 않게 한다.
  credit_specification {
    cpu_credits = "unlimited"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required"
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    github_repository = var.github_repository
    git_ref           = var.git_ref
    install_k6        = false
  })

  tags = merge(var.tags, { Name = "jbank-perf-target", Role = "target" })
}

resource "aws_instance" "loadgen" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.loadgen_instance_type
  subnet_id                   = data.aws_subnet.selected.id
  vpc_security_group_ids      = [aws_security_group.loadgen.id]
  iam_instance_profile        = aws_iam_instance_profile.instance.name
  associate_public_ip_address = true

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required"
  }

  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    github_repository = var.github_repository
    git_ref           = var.git_ref
    install_k6        = true
  })

  tags = merge(var.tags, { Name = "jbank-perf-loadgen", Role = "loadgen" })
}
