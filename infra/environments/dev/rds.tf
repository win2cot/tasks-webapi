# ---------------------------------------------------------------------------
# RDS MySQL 8.4 (S1Infra-1 / ADR-0007 / ADR-0004)
# tasks-webapi: IAM 認証 / Keycloak SPI read user: パスワード認証 / master: DBA 用
# private subnet IDs は /platform/{env}/private-subnet-ids(SSM, カンマ区切り)参照
# ---------------------------------------------------------------------------

module "rds" {
  source = "../../modules/rds"

  env         = var.env
  db_password = var.db_password
  subnet_ids  = split(",", data.aws_ssm_parameter.private_subnet_ids.value)
  sg_id       = module.security_group.rds_sg_id
}
