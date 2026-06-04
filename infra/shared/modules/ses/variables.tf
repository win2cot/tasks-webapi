variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "base_domain" {
  type        = string
  description = "Base domain (e.g. dgz48.xyz). SES identity = mail.<base_domain>, MAIL FROM = bounce.mail.<base_domain>"
}

variable "region" {
  type        = string
  description = "AWS region — used for MAIL FROM MX record (e.g. ap-northeast-1)"
}
