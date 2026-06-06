# ---------------------------------------------------------------------------
# Route53 Private Hosted Zone — tasks.internal (S0Infra-8 / ADR-0001)
# VPC ID is read from shared data.tf (data.aws_ssm_parameter.vpc_id).
# rds_endpoint wires the placeholder CNAME to the actual RDS address (S1Infra-1).
# ---------------------------------------------------------------------------

module "route53" {
  source = "../../modules/route53"

  env          = var.env
  vpc_id       = data.aws_ssm_parameter.vpc_id.value
  rds_endpoint = module.rds.db_instance_address
}
