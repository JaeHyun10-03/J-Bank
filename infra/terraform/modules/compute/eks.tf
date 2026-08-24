# 클러스터·노드그룹·IRSA OIDC provider·관련 IAM 역할을 전부 손으로 짜면 리소스가 수십
# 개로 늘어난다. 이미 검증된 커뮤니티 모듈(terraform-aws-modules/eks)을 감싸서 쓴다.
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = "jbank-${var.environment}"
  cluster_version = var.eks_cluster_version

  vpc_id     = var.vpc_id
  subnet_ids = var.was_subnet_ids # EKS 워커노드는 WAS(private) 서브넷(인프라아키텍처 문서 5.2절)

  cluster_endpoint_public_access  = var.eks_endpoint_public_access
  cluster_endpoint_private_access = true

  # 모듈 기본값은 false라 terraform을 실행한 IAM 주체가 kubectl 접근 권한이 없는 채로
  # 클러스터가 생성된다(파괴·재생성 검증 중 실측). 시연 목적상 클러스터를 만든 사람이
  # 바로 kubectl을 쓸 수 있어야 하므로 true로 켠다.
  enable_cluster_creator_admin_permissions = true

  # 워커노드는 security 모듈에서 만든 was_sg(ALB로부터만 인바운드)를 그대로 추가로 붙인다.
  # 모듈이 클러스터 통신용 보안그룹은 자체 생성하고, 이건 애플리케이션 계층 규칙만 더한다.
  cluster_additional_security_group_ids = [var.was_security_group_id]

  eks_managed_node_groups = {
    default = {
      min_size       = var.node_group_min_size
      max_size       = var.node_group_max_size
      desired_size   = var.node_group_desired_size
      instance_types = var.node_instance_types
      capacity_type  = "ON_DEMAND"
    }
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-eks" })
}
