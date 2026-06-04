# ---------------------------------------------------------------------------
# Route53 Private Hosted Zone — tasks.internal (S0Infra-8 / ADR-0001)
# VPC ID is read from shared data.tf (data.aws_ssm_parameter.vpc_id).
# ---------------------------------------------------------------------------

module "route53" {
  source = "../../modules/route53"

  env    = var.env
  vpc_id = data.aws_ssm_parameter.vpc_id.value
}
