# ---------------------------------------------------------------------------
# RDS MySQL 8.4 — tasks stack 専用 (S1Infra-1 / ADR-0007 / ADR-0004)
# IAM 認証有効化: tasks-webapi は AWSAuthenticationPlugin で接続。
# Keycloak SPI read user と master(DBA 用)はパスワード認証(Parameter Store)。
# private subnet は /platform/{env}/private-subnet-ids(SSM)から渡される。
# ---------------------------------------------------------------------------

resource "aws_db_subnet_group" "main" {
  name        = "tasks-${var.env}-subnet-group"
  subnet_ids  = var.subnet_ids
  description = "Private subnet group for tasks ${var.env} RDS"

  tags = {
    Name = "tasks-${var.env}-subnet-group"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "tasks-${var.env}-mysql"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = "db.t4g.micro"

  # Storage — gp3 / 20 GiB base / 100 GiB autoscale ceiling
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  # Initial database and master credentials (DBA use only)
  db_name  = "tasks"
  username = "admin"
  password = var.db_password

  # Network
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.sg_id]
  multi_az               = false
  publicly_accessible    = false

  # IAM authentication for tasks-webapi (ADR-0006 / §3.6)
  iam_database_authentication_enabled = true

  # Backup / maintenance
  backup_retention_period = 7
  apply_immediately       = true

  # Dev-only: no final snapshot, no deletion protection
  skip_final_snapshot = true
  deletion_protection = false

  lifecycle {
    ignore_changes = [password]
  }

  tags = {
    Name = "tasks-${var.env}-mysql"
  }
}
