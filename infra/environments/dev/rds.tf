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

# ---------------------------------------------------------------------------
# EC2 Instance Connect Endpoint — DBA 用 MySQL トンネル (dev のみ)
#
# ローカルから RDS への接続手順:
#   aws ec2-instance-connect open-tunnel \
#     --instance-connect-endpoint-id $(terraform output -raw eice_id) \
#     --remote-port 3306 --local-port 13306
#   mysql -h 127.0.0.1 -P 13306 -u admin -p
#
# 必要 IAM 権限: ec2-instance-connect:OpenTunnel on このリソース ARN
# ---------------------------------------------------------------------------

resource "aws_ec2_instance_connect_endpoint" "main" {
  subnet_id          = element(split(",", data.aws_ssm_parameter.private_subnet_ids.value), 0)
  security_group_ids = [module.security_group.eice_sg_id]
  preserve_client_ip = false

  tags = {
    Name = "tasks-${var.env}-eice"
  }
}

output "eice_id" {
  description = "EC2 Instance Connect Endpoint ID (use for open-tunnel)"
  value       = aws_ec2_instance_connect_endpoint.main.id
}

# ---------------------------------------------------------------------------
# Keycloak SPI federation(ADR-0006 / #862)— platform の Keycloak が app users DB を
# read federation するための配線。
#   - spi-jdbc-url: RDS エンドポイントから組み立てた JDBC URL を SSM 公開。KC タスク定義は
#     valueFrom で ARN 参照し、ECS がコンテナ起動時に解決する(platform→tasks の apply 順に非依存)。
#   - RDS SG への Keycloak(VPC 内)からの :3306 ingress は security_group module の
#     rds_ingress_cidr_blocks(VPC CIDR)で許可する(security_group.tf)。RDS SG は他 rule も
#     インライン管理のため、インライン ingress として追加する(aws_security_group_rule 混在を避ける)。
#   - keycloak_spi_read DB ユーザー(パスワード認証・SELECT のみ)は master 権限が必要なため
#     EICE トンネル経由で手動作成する(本ファイル冒頭の手順、infrastructure-plan §手動)。
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "spi_jdbc_url" {
  name  = "/tasks/${var.env}/db/spi-jdbc-url"
  type  = "String"
  value = "jdbc:mysql://${module.rds.db_instance_address}:3306/tasks?allowPublicKeyRetrieval=true&useSSL=false"

  tags = {
    Name = "tasks-${var.env}-db-spi-jdbc-url"
  }
}
