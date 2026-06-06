variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID to associate the Private Hosted Zone with (from platform SSM output)"
}

variable "base_domain" {
  type        = string
  default     = "dgz48.xyz"
  description = "Root public domain (e.g. dgz48.xyz)"
}

variable "alb_https_listener_arn" {
  type        = string
  description = "ARN of the shared ALB HTTPS listener (from platform SSM /platform/<env>/alb-https-listener-arn)"
}

variable "alb_dns_name" {
  type        = string
  description = "DNS name of the shared ALB (from platform SSM /platform/<env>/alb-dns-name)"
}

variable "alb_zone_id" {
  type        = string
  description = "Hosted Zone ID of the shared ALB (from platform SSM /platform/<env>/alb-zone-id)"
}

variable "cloudfront_domain_name" {
  type        = string
  default     = null
  description = "CloudFront distribution domain name for tasks-<env>.<base_domain> alias (S2Infra-3). Set null until CloudFront is provisioned."
}

variable "cloudfront_zone_id" {
  type        = string
  default     = null
  description = "CloudFront hosted zone ID (always Z2FDTNDATAQYW2 for CloudFront). Set null until CloudFront is provisioned."
}
