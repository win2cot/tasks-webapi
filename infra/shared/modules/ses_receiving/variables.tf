variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "base_domain" {
  type        = string
  description = "Base domain (e.g. dgz48.xyz). 受信サブドメイン = e2e.<base_domain>"
}

variable "region" {
  type        = string
  description = "AWS region — SES email receiving endpoint に使用(例: ap-northeast-1)"
}
