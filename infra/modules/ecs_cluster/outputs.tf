output "cluster_name" {
  description = "ECS Cluster name"
  value       = aws_ecs_cluster.main.name
}

output "cluster_arn" {
  description = "ECS Cluster ARN"
  value       = aws_ecs_cluster.main.arn
}

output "adot_log_group_name" {
  description = "CloudWatch Log Group name for ADOT Collector sidecar"
  value       = aws_cloudwatch_log_group.adot.name
}

output "webapi_metrics_log_group_name" {
  description = "CloudWatch Log Group name for ADOT awsemf EMF output (metrics)"
  value       = aws_cloudwatch_log_group.webapi_metrics.name
}
