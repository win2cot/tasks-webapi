# ---------------------------------------------------------------------------
# Private Hosted Zone — tasks.internal (ADR-0001 α案: env 毎独立 PHZ)
# Owned by tasks stack, associated with the shared platform VPC (ADR-0004).
# CNAME db.tasks.internal is a placeholder; replaced by actual RDS endpoint
# in S1Infra-1 (#318).
# ---------------------------------------------------------------------------

resource "aws_route53_zone" "tasks_internal" {
  name    = "tasks.internal"
  comment = "Private hosted zone for ${var.env} env internal services"

  vpc {
    vpc_id = var.vpc_id
  }

  tags = {
    Name = "tasks-${var.env}-phz"
  }
}

resource "aws_route53_record" "db" {
  zone_id = aws_route53_zone.tasks_internal.zone_id
  name    = "db.tasks.internal"
  type    = "CNAME"
  ttl     = 300
  records = ["placeholder.invalid"]
}

# ---------------------------------------------------------------------------
# Public Hosted Zone lookup — dgz48.xyz (shared, account-level)
# ---------------------------------------------------------------------------

data "aws_route53_zone" "public" {
  name         = "${var.base_domain}."
  private_zone = false
}

# ---------------------------------------------------------------------------
# ACM (ap-northeast-1) — tasks deep cert: *.tasks.<base_domain>
# DNS validation via the shared public hosted zone.
# Attached to shared ALB HTTPS listener as SNI cert (ADR-0004 §E).
# ---------------------------------------------------------------------------

resource "aws_acm_certificate" "tasks_regional" {
  domain_name       = "*.tasks.${var.base_domain}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "tasks-${var.env}-cert-regional"
  }
}

resource "aws_route53_record" "tasks_regional_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.tasks_regional.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.public.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "tasks_regional" {
  certificate_arn         = aws_acm_certificate.tasks_regional.arn
  validation_record_fqdns = [for r in aws_route53_record.tasks_regional_cert_validation : r.fqdn]
}

# Attach tasks deep cert to the shared ALB HTTPS listener as additional SNI cert.
resource "aws_lb_listener_certificate" "tasks_regional" {
  listener_arn    = var.alb_https_listener_arn
  certificate_arn = aws_acm_certificate_validation.tasks_regional.certificate_arn
}

# ---------------------------------------------------------------------------
# ACM (us-east-1) — CloudFront cert: tasks-<env>.<base_domain>
# Must live in us-east-1 for CloudFront. DNS validation via shared public zone.
# ---------------------------------------------------------------------------

resource "aws_acm_certificate" "tasks_cloudfront" {
  provider          = aws.us_east_1
  domain_name       = "tasks-${var.env}.${var.base_domain}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "tasks-${var.env}-cert-cloudfront"
  }
}

resource "aws_route53_record" "tasks_cloudfront_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.tasks_cloudfront.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.public.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "tasks_cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.tasks_cloudfront.arn
  validation_record_fqdns = [for r in aws_route53_record.tasks_cloudfront_cert_validation : r.fqdn]
}

# ---------------------------------------------------------------------------
# Alias records — api-dev.tasks.<base_domain> and auth-dev.tasks.<base_domain>
# Both point to the shared ALB (A alias).
# ---------------------------------------------------------------------------

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.public.zone_id
  name    = "api-${var.env}.tasks.${var.base_domain}"
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "auth" {
  zone_id = data.aws_route53_zone.public.zone_id
  name    = "auth-${var.env}.tasks.${var.base_domain}"
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

# ---------------------------------------------------------------------------
# Alias record — tasks-<env>.<base_domain> → CloudFront (S2Infra-3)
# Created only when cloudfront_domain_name is set (non-null).
# ---------------------------------------------------------------------------

resource "aws_route53_record" "frontend" {
  count = var.cloudfront_domain_name != null ? 1 : 0

  zone_id = data.aws_route53_zone.public.zone_id
  name    = "tasks-${var.env}.${var.base_domain}"
  type    = "A"

  alias {
    name                   = var.cloudfront_domain_name
    zone_id                = var.cloudfront_zone_id
    evaluate_target_health = false
  }
}
