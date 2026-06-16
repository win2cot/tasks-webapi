# ---------------------------------------------------------------------------
# SG-ECS — inbound from SG-ALB on :8080 (tasks-webapi) and :8443 (Keycloak)
#           unrestricted outbound
# ---------------------------------------------------------------------------

resource "aws_security_group" "ecs" {
  name        = "tasks-${var.env}-sg-ecs"
  description = "ECS tasks: inbound from ALB on :8080/:8443, unrestricted outbound"
  vpc_id      = var.vpc_id

  ingress {
    description     = "tasks-webapi from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  ingress {
    description     = "Keycloak from ALB"
    from_port       = 8443
    to_port         = 8443
    protocol        = "tcp"
    security_groups = [var.alb_sg_id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "tasks-${var.env}-sg-ecs"
  }
}

# ---------------------------------------------------------------------------
# SG-RDS — inbound from SG-ECS and SG-EICE on :3306, no egress
# ---------------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name        = "tasks-${var.env}-sg-rds"
  description = "RDS: inbound from ECS on :3306 only, no outbound"
  vpc_id      = var.vpc_id

  ingress {
    description     = "MySQL from ECS"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  ingress {
    description     = "MySQL from EICE (DBA tunnel)"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.eice.id]
  }

  tags = {
    Name = "tasks-${var.env}-sg-rds"
  }
}

# ---------------------------------------------------------------------------
# SG-EICE — EC2 Instance Connect Endpoint; egress scoped to RDS :3306 only.
# aws_security_group_rule is used to avoid a circular dependency between
# eice (egress → rds) and rds (ingress ← eice).
# ---------------------------------------------------------------------------

resource "aws_security_group" "eice" {
  name        = "tasks-${var.env}-sg-eice"
  description = "EICE: outbound to RDS :3306 only for DBA tunnel"
  vpc_id      = var.vpc_id

  tags = {
    Name = "tasks-${var.env}-sg-eice"
  }
}

resource "aws_security_group_rule" "eice_to_rds" {
  type                     = "egress"
  description              = "MySQL to RDS"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  security_group_id        = aws_security_group.eice.id
  source_security_group_id = aws_security_group.rds.id
}
