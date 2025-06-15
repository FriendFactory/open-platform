variable "vpc_name" {
  type        = string
  description = "The name of the VPC to create instance to host Modulai stuff in"
}

variable "region" {
  type        = string
  description = "The AWS region to create observability in"
  default     = "eu-central-1"
}

variable "modulai_instance_type" {
  type        = string
  description = "The EC2 instance type to create for Modulai stuff"
  default     = "t3.nano"
}

variable "modulai_instance_eips" {
  type        = list(string)
  description = "The EC2 instance Elastic IPs list"
  default     = []
}

variable "create_sagemaker_domain" {
  type        = bool
  default     = false
  description = "Whether or not create SageMaker domain. The account-level service limit 'Total domains' is 1 Domains, with current utilization of 1 Domains and a request delta of 1 Domains. Only 1 Domains is supported."
}

variable "s3_bucket_cross_env_access" {
  type        = list(string)
  description = "Access S3 setup for another environment"
  default     = []
}

variable "ami_type" {
  type        = string
  description = "EC2 AMI type, amazon linux 2 or ubuntu 22 for now"
  default     = "amli2"

  validation {
    condition     = contains(["amli2", "ubuntu22"], var.ami_type)
    error_message = "Must be either amli2 or ubuntu22"
  }
}

