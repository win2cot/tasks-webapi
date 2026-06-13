# ---------------------------------------------------------------------------
# Frontend S3 + CloudFront (S2Infra-3 / #480)
# tasks-dev.dgz48.xyz → CloudFront → S3(tasks-dev-frontend)
#
# The Route53 alias record is created here (not in module.route53) to avoid
# a circular dependency: route53 outputs the ACM cert ARN that frontend needs,
# and frontend outputs the CloudFront domain that the alias record needs.
# ---------------------------------------------------------------------------

module "frontend" {
  source = "../../modules/frontend"

  env                 = var.env
  acm_certificate_arn = module.route53.tasks_cloudfront_cert_arn

  depends_on = [module.route53]
}

# ---------------------------------------------------------------------------
# Route53 alias record — tasks-dev.dgz48.xyz → CloudFront distribution
# ---------------------------------------------------------------------------

data "aws_route53_zone" "public" {
  name         = "dgz48.xyz."
  private_zone = false
}

resource "aws_route53_record" "frontend" {
  zone_id = data.aws_route53_zone.public.zone_id
  name    = "tasks-${var.env}.dgz48.xyz"
  type    = "A"

  alias {
    name                   = module.frontend.cloudfront_domain_name
    zone_id                = module.frontend.cloudfront_hosted_zone_id
    evaluate_target_health = false
  }
}
