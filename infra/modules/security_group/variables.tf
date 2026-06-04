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
