# ---------------------------------------------------------------------------
# ECR repositories — tasks-webapi / keycloak-custom (S1Infra-7)
# Both repos are owned by the tasks stack.
# Image scan on push is enabled; results will be integrated with Security Hub
# in Sprint 4 NIST hardening (see infrastructure-plan.md §6.5).
# IAM push/pull policies will be attached in Sprint 2 when the CI OIDC role
# and ECS Task Execution Role are created (S2Infra-4/5).
# ---------------------------------------------------------------------------

locals {
  repos = {
    tasks_webapi    = "tasks-webapi"
    keycloak_custom = "keycloak-custom"
  }

  lifecycle_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Keep only the ${var.tagged_image_count} most recent tagged images"
        selection = {
          tagStatus       = "tagged"
          tagPatternList  = ["*"]
          countType       = "imageCountMoreThan"
          countNumber     = var.tagged_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_ecr_repository" "this" {
  for_each = local.repos

  name                 = each.value
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "tasks-${var.env}-ecr-${each.value}"
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = local.repos

  repository = aws_ecr_repository.this[each.key].name
  policy     = local.lifecycle_policy
}
