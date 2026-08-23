# ponytail: 지금은 로드밸런서 골격만 — 도메인·ACM 인증서가 없어 HTTP(80) 리스너만 두고
# 기본 액션은 고정 503 응답이다. 실제 EKS 워크로드 연결(AWS Load Balancer Controller의
# TargetGroupBinding)과 HTTPS 리스너 추가는 도메인이 생기는 다음 단계.

resource "aws_lb" "this" {
  name               = "jbank-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [var.alb_security_group_id]

  tags = merge(var.tags, { Name = "jbank-${var.environment}-alb" })
}

resource "aws_lb_target_group" "placeholder" {
  name        = "jbank-${var.environment}-placeholder-tg"
  port        = var.app_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip" # AWS Load Balancer Controller가 파드 IP를 직접 등록하는 방식과 맞춤

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = merge(var.tags, { Name = "jbank-${var.environment}-placeholder-tg" })
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "jbank-${var.environment}: no target group bound yet"
      status_code  = "503"
    }
  }
}
