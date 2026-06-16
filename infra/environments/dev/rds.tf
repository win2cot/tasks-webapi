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
