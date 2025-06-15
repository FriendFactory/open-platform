variable "region" {
  type        = string
  description = "The AWS region to operate"
  default     = "eu-central-1"
}

variable "vpc_name" {
  type        = string
  description = "The name of the VPC to create instance to host Modulai stuff in"
  default     = "content-prod"
}

