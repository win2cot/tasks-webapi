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

output "webapi_tg_arn" {
  description = "ARN of the tasks-webapi target group (port 8080)"
  value       = aws_lb_target_group.webapi.arn
}

output "keycloak_tg_arn" {
  description = "ARN of the Keycloak target group (port 8443)"
  value       = aws_lb_target_group.keycloak.arn
}
