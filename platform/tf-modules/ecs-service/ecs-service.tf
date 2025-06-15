data "aws_vpc" "vpc" {
  tags = {
    Name = var.vpc_name
  }
}

data "aws_lb" "lb" {
  for_each = toset(var.lb_names)
  name     = each.key
}

data "aws_lb_listener" "lb-listener" {
  for_each          = toset(var.lb_names)
  load_balancer_arn = data.aws_lb.lb[each.key].arn
  port              = can(var.lb_listener_port[each.key]) ? var.lb_listener_port[each.key] : 80
}

data "aws_security_group" "lb-sg" {
  vpc_id = data.aws_vpc.vpc.id
  name   = var.lb_sg_name
}

resource "aws_security_group" "service-sg" {
  name        = "${var.vpc_name}-${var.service_name}-sg"
  description = "ECS service ${var.service_name} sg."
  vpc_id      = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-${var.service_name}-sg"
  }
}

resource "aws_security_group_rule" "from-alb-http" {
  security_group_id        = aws_security_group.service-sg.id
  type                     = "ingress"
  from_port                = 80
  to_port                  = 80
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.lb-sg.id
}

resource "aws_security_group_rule" "from-alb-8080" {
  security_group_id        = aws_security_group.service-sg.id
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.lb-sg.id
}

resource "aws_security_group_rule" "service-sg-https-out" {
  security_group_id = aws_security_group.service-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "service-sg-http-out" {
  security_group_id = aws_security_group.service-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_lb_target_group" "service-tg" {
  for_each             = toset(var.lb_names)
  name                 = var.target_group_name != null ? var.target_group_name : can(regex("public", each.key)) ? "${var.service_name}-public-tg" : "${var.service_name}-tg"
  port                 = 80
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = data.aws_vpc.vpc.id
  deregistration_delay = 20
  health_check {
    healthy_threshold   = 3
    unhealthy_threshold = 2
    port                = "traffic-port"
    path                = "/${var.service_root_path != null ? var.service_root_path : replace(var.service_name, "/-dev/", "")}/${var.service_health_check_path}"
    protocol            = "HTTP"
    timeout             = 5
    interval            = 10
    matcher             = "200"
  }
}

resource "aws_lb_listener_rule" "service-listener-rule" {
  for_each     = toset(var.lb_names)
  listener_arn = data.aws_lb_listener.lb-listener[each.key].arn
  priority     = var.lb_listener_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service-tg[each.key].arn
  }

  condition {
    path_pattern {
      values = ["/${var.service_root_path != null ? var.service_root_path : replace(var.service_name, "/-dev/", "")}/*"]
    }
  }

  condition {
    host_header {
      values = [var.service_host_header[each.key], "localhost.localdomain"]
    }
  }
}

data "aws_iam_policy" "AmazonECSTaskExecutionRolePolicy" {
  name = "AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "service-task-execution-role" {
  name = var.service_role_name != null ? var.service_role_name : "${var.service_name}-task-execution-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "EcsAssume"
        Principal = {
          Service = "ecs.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "EcsTasksAssume"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachments_exclusive" "managed-policy" {
  role_name   = aws_iam_role.service-task-execution-role.name
  policy_arns = concat([data.aws_iam_policy.AmazonECSTaskExecutionRolePolicy.arn], var.service_extra_managed_policies)
}

resource "aws_cloudwatch_log_group" "service-logs" {
  name              = var.cloudwatch_log_group_name != null ? var.cloudwatch_log_group_name : "${var.service_name}-service-logs"
  retention_in_days = var.cloudwatch_log_retention_in_days
}

resource "aws_ecs_task_definition" "service-task-definition" {
  family                   = var.service_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.service_cpu_quota
  memory                   = var.service_memory_quota
  task_role_arn            = aws_iam_role.service-task-execution-role.arn
  execution_role_arn       = aws_iam_role.service-task-execution-role.arn
  container_definitions = jsonencode([
    {
      name      = var.service_name
      image     = var.service_container_image_url
      cpu       = var.service_cpu_quota
      memory    = var.service_memory_quota
      essential = true
      portMappings = [
        {
          protocol      = "tcp"
          containerPort = var.service_container_port
        }
      ]
      linuxParameters = {
        initProcessEnabled = true
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-region        = var.region
          awslogs-group         = aws_cloudwatch_log_group.service-logs.name
          awslogs-stream-prefix = var.service_name
        }
      }
      environment = var.service_envs
      secrets     = var.service_secrets
    }
  ])

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

}

data "aws_ecs_cluster" "ecs-cluster" {
  cluster_name = var.ecs_cluster_name
}

resource "aws_ecs_service" "ecs-service" {
  name                               = var.service_name
  cluster                            = data.aws_ecs_cluster.ecs-cluster.id
  task_definition                    = aws_ecs_task_definition.service-task-definition.arn
  desired_count                      = var.service_instance_count
  launch_type                        = "FARGATE"
  deployment_minimum_healthy_percent = var.service_deployment_minimum_healthy_percent
  deployment_maximum_percent         = var.service_deployment_maximum_percent
  health_check_grace_period_seconds  = var.service_health_check_grace_period_seconds
  enable_execute_command             = true

  dynamic "load_balancer" {
    for_each = toset(var.lb_names)
    content {
      target_group_arn = aws_lb_target_group.service-tg[load_balancer.key].arn
      container_name   = var.service_name
      container_port   = var.service_container_port
    }
  }

  network_configuration {
    subnets         = var.service_subnet_ids
    security_groups = concat([aws_security_group.service-sg.id], var.service_extra_sgs)
  }
}

output "service-iam-role" {
  description = "The IAM role for the service."
  value       = aws_iam_role.service-task-execution-role
}

output "service-security-group" {
  description = "The security group created for the service."
  value       = aws_security_group.service-sg
}

output "service-logs" {
  description = "The CloudWatch log group created for the service."
  value       = aws_cloudwatch_log_group.service-logs
}

