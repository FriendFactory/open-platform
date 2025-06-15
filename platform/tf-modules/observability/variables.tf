variable "vpc_name" {
  type        = string
  description = "The name of the VPC to create instance to host observability services in"
}

variable "eks_cluster_name" {
  type        = string
  description = "The name of the EKS cluster, for looking up the security group."
  default     = ""
}

variable "region" {
  type        = string
  description = "The AWS region to create observability in"
  default     = "eu-central-1"
}

variable "observability_instance_count" {
  type        = number
  description = "The number of observability EC2 instances, should be either 1 or 3"
  default     = 1

  validation {
    condition     = var.observability_instance_count == 1 || var.observability_instance_count == 3
    error_message = "The observability_instance_count should be either 1 (for non-prod workloads) or 3 (for prod workloads)."
  }
}

variable "observability_instance_type" {
  type        = string
  description = "The EC2 instance type to create for observability services"
  default     = "t4g.small"
}

variable "observability_instance_ami" {
  type        = string
  description = "The EC2 instance AMI"
  default     = null
}

variable "enable_cloudwatch_metrics_in_grafana" {
  type        = bool
  description = "Whether or not grant permissions to the EC2 instance, so that the instance could pull AWS cloudwatch metrics in Grafana."
  default     = false
}

variable "use_amazon_linux_2023" {
  type        = bool
  description = "Whether or not use Amazon Linux 2023. The old instances are still based on Amazon Linux 2."
  default     = false
}

variable "long-loki-s3-bucket-name" {
  type        = bool
  description = "Whether or not append company name to the loki s3 bucket, for deduplication."
  default     = false
}

