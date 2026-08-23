# 인프라아키텍처 문서 8.3절: OWASP Top 10 대응 룰셋 + SQL Injection 탐지. ALB에 실제로
# 붙이는 aws_wafv2_web_acl_association은 여기 두지 않는다 — ALB는 compute 모듈 소관이고,
# compute가 이 모듈의 SG를 참조하는 반대 방향 의존성이 이미 있어서 여기서 ALB arn을
# 다시 참조하면 순환참조가 생긴다. 연결은 두 모듈을 다 아는 루트(envs/*)에서 한다.

resource "aws_wafv2_web_acl" "alb" {
  name  = "jbank-${var.environment}-alb-waf"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "aws-common-rule-set"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "jbank-${var.environment}-common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "aws-known-bad-inputs"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "jbank-${var.environment}-known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "aws-sqli"
    priority = 3

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "jbank-${var.environment}-sqli"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "jbank-${var.environment}-alb-waf"
    sampled_requests_enabled   = true
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-alb-waf" })
}
