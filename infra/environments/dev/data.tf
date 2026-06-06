# ---------------------------------------------------------------------------
# Shared data sources — platform SSM outputs (ADR-0004)
# Centralised here so all feature .tf files in this root module can reference
# these without re-declaring the data resource.
# ---------------------------------------------------------------------------

data "aws_ssm_parameter" "vpc_id" {
  name = "/platform/${var.env}/vpc-id"
}

data "aws_ssm_parameter" "alb_sg_id" {
  name = "/platform/${var.env}/alb-sg-id"
}

data "aws_ssm_parameter" "private_subnet_ids" {
  name = "/platform/${var.env}/private-subnet-ids"
}
