variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "RDS master password (DBA use only; tasks-webapi uses IAM auth)"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for the RDS DB subnet group (from /platform/{env}/private-subnet-ids SSM)"
}

variable "sg_id" {
  type        = string
  description = "Security Group ID for RDS (SG-RDS from security_group module)"
}
