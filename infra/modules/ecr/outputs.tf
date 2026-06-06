output "tasks_webapi_repository_url" {
  description = "ECR repository URL for tasks-webapi"
  value       = aws_ecr_repository.this["tasks_webapi"].repository_url
}

output "tasks_webapi_repository_arn" {
  description = "ECR repository ARN for tasks-webapi"
  value       = aws_ecr_repository.this["tasks_webapi"].arn
}

output "keycloak_custom_repository_url" {
  description = "ECR repository URL for keycloak-custom"
  value       = aws_ecr_repository.this["keycloak_custom"].repository_url
}

output "keycloak_custom_repository_arn" {
  description = "ECR repository ARN for keycloak-custom"
  value       = aws_ecr_repository.this["keycloak_custom"].arn
}
