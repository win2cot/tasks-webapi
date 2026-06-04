output "alb_arn" {
  description = "ALB ARN"
  value       = aws_lb.main.arn
}

output "https_listener_arn" {
  description = "HTTPS listener ARN"
  value       = aws_lb_listener.https.arn
}

output "sg_id" {
  description = "Security Group ID for ALB (SG-ALB)"
  value       = aws_security_group.alb.id
}

output "dns_name" {
  description = "ALB DNS name (for Route53 alias records)"
  value       = aws_lb.main.dns_name
}

output "zone_id" {
  description = "ALB canonical hosted zone ID (for Route53 alias records)"
  value       = aws_lb.main.zone_id
}

output "base_cert_arn" {
  description = "ARN of the validated base wildcard ACM certificate"
  value       = aws_acm_certificate_validation.base.certificate_arn
}
