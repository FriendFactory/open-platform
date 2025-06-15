variable "vpc_name" {
  type        = string
  description = "The name of the VPC to create Jaeger in"
}

variable "region" {
  type        = string
  description = "The AWS region to create Jaeger in"
  default     = "eu-central-1"
}

variable "jaeger_instance_count" {
  type        = number
  description = "The number of Jaeger EC2 instances, should be either 1 or 3"
  default     = 1

  validation {
    condition     = var.jaeger_instance_count == 1 || var.jaeger_instance_count == 3
    error_message = "The jaeger_instance_count should be either 1 (for non-prod workloads) or 3 (for prod workloads)."
  }
}

variable "jaeger_instance_type" {
  type        = string
  description = "The EC2 instance type to create for Jaeger and Cassandra"
  default     = "t4g.medium"
}

variable "jaeger_instance_ami" {
  type        = string
  description = "The EC2 instance AMI"
  default     = null
}

variable "use_amazon_linux_2023" {
  type        = bool
  description = "Whether or not use Amazon Linux 2023. The old instances are still based on Amazon Linux 2."
  default     = false
}

