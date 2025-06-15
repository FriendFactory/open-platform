data "aws_vpc" "prod" {
  tags = {
    Name = "prod"
  }
}

data "aws_subnets" "prod-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.prod.id]
  }
  filter {
    name   = "tag:Name"
    values = ["prod-private-*"]
  }
}

data "aws_security_group" "content-prod-db-replica-client" {
  name   = "content-prod-db-replica-client"
  vpc_id = data.aws_vpc.prod.id
}

data "aws_iam_policy" "allow-access-s3-content-bucket-in-frever-aws-account" {
  name = "allow-access-s3-content-bucket-in-frever-aws-account"
}

data "aws_iam_policy" "machine-learning-sagemaker-s3-read-write" {
  name = "machine-learning-sagemaker-s3-read-write"
}

module "video-gen-dev" {
  source = "../../../../../tf-modules/ecs-service"

  vpc_name                                  = "prod"
  region                                    = "eu-central-1"
  ecs_cluster_name                          = "machine-learning"
  service_name                              = "video-gen-dev"
  lb_names                                  = ["machine-learning-dev"]
  lb_sg_name                                = "machine-learning-dev-load-balancer-sg"
  lb_listener_priority                      = 40
  lb_listener_port                          = { "machine-learning-dev" = 443 }
  service_host_header                       = { "machine-learning-dev" = "dev.frever-ml.com" }
  service_container_image_url               = "304552489232.dkr.ecr.eu-central-1.amazonaws.com/video-gen:dev"
  service_subnet_ids                        = data.aws_subnets.prod-private.ids
  service_extra_sgs                         = [data.aws_security_group.content-prod-db-replica-client.id]
  service_extra_managed_policies            = [data.aws_iam_policy.allow-access-s3-content-bucket-in-frever-aws-account.arn, data.aws_iam_policy.machine-learning-sagemaker-s3-read-write.arn]
  service_health_check_grace_period_seconds = 120
}

data "aws_caller_identity" "current" {}

data "aws_cloudwatch_log_group" "session-manager-log-group" {
  name = "session-manager"
}

data "aws_kms_key" "session-manager-kms-key" {
  key_id = "alias/session-manager-kms-key"
}

resource "aws_iam_role_policy" "video-gen-dev-task-execution-role-policy" {
  name = "video-gen-dev-task-execution-role-policy"
  role = module.video-gen-dev.service-iam-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "ssmmessages:CreateControlChannel",
          "ssmmessages:CreateDataChannel",
          "ssmmessages:OpenControlChannel",
          "ssmmessages:OpenDataChannel"
        ]
        Sid      = "EcsExec"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "logs:DescribeLogGroups"
        ]
        Sid      = "DescribeLogGroups"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "logs:CreateLogStream",
          "logs:DescribeLogStreams",
          "logs:PutLogEvents"
        ]
        Sid      = "UseLogGroups"
        Effect   = "Allow"
        Resource = [module.video-gen-dev.service-logs.arn, data.aws_cloudwatch_log_group.session-manager-log-group.arn]
      },
      {
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
        ]
        Sid      = "UseKmsKey"
        Effect   = "Allow"
        Resource = data.aws_kms_key.session-manager-kms-key.arn
      },
      {
        Action   = "ssm:DescribeParameters"
        Sid      = "DescribeParameters"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "ssm:GetParameterHistory",
          "ssm:GetParametersByPath",
          "ssm:GetParameters",
          "ssm:GetParameter",
        ]
        Sid      = "UseSsmParameters"
        Effect   = "Allow"
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/msk/*", "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/postgresql-rds/*"]
      },
    ]
  })
}

