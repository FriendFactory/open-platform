variable "ecs_cluster_name" {
  type        = string
  description = "The ECS cluster name."
}

variable "aws_route53_zone_name" {
  type        = string
  description = "The aws_route53_zone name for the LB."
  default     = "frever-internal.com"
}

variable "aws_route53_zone_private" {
  type        = bool
  description = "The aws_route53_zone is a private zone or not."
  default     = true
}

variable "vpc_name" {
  type        = string
  description = "The name of the VPC to host the ECS service."
}

variable "ecs_subnet_name_prefix" {
  type        = string
  description = "The subnet name prefix that the ECS services run in."
}

variable "lb_subnet_name_prefix" {
  type        = string
  description = "The subnet name prefix that the load balancer runs in."
}

variable "lb_name" {
  type        = string
  description = "The load balancer name for the ECS services."
}

variable "lb_idle_timeout" {
  type        = number
  description = "The load balancer idle_timeout attribute."
  default     = 60
}
