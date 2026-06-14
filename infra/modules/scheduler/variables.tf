variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "stack" {
  type        = string
  description = "Stack prefix used for resource naming: 'tasks' or 'platform'"
}

variable "region" {
  type        = string
  description = "AWS region"
}

variable "account_id" {
  type        = string
  description = "AWS account ID"
}

variable "ecs_service_arns" {
  type        = list(string)
  default     = []
  description = "ECS service ARNs this Lambda is allowed to call UpdateService on"
}

variable "rds_instance_arns" {
  type        = list(string)
  default     = []
  description = "RDS DB instance ARNs this Lambda is allowed to start/stop"
}

variable "schedules" {
  type = list(object({
    name                = string
    schedule_expression = string
    input               = string
  }))
  description = "EventBridge Scheduler schedules. Each entry becomes one aws_scheduler_schedule resource named '<stack>-<env>-<name>'."
}
