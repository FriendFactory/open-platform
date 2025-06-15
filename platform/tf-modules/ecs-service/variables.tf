variable "vpc_name" {
  type        = string
  description = "The name of the VPC to host the ECS service."
}

variable "region" {
  type        = string
  description = "The AWS region the service will run in."
  default     = "eu-central-1"
}

variable "ecs_cluster_name" {
  type        = string
  description = "The ECS cluster name."
}

variable "service_name" {
  type        = string
  description = "The name of the ECS service."
}

variable "service_root_path" {
  type        = string
  description = "The URL root path of the service."
  default     = null
}

variable "target_group_name" {
  type        = string
  description = "The name of the ECS target group. Default to 'service_name'-tg."
  default     = null
}

variable "service_role_name" {
  type        = string
  description = "The name of the ECS service role. Default to 'service_name'-task-execution-role."
  default     = null
}

variable "cloudwatch_log_group_name" {
  type        = string
  description = "The name of the CloudWatch log group name for this ECS service. Default to 'service_name'-service-logs."
  default     = null
}

variable "lb_names" {
  type        = list(string)
  description = "The application load balancer names."
}

variable "lb_listener_port" {
  type        = map(number)
  description = "The application load balancer listener ports."
  default     = {}
}

variable "lb_listener_priority" {
  type        = number
  description = "The application load balancer listener priority, cannot duplicate."
}

variable "lb_sg_name" {
  type        = string
  description = "The security group name of the load balancer that will serve the traffic."
}

variable "service_extra_managed_policies" {
  type        = list(string)
  description = "Extra AWS managed IAM policy arns that will be attached to the service IAM role."
  default     = []
}

variable "service_health_check_path" {
  type        = string
  description = "The URL path of health check for this service."
  default     = "status"
}

variable "service_host_header" {
  type        = map(string)
  description = "The service hostname header, used in listener-rule condition."
}

variable "cloudwatch_log_retention_in_days" {
  type        = number
  description = "The number of days that the service logs will be kept in CloudWatch."
  default     = 7
}

variable "service_container_image_url" {
  type        = string
  description = "The service container image URL, in ECR or public repo."
}

variable "service_cpu_quota" {
  type        = number
  description = "The service CPU quota."
  default     = 256
}

variable "service_memory_quota" {
  type        = number
  description = "The service Memory quota."
  default     = 512
}

variable "service_container_port" {
  type        = number
  description = "The service container traffic serving port."
  default     = 80
}

variable "service_envs" {
  type        = list(object({ name = string, value = string }))
  description = "The service environmental variables."
  default     = null
}

variable "service_secrets" {
  type        = list(object({ name = string, valueFrom = string }))
  description = "The service secrets."
  default     = []
}

variable "service_instance_count" {
  type        = number
  description = "The number of instances to run for this service."
  default     = 1
}

variable "service_deployment_minimum_healthy_percent" {
  type        = number
  description = "deployment_minimum_healthy_percent in aws_ecs_service resource for this service."
  default     = 0
}

variable "service_deployment_maximum_percent" {
  type        = number
  description = "deployment_maximum_percent in aws_ecs_service resource for this service."
  default     = 100
}

variable "service_health_check_grace_period_seconds" {
  type        = number
  description = "health_check_grace_period_seconds in aws_ecs_service resource for this service."
  default     = 45
}

variable "service_subnet_ids" {
  type        = list(string)
  description = "VPC subnet ids that the service will run in."
}

variable "service_extra_sgs" {
  type        = list(string)
  description = "Extra security groups that will be attached to the service."
  default     = []
}

variable "cpu_architecture" {
  type        = string
  description = "The cpu_architecture for the ECS service, could only be 'X86_64' or 'ARM64', default to 'X86_64'."
  default     = "X86_64"
}

