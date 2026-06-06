variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "tagged_image_count" {
  type        = number
  default     = 10
  description = "Maximum number of tagged images to retain per repository"
}
