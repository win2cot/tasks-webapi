# ---------------------------------------------------------------------------
# Route53 alias records + ACM certs (S1Infra-6 / #323):
#   - *.tasks.dgz48.xyz  (ap-northeast-1, SNI on shared ALB)
#   - tasks-dev.dgz48.xyz (us-east-1, CloudFront)
# ---------------------------------------------------------------------------

module "route53" {
  source = "../../modules/route53"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  env    = var.env
  vpc_id = data.aws_ssm_parameter.vpc_id.value

  alb_https_listener_arn = data.aws_ssm_parameter.alb_https_listener_arn.value
  alb_dns_name           = data.aws_ssm_parameter.alb_dns_name.value
  alb_zone_id            = data.aws_ssm_parameter.alb_zone_id.value
}
