# ---------------------------------------------------------------------------
# SG-KeycloakDB — no inline ingress rules; ingress is wired from
# platform/dev/main.tf via aws_security_group_rule (SG-Keycloak → :3306)
# to break the cross-module circular dependency (#322).
# ---------------------------------------------------------------------------

resource "aws_security_group" "keycloak_db" {
  # name_prefix: create_before_destroy と組み合わせることで新旧 SG が同時存在できる。
  # GroupName は VPC 内でユニークでなければならないため固定 name ではなく name_prefix を使用。
  name_prefix = "platform-${var.env}-sg-keycloak-db-"
  description = "Keycloak DB: inbound from SG-Keycloak on :3306 (see main.tf sg rule), no outbound"
  vpc_id      = var.vpc_id

  tags = {
    Name = "platform-${var.env}-sg-keycloak-db"
  }

  # create_before_destroy: SG 差し替え時に「新SG作成 → RDS 切替 → 旧SG削除」の順を保証する。
  # デフォルト(destroy-first)では旧SG削除時に RDS ENI がまだ旧SGを参照しており削除失敗する。
  lifecycle {
    create_before_destroy = true
  }
}

# ---------------------------------------------------------------------------
# DB Subnet Group — private subnets
# ---------------------------------------------------------------------------

resource "aws_db_subnet_group" "keycloak" {
  name        = "platform-${var.env}-keycloak-db"
  description = "Subnet group for Keycloak dedicated DB"
  subnet_ids  = var.private_subnet_ids

  tags = {
    Name = "platform-${var.env}-keycloak-db-subnet-group"
  }
}

# ---------------------------------------------------------------------------
# RDS MySQL 8.4 — db.t4g.micro, single-AZ, private subnets
# Always-on: Keycloak requires persistent DB (no stop schedule unlike tasks RDS)
# Engine: RDS MySQL 8.4 per ADR-0007 (Testcontainers 互換 + 運用一貫性)
# ---------------------------------------------------------------------------

resource "aws_db_instance" "keycloak" {
  identifier        = "platform-${var.env}-keycloak-db"
  engine            = "mysql"
  engine_version    = "8.4"
  instance_class    = "db.t4g.micro"
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "keycloak"
  username = "keycloak_admin"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.keycloak.name
  vpc_security_group_ids = [aws_security_group.keycloak_db.id]
  publicly_accessible    = false

  multi_az            = false
  skip_final_snapshot = true
  apply_immediately   = true

  backup_retention_period = 7
  backup_window           = "19:00-20:00"
  maintenance_window      = "Mon:20:00-Mon:21:00"

  auto_minor_version_upgrade = true
  deletion_protection        = false

  lifecycle {
    ignore_changes = [password]
  }

  tags = {
    Name = "platform-${var.env}-keycloak-db"
  }
}

# ---------------------------------------------------------------------------
# SSM: store DB password as SecureString (placeholder; set in console after apply)
# ---------------------------------------------------------------------------

resource "aws_ssm_parameter" "keycloak_db_password" {
  name  = "/platform/${var.env}/keycloak-db-password"
  type  = "SecureString"
  value = var.db_password

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "platform-${var.env}-keycloak-db-password"
  }
}
