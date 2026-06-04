# ---------------------------------------------------------------------------
# VPC lookup — used to scope egress to VPC CIDR
# ---------------------------------------------------------------------------

data "aws_vpc" "main" {
  id = var.vpc_id
}

# ---------------------------------------------------------------------------
# SG-ALB — inbound HTTP/HTTPS from any (IPv4 + IPv6), outbound to VPC CIDR
# ---------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "platform-${var.env}-sg-alb"
  description = "Shared ALB: inbound HTTP/HTTPS from anywhere"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP from IPv4"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description      = "HTTP from IPv6"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "HTTPS from IPv4"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description      = "HTTPS from IPv6"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    description = "All traffic to VPC CIDR"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }

  tags = {
    Name = "platform-${var.env}-sg-alb"
  }
}

# ---------------------------------------------------------------------------
# ALB — internet-facing, dual public subnets
# ---------------------------------------------------------------------------

resource "aws_lb" "main" {
  name                       = "platform-${var.env}-alb"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = var.public_subnet_ids
  drop_invalid_header_fields = true
  enable_http2               = true
  idle_timeout               = 60
  enable_deletion_protection = var.enable_deletion_protection

  tags = {
    Name = "platform-${var.env}-alb"
  }
}

# ---------------------------------------------------------------------------
# ACM — base wildcard cert (*.base_domain + SAN base_domain)
#        DNS validation via shared Route53 public hosted zone
# ---------------------------------------------------------------------------

data "aws_route53_zone" "base" {
  name         = "${var.base_domain}."
  private_zone = false
}

resource "aws_acm_certificate" "base" {
  domain_name               = "*.${var.base_domain}"
  subject_alternative_names = [var.base_domain]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "platform-${var.env}-cert-base"
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.base.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.base.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "base" {
  certificate_arn         = aws_acm_certificate.base.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}

# ---------------------------------------------------------------------------
# HTTPS Listener :443 — default 404 fixed-response + HSTS injection
# ---------------------------------------------------------------------------

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.base.certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "no route"
      status_code  = "404"
    }
  }

  tags = {
    Name = "platform-${var.env}-listener-https"
  }
}

# HSTS: Do NOT add preload — shared apex dgz48.xyz must not be preload-registered.
resource "aws_lb_listener_attribute" "https_hsts" {
  listener_arn = aws_lb_listener.https.arn
  key          = "routing.http.response.strict_transport_security.header_value"
  value        = "max-age=${var.hsts_max_age}; includeSubDomains"
}

# ---------------------------------------------------------------------------
# HTTP Listener :80 — redirect to HTTPS 301
# ---------------------------------------------------------------------------

resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }

  tags = {
    Name = "platform-${var.env}-listener-http-redirect"
  }
}
