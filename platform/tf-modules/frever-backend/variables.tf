variable "vpc_name" {
  type        = string
  description = "The name of the VPC."
}

variable "cidr_block" {
  type        = string
  description = "The cidr block of the VPC."
}

variable "region" {
  type        = string
  description = "The AWS region."
  default     = "eu-central-1"
}

variable "use_nat_instance" {
  type        = bool
  description = "Whether to use NAT instances."
  default     = false
}

variable "nat_count" {
  type        = number
  description = "the number of nat_gateway instances or the number of nat ec2 instances"
  default     = "1"
}

variable "require_rds" {
  type        = bool
  description = "Whether to create an RDS instance. Could be used if we need to create the RDS later."
  default     = true
}

variable "rds_allocated_storage" {
  type        = number
  description = "How many GBs of disk space allocated to the RDS instance."
  default     = 20
}

variable "rds_max_allocated_storage" {
  type        = number
  description = "Max GBs of disk space allocated to the RDS instance from storage auto expansion."
  default     = 100
}

variable "rds_instance_class" {
  type        = string
  description = "The instance class of the RDS instance."
  default     = "db.t4g.small"
}

variable "rds_multi_az" {
  type        = bool
  description = "Whether the RDS instance is multi_az."
  default     = false
}

variable "rds_engine_version" {
  type        = string
  description = "The engine version of the RDS instance."
  default     = "15.5"
}

variable "rds_replicate_source_db" {
  type        = string
  description = "The source DB to replicate."
  default     = null
}

variable "rds_needs_logical_replication" {
  type        = bool
  description = "Whether the RDS instance needs to support logical replication."
  default     = false
}

variable "rds_kms_key_id" {
  type        = string
  description = "The KMS key Id used to encrypt the RDS storage."
  default     = null
}

variable "redis_replicas" {
  type        = number
  description = "The number of replicas for the Redis instance."
  default     = 0
}

variable "redis_parameter_group_name" {
  type        = string
  description = "Redis parameter_group_name"
  default     = "frever-redis-7"
}

variable "redis_log_retention_in_days" {
  type        = number
  description = "Redis CloudWatch logs retention days."
  default     = 14
}

variable "redis_node_type" {
  type        = string
  description = "The node type of the Redis instance."
  default     = "cache.t4g.small"
}

variable "eks_node_group_desired_size" {
  type        = number
  description = "EKS node-group desired_size."
  default     = 6
}

variable "eks_node_group_max_size" {
  type        = number
  description = "EKS node-group max_size."
  default     = 9
}

variable "eks_node_group_min_size" {
  type        = number
  description = "EKS node-group min_size."
  default     = 3
}

variable "eks_node_group_instance_types" {
  type        = list(string)
  description = "The node-group instance types."
  default     = ["t3.large"]
}

variable "bastion_instance_ami" {
  type        = string
  description = "The AMI for the bastion ec2 instance."
  default     = ""
}

