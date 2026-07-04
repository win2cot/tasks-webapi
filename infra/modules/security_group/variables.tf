variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID to associate the security groups with"
}

variable "alb_sg_id" {
  type        = string
  description = "Security Group ID of the shared ALB (SG-ALB); sourced from SSM /platform/{env}/alb-sg-id"
}

variable "rds_ingress_cidr_blocks" {
  type        = list(string)
  default     = []
  description = "Extra CIDR blocks allowed inbound to RDS on :3306 (Keycloak SPI federation cross-stack; ADR-0006). Empty = none."
}
