# 인프라아키텍처 문서 5.2절 4단 서브넷 표를 그대로 구현한다.
# CIDR은 /16 VPC를 /24로 쪼개 계층별로 10칸씩 띄워 배정한다(성장 여유):
#   public 0~, was(private) 10~, db(isolated) 20~, mgmt 30~

locals {
  az_count = length(var.azs)

  public_cidrs = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, i)]
  was_cidrs    = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, 10 + i)]
  db_cidrs     = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, 20 + i)]
  mgmt_cidrs   = [for i in range(local.az_count) : cidrsubnet(var.vpc_cidr, 8, 30 + i)]

  nat_count = var.single_nat_gateway ? 1 : local.az_count

  # was 서브넷이 바라볼 NAT Gateway id. single_nat_gateway면 전부 0번 하나를 공유한다.
  was_nat_gateway_ids = {
    for i, az in var.azs :
    az => aws_nat_gateway.this[var.single_nat_gateway ? 0 : i].id
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = "jbank-${var.environment}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-igw" })
}

# ---- 서브넷 4단 ----

resource "aws_subnet" "public" {
  for_each = { for i, az in var.azs : az => local.public_cidrs[i] }

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = each.value
  map_public_ip_on_launch = true

  tags = merge(var.tags, { Name = "jbank-${var.environment}-public-${each.key}", Tier = "public" })
}

resource "aws_subnet" "was" {
  for_each = { for i, az in var.azs : az => local.was_cidrs[i] }

  vpc_id            = aws_vpc.this.id
  availability_zone = each.key
  cidr_block        = each.value

  tags = merge(var.tags, { Name = "jbank-${var.environment}-was-${each.key}", Tier = "was" })
}

resource "aws_subnet" "db" {
  for_each = { for i, az in var.azs : az => local.db_cidrs[i] }

  vpc_id            = aws_vpc.this.id
  availability_zone = each.key
  cidr_block        = each.value

  tags = merge(var.tags, { Name = "jbank-${var.environment}-db-${each.key}", Tier = "db" })
}

resource "aws_subnet" "mgmt" {
  for_each = { for i, az in var.azs : az => local.mgmt_cidrs[i] }

  vpc_id            = aws_vpc.this.id
  availability_zone = each.key
  cidr_block        = each.value

  tags = merge(var.tags, { Name = "jbank-${var.environment}-mgmt-${each.key}", Tier = "mgmt" })
}

# ---- NAT Gateway (public 서브넷에 배치, was 서브넷 아웃바운드 전용) ----

resource "aws_eip" "nat" {
  count  = local.nat_count
  domain = "vpc"

  tags = merge(var.tags, { Name = "jbank-${var.environment}-nat-eip-${count.index}" })
}

resource "aws_nat_gateway" "this" {
  count = local.nat_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[var.azs[count.index]].id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-nat-${count.index}" })

  depends_on = [aws_internet_gateway.this]
}

# ---- 라우팅 ----

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-public-rt" })
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# was는 가용영역별 라우팅 테이블 — single_nat_gateway=false일 때 각자 자기 AZ의 NAT만 타도록.
resource "aws_route_table" "was" {
  for_each = aws_subnet.was

  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = local.was_nat_gateway_ids[each.key]
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-was-rt-${each.key}" })
}

resource "aws_route_table_association" "was" {
  for_each = aws_subnet.was

  subnet_id      = each.value.id
  route_table_id = aws_route_table.was[each.key].id
}

# db, mgmt는 인터넷 라우팅 없음 — 로컬 라우팅만 있는 빈 라우팅 테이블.
resource "aws_route_table" "db" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-db-rt" })
}

resource "aws_route_table_association" "db" {
  for_each = aws_subnet.db

  subnet_id      = each.value.id
  route_table_id = aws_route_table.db.id
}

resource "aws_route_table" "mgmt" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "jbank-${var.environment}-mgmt-rt" })
}

resource "aws_route_table_association" "mgmt" {
  for_each = aws_subnet.mgmt

  subnet_id      = each.value.id
  route_table_id = aws_route_table.mgmt.id
}
