output "sg_id" {
  description = "Security Group ID for Keycloak ECS (SG-Keycloak)"
  value       = aws_security_group.keycloak.id
}

output "exec_role_arn" {
  description = "ECS Task Execution Role ARN"
  value       = aws_iam_role.exec.arn
}

output "task_role_arn" {
  description = "ECS Task Role ARN"
  value       = aws_iam_role.task.arn
}

output "service_name" {
  description = "ECS Service name"
  value       = aws_ecs_service.keycloak.name
}

output "target_group_arn" {
  description = "ALB Target Group ARN for Keycloak"
  value       = aws_lb_target_group.keycloak.arn
}
