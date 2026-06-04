variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID"
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "Public subnet IDs for ALB placement (one per AZ)"
}

variable "base_domain" {
  type        = string
  description = "Base domain for wildcard ACM certificate (e.g. dgz48.xyz)"
}
