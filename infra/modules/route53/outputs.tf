output "zone_id" {
  description = "Route53 Private Hosted Zone ID for tasks.internal"
  value       = aws_route53_zone.tasks_internal.zone_id
}

output "zone_name" {
  description = "Name of the Private Hosted Zone"
  value       = aws_route53_zone.tasks_internal.name
}

output "tasks_regional_cert_arn" {
  description = "ARN of the tasks deep ACM certificate in ap-northeast-1 (*.tasks.<base_domain>)"
  value       = aws_acm_certificate_validation.tasks_regional.certificate_arn
}

output "tasks_cloudfront_cert_arn" {
  description = "ARN of the tasks CloudFront ACM certificate in us-east-1 (tasks-<env>.<base_domain>)"
  value       = aws_acm_certificate_validation.tasks_cloudfront.certificate_arn
}

output "api_fqdn" {
  description = "FQDN of the api alias record"
  value       = aws_route53_record.api.fqdn
}

output "auth_fqdn" {
  description = "FQDN of the auth alias record"
  value       = aws_route53_record.auth.fqdn
}
