# ---------------------------------------------------------------------------
# Security Groups — SG-ECS / SG-RDS (S0Infra-5 / ADR-0004)
# SG-ALB is owned by platform/alb (#242); referenced here via SSM.
# ---------------------------------------------------------------------------

module "security_group" {
  source = "../../modules/security_group"

  env       = var.env
  vpc_id    = data.aws_ssm_parameter.vpc_id.value
  alb_sg_id = data.aws_ssm_parameter.alb_sg_id.value
}
