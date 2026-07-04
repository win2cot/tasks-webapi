# ---------------------------------------------------------------------------
# Shared data sources — platform SSM outputs (ADR-0004)
# Centralised here so all feature .tf files in this root module can reference
# these without re-declaring the data resource.
# ---------------------------------------------------------------------------

data "aws_caller_identity" "current" {}

data "aws_ssm_parameter" "vpc_id" {
  name = "/platform/${var.env}/vpc-id"
}

# VPC CIDR — Keycloak(platform)からの SPI federation ingress(RDS :3306)許可元に使う。
# cross-stack のため SG 参照不可 → KC egress と対称に VPC CIDR で許可(ADR-0006 / #862、規約 R2 例外)。
data "aws_ssm_parameter" "vpc_cidr" {
  name = "/platform/${var.env}/vpc-cidr"
}

data "aws_ssm_parameter" "alb_sg_id" {
  name = "/platform/${var.env}/alb-sg-id"
}

data "aws_ssm_parameter" "private_subnet_ids" {
  name = "/platform/${var.env}/private-subnet-ids"
}

data "aws_ssm_parameter" "alb_https_listener_arn" {
  name = "/platform/${var.env}/alb-https-listener-arn"
}

data "aws_ssm_parameter" "alb_dns_name" {
  name = "/platform/${var.env}/alb-dns-name"
}

data "aws_ssm_parameter" "alb_zone_id" {
  name = "/platform/${var.env}/alb-zone-id"
}
