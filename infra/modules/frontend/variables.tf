variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "base_domain" {
  type        = string
  default     = "dgz48.xyz"
  description = "Root public domain (e.g. dgz48.xyz)"
}

variable "acm_certificate_arn" {
  type        = string
  description = "ARN of ACM certificate in us-east-1 for CloudFront (from route53 module output tasks_cloudfront_cert_arn)"
}

variable "hsts_max_age_sec" {
  type        = number
  description = "max-age for Strict-Transport-Security. dev: 300 (short-term), prd: 31536000 (1 year)"
  default     = 300
}
