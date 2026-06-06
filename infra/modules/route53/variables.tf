variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID to associate the Private Hosted Zone with (from platform SSM output)"
}

variable "rds_endpoint" {
  type        = string
  description = "RDS endpoint hostname to set as CNAME target for db.tasks.internal (from rds module output)"
}
