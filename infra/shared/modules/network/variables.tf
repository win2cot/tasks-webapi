variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

variable "region" {
  type        = string
  description = "AWS region"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "VPC CIDR block"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
  description = "CIDR blocks for public subnets (must match availability_zones count)"
}

variable "private_subnet_cidrs" {
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
  description = "CIDR blocks for private subnets (must match availability_zones count)"
}

variable "availability_zones" {
  type        = list(string)
  description = "Availability zones for subnets (must match subnet CIDR counts)"
}
