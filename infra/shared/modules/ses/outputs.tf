output "config_set_name" {
  description = "SES Configuration Set name (publish to SSM /platform/<env>/ses-config-set)"
  value       = aws_sesv2_configuration_set.main.configuration_set_name
}

output "identity_arn" {
  description = "SES Email Identity ARN for mail.<base_domain>"
  value       = aws_sesv2_email_identity.mail.arn
}
