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

data "aws_iam_policy" "machine-learning-sagemaker-s3-read-write" {
  name = "machine-learning-sagemaker-s3-read-write"
}

data "aws_iam_policy" "allow-access-s3-bucket-in-frever-aws-account" {
  name = "allow-access-s3-bucket-in-frever-aws-account"
}

module "ml-service" {
  source = "../../../../../tf-modules/ecs-service"

  vpc_name                                   = "prod"
  region                                     = "eu-central-1"
  ecs_cluster_name                           = "machine-learning"
  service_name                               = "ml-service"
  service_root_path                          = "api"
  lb_names                                   = ["machine-learning-prod", "machine-learning-prod-public"]
  lb_listener_port                           = { "machine-learning-prod-public" = 443 }
  lb_sg_name                                 = "machine-learning-prod-load-balancer-sg"
  lb_listener_priority                       = 8
  service_extra_managed_policies             = [data.aws_iam_policy.machine-learning-sagemaker-s3-read-write.arn, data.aws_iam_policy.allow-access-s3-bucket-in-frever-aws-account.arn]
  service_host_header                        = { "machine-learning-prod" = "frever-ml-internal.com", "machine-learning-prod-public" = "frever-ml.com" }
  service_container_image_url                = "304552489232.dkr.ecr.eu-central-1.amazonaws.com/ml-service:prod"
  service_container_port                     = 8080
  service_subnet_ids                         = data.aws_subnets.prod-private.ids
  service_extra_sgs                          = [data.aws_security_group.content-prod-db-replica-client.id]
  service_envs                               = [{ name = "JAVA_OPTS", value = "-Xmx1536m -Xms1536m -Djava.net.preferIPv4Stack=true -Dquarkus.profile=prod -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" }]
  service_instance_count                     = 3
  service_deployment_minimum_healthy_percent = 50
  service_deployment_maximum_percent         = 150
  service_cpu_quota                          = 1024
  service_memory_quota                       = 2048
  service_health_check_grace_period_seconds  = 60
  cpu_architecture                           = "ARM64"
}

data "aws_security_group" "content-prod-db-replica-client" {
  name   = "content-prod-db-replica-client"
  vpc_id = data.aws_vpc.prod.id
}

data "aws_caller_identity" "current" {}

data "aws_caller_identity" "frever-account" {
  provider = aws.frever
}

data "aws_sqs_queue" "prod-video-conversion-job-creation" {
  provider = aws.frever
  name     = "prod-video-conversion-job-creation"
}

resource "aws_security_group_rule" "allow-access-comfy-ui" {
  security_group_id = module.ml-service.service-security-group.id
  type              = "egress"
  from_port         = 8188
  to_port           = 8188
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_iam_role_policy" "ml-service-task-execution-role-policy" {
  name = "ml-service-task-execution-role-policy"
  role = module.ml-service.service-iam-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/postgresql-rds/*"]
      },
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
        Resource = [module.ml-service.service-logs.arn, data.aws_cloudwatch_log_group.session-manager-log-group.arn]
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
    ]
  })
}

data "aws_cloudwatch_log_group" "session-manager-log-group" {
  name = "session-manager"
}

data "aws_kms_key" "session-manager-kms-key" {
  key_id = "alias/session-manager-kms-key"
}

resource "aws_sqs_queue" "ml-service-input-queue" {
  name = "prod-ml-service-input-queue"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 90
}

resource "aws_sqs_queue" "ml-service-input-queue-deadletter" {
  name = "prod-ml-service-input-queue-deadletter"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 90
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue",
    sourceQueueArns   = [aws_sqs_queue.ml-service-input-queue.arn]
  })
}

resource "aws_sqs_queue_redrive_policy" "ml-service-input-queue-redrive" {
  queue_url = aws_sqs_queue.ml-service-input-queue.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ml-service-input-queue-deadletter.arn
    maxReceiveCount     = 100
  })
}

data "aws_iam_policy_document" "sqs-policy-document" {
  statement {
    sid    = "AllowFreverMainAwsAccountSendMessage"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["722913253728"]
    }

    actions   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:ChangeMessageVisibility", "sqs:DeleteMessage"]
    resources = [aws_sqs_queue.ml-service-input-queue.arn]
  }
}

resource "aws_sqs_queue_policy" "sqs-policy" {
  queue_url = aws_sqs_queue.ml-service-input-queue.id
  policy    = data.aws_iam_policy_document.sqs-policy-document.json
}

locals {
  comfyui-task-types = ["lip-sync", "pulid", "makeup"]
  comfyui-task-instance-count = [
    {
      ami-prefix : "ComfyUi-Dev-20250415"
      instance-type : "g6e.xlarge",
      min-size : 1,
      max-size : 3
    },
    {
      ami-prefix : "ComfyUi-Photo-Dev-20250415"
      instance-type : "g6e.xlarge",
      min-size : 1,
      max-size : 3
    },
    {
      ami-prefix : "ComfyUi-Photo-Dev-2-20250415"
      instance-type : "g6e.xlarge",
      min-size : 1,
      max-size : 3
    }
  ]
  comfyui-task-type-to-instance-count = zipmap(local.comfyui-task-types, local.comfyui-task-instance-count)
}

resource "aws_sqs_queue" "ml-service-comfyui-task-queue" {
  for_each = toset(local.comfyui-task-types)
  name     = "ixia-prod-ml-service-comfyui-${each.key}-task-queue"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 600
}

resource "aws_sqs_queue" "ml-service-comfyui-task-queue-deadletter" {
  for_each = toset(local.comfyui-task-types)
  name     = "ixia-prod-ml-service-comfyui-${each.key}-task-queue-deadletter"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 600
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue",
    sourceQueueArns   = [aws_sqs_queue.ml-service-comfyui-task-queue[each.key].arn]
  })
}

resource "aws_sqs_queue_redrive_policy" "ml-service-comfyui-task-queue-redrive" {
  for_each  = toset(local.comfyui-task-types)
  queue_url = aws_sqs_queue.ml-service-comfyui-task-queue[each.key].id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ml-service-comfyui-task-queue-deadletter[each.key].arn
    maxReceiveCount     = 20
  })
}

data "aws_iam_role" "rds-monitoring-role" {
  name = "rds-monitoring-role"
}

data "aws_db_subnet_group" "isolated" {
  name = "prod-isolated"
}

data "aws_kms_alias" "rds" {
  name = "alias/aws/rds"
}

module "ml-service-ixia-prod" {
  source = "../../../../../tf-modules/ecs-service"

  vpc_name                                   = "prod"
  region                                     = "eu-central-1"
  ecs_cluster_name                           = "machine-learning"
  service_name                               = "ml-service-ixia-prod"
  service_root_path                          = "api"
  service_secrets                            = [{ "name" = "QUARKUS_DATASOURCE_ML_PASSWORD", "valueFrom" = "arn:aws:ssm:eu-central-1:${data.aws_caller_identity.current.account_id}:parameter/ixia-prod-postgresql-rds/password" }]
  lb_names                                   = ["machine-learning-ixia-prod"]
  lb_sg_name                                 = "machine-learning-ixia-prod-lb-sg"
  lb_listener_priority                       = 1
  service_extra_managed_policies             = [data.aws_iam_policy.machine-learning-sagemaker-s3-read-write.arn, data.aws_iam_policy.allow-access-s3-bucket-in-frever-aws-account.arn]
  service_host_header                        = { "machine-learning-ixia-prod" = "ixia-prod.frever-ml-internal.com" }
  service_container_image_url                = "304552489232.dkr.ecr.eu-central-1.amazonaws.com/ml-service:ixia-prod"
  service_container_port                     = 8080
  service_subnet_ids                         = data.aws_subnets.prod-private.ids
  service_extra_sgs                          = [data.aws_security_group.ixia-prod-rds-client-sg-for-machine-learning.id, aws_security_group.ixia-prod-ml-rds-client-sg.id]
  service_envs                               = [{ name = "JAVA_OPTS", value = "-Xmx1280m -Xms1280m -Djava.net.preferIPv4Stack=true -Dquarkus.profile=prod -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" }]
  service_instance_count                     = 3
  service_deployment_minimum_healthy_percent = 50
  service_deployment_maximum_percent         = 150
  service_cpu_quota                          = 1024
  service_memory_quota                       = 2048
  service_health_check_grace_period_seconds  = 60
  cpu_architecture                           = "ARM64"
}

data "aws_security_group" "ixia-prod-rds-client-sg-for-machine-learning" {
  name   = "ixia-prod-rds-client-sg-for-machine-learning"
  vpc_id = data.aws_vpc.prod.id
}

resource "aws_security_group_rule" "ml-ixia-prod-allow-access-comfy-ui" {
  security_group_id = module.ml-service-ixia-prod.service-security-group.id
  type              = "egress"
  from_port         = 8188
  to_port           = 8188
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_iam_role_policy" "ml-service-ixia-prod-task-execution-role-policy" {
  name = "ml-service-ixia-prod-task-execution-role-policy"
  role = module.ml-service-ixia-prod.service-iam-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/postgresql-rds/*", "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/ixia-prod-postgresql-rds/*"]
      },
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
        Resource = [module.ml-service.service-logs.arn, data.aws_cloudwatch_log_group.session-manager-log-group.arn]
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
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:DeleteMessageBatch",
          "sqs:SetQueueAttributes",
          "sqs:ChangeMessageVisibility",
          "sqs:PurgeQueue",
          "sqs:*MessageMoveTask",
          "sqs:Get*",
          "sqs:List*",
          "sqs:Send*",
        ]
        Sid      = "HandleMessageFromSQS"
        Effect   = "Allow"
        Resource = concat([aws_sqs_queue.ml-service-input-queue.arn, aws_sqs_queue.auto-scaling-event-queue.arn], values(aws_sqs_queue.ml-service-comfyui-task-queue)[*].arn)
      },
      {
        Action = [
          "sqs:Get*",
          "sqs:List*",
          "sqs:Send*",
        ]
        Sid      = "SendToMediaConvertQueue"
        Effect   = "Allow"
        Resource = [data.aws_sqs_queue.prod-video-conversion-job-creation.arn]
      },
      {
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:Get*Object*",
          "s3:PutObject",
          "s3:PutObjectAcl"
        ]
        Sid    = "S3PermissionsForContents"
        Effect = "Allow"
        Resource = [
          "arn:aws:s3:::frever-content/*",
          "arn:aws:s3:::frever-content",
          "arn:aws:s3:::video-source-content-production/*",
          "arn:aws:s3:::video-source-content-production",
          "arn:aws:s3:::frever-dev/*",
          "arn:aws:s3:::frever-dev",
        ]
      },
      {
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:Get*Object*",
          "s3:Put*Object*"
        ]
        Sid    = "S3PermissionsForDestination"
        Effect = "Allow"
        Resource = [
          "arn:aws:s3:::ff-publicfiles/*",
          "arn:aws:s3:::ff-publicfiles",
          "arn:aws:s3:::frever-comfyui-output-ixia-prod/*",
          "arn:aws:s3:::frever-comfyui-output-ixia-prod",
        ]
      },
      {
        Sid    = "PermissionsForAutoScaling"
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "autoscaling:DescribeAutoScalingInstances",
          "autoscaling:DescribeScalingProcessTypes",
          "autoscaling:DescribeLaunchConfigurations",
          "autoscaling:DescribeScalingActivities",
          "autoscaling:DescribeInstanceRefreshes",
          "autoscaling:ExitStandby",
          "autoscaling:EnterStandby",
          "autoscaling:ExecutePolicy",
          "autoscaling:PutScheduledUpdateGroupAction",
          "autoscaling:PutScalingPolicy",
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:DescribeWarmPool",
          "autoscaling:DescribeScheduledActions",
          "autoscaling:UpdateAutoScalingGroup",
          "autoscaling:SetInstanceHealth",
          "autoscaling:GetPredictiveScalingForecast",
          "autoscaling:PutNotificationConfiguration",
          "autoscaling:ResumeProcesses",
          "autoscaling:SetDesiredCapacity",
          "autoscaling:RollbackInstanceRefresh",
          "autoscaling:SuspendProcesses",
          "autoscaling:StartInstanceRefresh",
          "autoscaling:PutWarmPool",
          "autoscaling:CompleteLifecycleAction",
          "autoscaling:CancelInstanceRefresh",
        ],
        Resource = "*"
      },
      {
        Action   = ["sns:Publish*"]
        Sid      = "SnsPermissions"
        Effect   = "Allow"
        Resource = aws_sns_topic.comfyui-message.arn
      },
    ]
  })
}

resource "aws_db_instance" "postgresql-rds-ixia-prod" {
  identifier                   = "ixia-prod-ml-service-pg"
  allocated_storage            = 20
  max_allocated_storage        = 100
  db_name                      = "frever"
  engine                       = "postgres"
  engine_version               = "17.4"
  instance_class               = "db.t4g.small"
  multi_az                     = true
  username                     = "Master"
  kms_key_id                   = data.aws_kms_alias.rds.target_key_arn
  manage_master_user_password  = true
  parameter_group_name         = "frever-postgresql-17-logical-replication"
  skip_final_snapshot          = true
  db_subnet_group_name         = data.aws_db_subnet_group.isolated.name
  vpc_security_group_ids       = [aws_security_group.ixia-prod-ml-rds-sg.id]
  backup_retention_period      = "14"
  backup_window                = "07:00-07:30"
  maintenance_window           = "sun:08:00-sun:08:30"
  storage_type                 = "gp3"
  performance_insights_enabled = "true"
  monitoring_interval          = 60
  monitoring_role_arn          = data.aws_iam_role.rds-monitoring-role.arn
  storage_encrypted            = "true"
}

resource "aws_security_group" "ixia-prod-ml-rds-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "ixia-prod-ml-service-rds-sg"
  description = "The SG for the PostgreSQL RDS, used by ml-service in ixia-prod env."
  tags = {
    Name = "ixia-prod-ml-service-rds-sg"
  }
}

resource "aws_security_group" "ixia-prod-ml-rds-client-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "ixia-prod-ml-service-rds-client-sg"
  description = "The RDS client SG for the PostgreSQL RDS, used by ml-service in ixia-prod env."
  tags = {
    Name = "ixia-prod-ml-service-rds-client-sg"
  }
}

resource "aws_security_group_rule" "ixia-prod-rds-postgresql-in-from-client" {
  security_group_id        = aws_security_group.ixia-prod-ml-rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ixia-prod-ml-rds-client-sg.id
}

resource "aws_security_group_rule" "ixia-prod-rds-postgresql-client-out" {
  security_group_id        = aws_security_group.ixia-prod-ml-rds-client-sg.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ixia-prod-ml-rds-sg.id
}

data "aws_ami" "comfyui-task-instance-ami" {
  for_each    = toset(local.comfyui-task-types)
  most_recent = true
  owners      = ["self"]

  filter {
    name   = "name"
    values = ["${local.comfyui-task-type-to-instance-count[each.key].ami-prefix}*"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_iam_role" "comfyui-ixia-prod-role" {
  name = "comfyui-ixia-prod-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Sid    = "Ec2Assume"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachments_exclusive" "allow-access-s3-bucket-in-frever-aws-account" {
  role_name   = aws_iam_role.comfyui-ixia-prod-role.name
  policy_arns = [data.aws_iam_policy.allow-access-s3-bucket-in-frever-aws-account.arn]
}

resource "aws_iam_instance_profile" "comfyui-ixia-prod" {
  name = "comfyui-ixia-prod"
  role = aws_iam_role.comfyui-ixia-prod-role.name
}

resource "aws_iam_role_policy" "comfyui-ixia-prod-policy" {
  name = "comfyui-ixia-prod-policy"
  role = aws_iam_role.comfyui-ixia-prod-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/comfyui-ixia-prod/*", ]
      },
      {
        Action = [
          "s3:*"
        ]
        Sid    = "S3Permissions"
        Effect = "Allow"
        Resource = [
          "arn:aws:s3:::frever-comfyui-output-ixia-prod/*",
          "arn:aws:s3:::frever-comfyui-output-ixia-prod"
        ]
      },
    ]
  })
}

data "aws_security_group" "instance-connect-endpoint-sg" {
  vpc_id = data.aws_vpc.prod.id
  name   = "instance-connect-endpoint-sg"
}

resource "aws_security_group" "comfyui-ixia-prod-sg" {
  vpc_id = data.aws_vpc.prod.id
  name   = "comfyui-ixia-prod-sg"
  tags = {
    Name = "comfyui-ixia-prod-sg"
  }
}

resource "aws_security_group_rule" "comfyui-ixia-prod-sg-https-out" {
  security_group_id = aws_security_group.comfyui-ixia-prod-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "comfyui-ixia-prod-sg-http-out-within-vpcs" {
  security_group_id = aws_security_group.comfyui-ixia-prod-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_security_group_rule" "comfyui-ixia-prod-sg-ec2-instance-connect-endpoint-in" {
  security_group_id        = aws_security_group.comfyui-ixia-prod-sg.id
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.instance-connect-endpoint-sg.id
}

resource "aws_security_group_rule" "comfyui-ixia-prod-sg-comfy-ui-in-for-machine-learning-eu-prod-vpc" {
  security_group_id = aws_security_group.comfyui-ixia-prod-sg.id
  type              = "ingress"
  from_port         = 8188
  to_port           = 8188
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_launch_template" "comfyui-task-instance-launch-template" {
  for_each = toset(local.comfyui-task-types)
  name          = "comfyui-${each.key}-task-instance-launch-template"
  image_id      = data.aws_ami.comfyui-task-instance-ami[each.key].id
  instance_type = local.comfyui-task-type-to-instance-count[each.key].instance-type
  key_name      = "platform-key"
  iam_instance_profile {
    name = aws_iam_instance_profile.comfyui-ixia-prod.name
  }
  vpc_security_group_ids = [aws_security_group.comfyui-ixia-prod-sg.id]

  tag_specifications {
    resource_type = "instance"

    tags = {
      Name = "comfyui-${each.key}-task-instance"
    }
  }
}

data "aws_subnets" "comfyui-instance-exist" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.prod.id]
  }
  filter {
    name   = "tag:Name"
    values = ["prod-private-*1a", "prod-private-*1c"]
  }
}

resource "aws_autoscaling_group" "comfyui-task-instance-autoscaling-group" {
  name                = "comfyui-${each.key}-task-instance-autoscaling-group"
  for_each            = toset(local.comfyui-task-types)
  vpc_zone_identifier = data.aws_subnets.comfyui-instance-exist.ids
  desired_capacity    = local.comfyui-task-type-to-instance-count[each.key].min-size
  max_size            = local.comfyui-task-type-to-instance-count[each.key].max-size
  min_size            = local.comfyui-task-type-to-instance-count[each.key].min-size

  launch_template {
    id      = aws_launch_template.comfyui-task-instance-launch-template[each.key].id
    version = aws_launch_template.comfyui-task-instance-launch-template[each.key].latest_version
  }

  warm_pool {
    pool_state                  = "Stopped"
    min_size                    = 0
    max_group_prepared_capacity = local.comfyui-task-type-to-instance-count[each.key].max-size

    instance_reuse_policy {
      reuse_on_scale_in = true
    }
  }
}

resource "aws_autoscaling_notification" "comfyui-task-instance-autoscaling-notifications" {
  group_names = values(aws_autoscaling_group.comfyui-task-instance-autoscaling-group)[*].name

  notifications = [
    "autoscaling:EC2_INSTANCE_LAUNCH",
    "autoscaling:EC2_INSTANCE_LAUNCH_ERROR",
    "autoscaling:EC2_INSTANCE_TERMINATE",
    "autoscaling:EC2_INSTANCE_TERMINATE_ERROR",
    "autoscaling:TEST_NOTIFICATION",
  ]

  topic_arn = aws_sns_topic.comfyui-task-instance-autoscaling-topic.arn
}

resource "aws_sns_topic" "comfyui-task-instance-autoscaling-topic" {
  name = "comfyui-task-instance-autoscaling-topic"
}

resource "aws_sns_topic_subscription" "comfyui-task-instance-autoscaling-topic-sqs-target" {
  topic_arn = aws_sns_topic.comfyui-task-instance-autoscaling-topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.auto-scaling-event-queue.arn
}

data "aws_iam_policy_document" "auto-scaling-event-sqs-policy-document" {
  statement {
    sid    = "AllowAutoScalingTopicSendMessageToEventQueue"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    actions = ["sqs:SendMessage", "sqs:ChangeMessageVisibility"]

    resources = [aws_sqs_queue.auto-scaling-event-queue.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"

      values = [
        aws_sns_topic.comfyui-task-instance-autoscaling-topic.arn,
      ]
    }
  }
}

resource "aws_sqs_queue" "auto-scaling-event-queue" {
  name = "ixia-prod-auto-scaling-event-queue"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 60
}

resource "aws_sqs_queue_policy" "auto-scaling-event-queue-sqs-policy" {
  queue_url = aws_sqs_queue.auto-scaling-event-queue.id
  policy    = data.aws_iam_policy_document.auto-scaling-event-sqs-policy-document.json
}

resource "aws_sqs_queue" "comfyui-message-queue" {
  provider = aws.frever
  // 14 days
  message_retention_seconds = 1209600
  name                      = "comfyui-message-queue"
}

resource "aws_sqs_queue" "comfyui-message-queue-deadletter" {
  provider = aws.frever
  // 14 days
  message_retention_seconds = 1209600
  name                      = "comfyui-message-queue-deadletter"
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue",
    sourceQueueArns   = [aws_sqs_queue.comfyui-message-queue.arn]
  })
}

resource "aws_sqs_queue_redrive_policy" "comfyui-message-queue-redrive" {
  provider  = aws.frever
  queue_url = aws_sqs_queue.comfyui-message-queue.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.comfyui-message-queue-deadletter.arn
    maxReceiveCount     = 10
  })
}

data "aws_iam_policy_document" "comfyui-message-queue-policy-document" {
  statement {
    sid    = "AllowSns"
    effect = "Allow"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.comfyui-message-queue.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.comfyui-message.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "comfyui-message-queue-policy" {
  provider  = aws.frever
  queue_url = aws_sqs_queue.comfyui-message-queue.id
  policy    = data.aws_iam_policy_document.comfyui-message-queue-policy-document.json
}

data "aws_iam_policy_document" "comfyui-message-topic-policy-document" {
  statement {
    actions = [
      "SNS:Subscribe",
      "SNS:SetTopicAttributes",
      "SNS:RemovePermission",
      "SNS:Publish",
      "SNS:ListSubscriptionsByTopic",
      "SNS:GetTopicAttributes",
      "SNS:DeleteTopic",
      "SNS:AddPermission",
    ]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceOwner"

      values = [data.aws_caller_identity.current.account_id]
    }

    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    resources = [aws_sns_topic.comfyui-message.arn]

    sid = "SnsPermissions"
  }

  statement {
    actions = [
      "SNS:Subscribe",
      "SNS:Receive",
    ]

    condition {
      test     = "StringLike"
      variable = "SNS:Endpoint"

      values = [aws_sqs_queue.comfyui-message-queue.arn]
    }

    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    resources = [aws_sns_topic.comfyui-message.arn]
    sid       = "AllowSubscriptionFromFreverAccount"
  }
}

resource "aws_sns_topic" "comfyui-message" {
  name = "comfyui-message"
}

resource "aws_sns_topic_policy" "comfyui-message-topic-policy" {
  arn    = aws_sns_topic.comfyui-message.arn
  policy = data.aws_iam_policy_document.comfyui-message-topic-policy-document.json
}

resource "aws_sns_topic_subscription" "comfyui-message-queue-subscription" {
  depends_on           = [aws_sns_topic_policy.comfyui-message-topic-policy]
  provider             = aws.frever
  topic_arn            = aws_sns_topic.comfyui-message.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.comfyui-message-queue.arn
  raw_message_delivery = true
}

