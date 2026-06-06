# ---------------------------------------------------------------------------
# SG-KeycloakDB — inbound from VPC CIDR on :3306, no egress
# Ingress from VPC CIDR until #322 creates SG-Keycloak; private subnet only.
# ---------------------------------------------------------------------------

resource "aws_security_group" "keycloak_db" {
  name        = "platform-${var.env}-sg-keycloak-db"
  description = "Keycloak DB: inbound from VPC on :3306, no outbound"
  vpc_id      = var.vpc_id

  ingress {
    description = "MySQL from VPC (Keycloak ECS)"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = {
    Name = "platform-${var.env}-sg-keycloak-db"
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
# RDS MySQL 8.0 — db.t4g.micro, single-AZ, private subnets
# Always-on: Keycloak requires persistent DB (no stop schedule unlike tasks RDS)
# Engine: RDS MySQL for operational consistency; cheaper than Aurora Serverless v2 for always-on
# ---------------------------------------------------------------------------

resource "aws_db_instance" "keycloak" {
  identifier        = "platform-${var.env}-keycloak-db"
  engine            = "mysql"
  engine_version    = "8.0"
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
