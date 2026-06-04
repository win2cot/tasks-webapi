variable "account_id" {
  type        = string
  description = "AWS account ID (e.g. 138285070797)"
}

variable "repo" {
  type        = string
  default     = "win2cot/tasks-webapi"
  description = "GitHub repository in owner/repo format"
}

variable "state_bucket" {
  type        = string
  default     = "dgz48-tfstate"
  description = "S3 bucket name for Terraform state"
}

variable "env" {
  type        = string
  default     = "dev"
  description = "Environment name (dev / stg / prd)"
}

variable "region" {
  type        = string
  default     = "ap-northeast-1"
  description = "AWS region"
}
