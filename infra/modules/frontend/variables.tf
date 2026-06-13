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
