output "oidc_provider_arn" {
  description = "ARN of the GitHub Actions OIDC provider"
  value       = aws_iam_openid_connect_provider.github_actions.arn
}

output "platform_plan_role_arn" {
  description = "ARN of the platform-dev-plan IAM role"
  value       = aws_iam_role.platform_plan.arn
}

output "platform_apply_role_arn" {
  description = "ARN of the platform-dev-apply IAM role"
  value       = aws_iam_role.platform_apply.arn
}

output "tasks_plan_role_arn" {
  description = "ARN of the tasks-dev-plan IAM role"
  value       = aws_iam_role.tasks_plan.arn
}

output "tasks_apply_role_arn" {
  description = "ARN of the tasks-dev-apply IAM role"
  value       = aws_iam_role.tasks_apply.arn
}

output "release_build_role_arn" {
  description = "ARN of the release-build IAM role (used by release-build.yml workflow)"
  value       = aws_iam_role.release_build.arn
}

output "tasks_deploy_role_arn" {
  description = "ARN of the tasks-<env>-deploy IAM role (used by deploy.yml: verify / deploy-webapi / deploy-web)"
  value       = aws_iam_role.tasks_deploy.arn
}

output "platform_deploy_role_arn" {
  description = "ARN of the platform-<env>-deploy IAM role (used by deploy.yml: deploy-keycloak)"
  value       = aws_iam_role.platform_deploy.arn
}

output "dev_smoke_role_arn" {
  description = "ARN of the platform-<env>-smoke IAM role (used by dev-smoke.yml: post-deploy dev-smoke E2E, S3 mail read)"
  value       = aws_iam_role.dev_smoke.arn
}
