output "webapi_log_group_name" {
  description = "CloudWatch Log Group name for tasks-webapi application logs"
  value       = aws_cloudwatch_log_group.webapi.name
}

output "audit_log_group_name" {
  description = "CloudWatch Log Group name for security audit trail logs"
  value       = aws_cloudwatch_log_group.audit.name
}
