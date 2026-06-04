output "zone_id" {
  description = "Route53 Private Hosted Zone ID for tasks.internal"
  value       = aws_route53_zone.tasks_internal.zone_id
}

output "zone_name" {
  description = "Name of the Private Hosted Zone"
  value       = aws_route53_zone.tasks_internal.name
}
