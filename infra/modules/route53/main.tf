# ---------------------------------------------------------------------------
# Private Hosted Zone — tasks.internal (ADR-0001 α案: env 毎独立 PHZ)
# Owned by tasks stack, associated with the shared platform VPC (ADR-0004).
# CNAME db.tasks.internal → RDS endpoint (var.rds_endpoint) wired in S1Infra-1.
# ---------------------------------------------------------------------------

resource "aws_route53_zone" "tasks_internal" {
  name    = "tasks.internal"
  comment = "Private hosted zone for ${var.env} env internal services"

  vpc {
    vpc_id = var.vpc_id
  }

  tags = {
    Name = "tasks-${var.env}-phz"
  }
}

resource "aws_route53_record" "db" {
  zone_id = aws_route53_zone.tasks_internal.zone_id
  name    = "db.tasks.internal"
  type    = "CNAME"
  ttl     = 300
  records = [var.rds_endpoint]
}
