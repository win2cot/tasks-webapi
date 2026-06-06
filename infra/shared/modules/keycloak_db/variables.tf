variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID to associate the security group with"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block; used to scope SG-KeycloakDB ingress to VPC only"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for the DB subnet group"
}

variable "db_password" {
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
  description = "RDS master password placeholder (set actual value in SSM console after initial apply)"
}
