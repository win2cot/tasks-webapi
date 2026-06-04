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
