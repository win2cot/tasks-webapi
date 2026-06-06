variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "vpc_id" {
  type        = string
  description = "VPC ID"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block; used to scope egress to tasks RDS (cross-stack SPI federation)"
}

variable "alb_sg_id" {
  type        = string
  description = "Security Group ID of the shared ALB (SG-ALB); used for Keycloak ingress"
}

variable "keycloak_db_sg_id" {
  type        = string
  description = "Security Group ID for Keycloak dedicated DB (SG-KeycloakDB); used for DB egress"
}

variable "keycloak_db_endpoint" {
  type        = string
  description = "RDS endpoint hostname (address) for Keycloak DB; used to construct JDBC URL"
}

variable "https_listener_arn" {
  type        = string
  description = "ARN of the shared ALB HTTPS listener (:443)"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnet IDs for ECS Service placement"
}

variable "ecs_cluster_arn" {
  type        = string
  description = "ECS cluster ARN to place the Keycloak Service in"
}

variable "image_uri" {
  type        = string
  description = "ECR image URI for the Keycloak Custom Image (e.g. <account>.dkr.ecr.<region>.amazonaws.com/keycloak-custom:<tag>)"
}

variable "hostname" {
  type        = string
  description = "Public hostname for Keycloak (KC_HOSTNAME; e.g. auth-dev.dgz48.xyz)"
}

variable "account_id" {
  type        = string
  description = "AWS account ID; used to construct SSM parameter ARNs"
}

variable "region" {
  type        = string
  description = "AWS region; used to construct SSM parameter ARNs and log configuration"
}

variable "listener_rule_priority" {
  type        = number
  description = "Priority for the ALB listener rule (must be unique per listener; platform range: 10-99)"
  default     = 10
}

variable "desired_count" {
  type        = number
  description = "Desired ECS task count for Keycloak Service"
  default     = 1
}
