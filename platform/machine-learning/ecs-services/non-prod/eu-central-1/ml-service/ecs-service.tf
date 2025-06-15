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

module "ml-service-dev" {
  source = "../../../../../tf-modules/ecs-service"

  vpc_name                                  = "prod"
  region                                    = "eu-central-1"
  ecs_cluster_name                          = "machine-learning"
  service_name                              = "ml-service-dev"
  service_root_path                         = "api"
  service_secrets                           = [{ "name" = "QUARKUS_DATASOURCE_ML_PASSWORD", "valueFrom" = "arn:aws:ssm:eu-central-1:${data.aws_caller_identity.current.account_id}:parameter/dev-ml-postgresql-rds/password" }]
  lb_names                                  = ["machine-learning-dev"]
  lb_sg_name                                = "machine-learning-dev-load-balancer-sg"
  lb_listener_priority                      = 8
  lb_listener_port                          = { "machine-learning-dev" = 443 }
  service_host_header                       = { "machine-learning-dev" = "dev.frever-ml.com" }
  service_container_image_url               = "304552489232.dkr.ecr.eu-central-1.amazonaws.com/ml-service:dev"
  service_container_port                    = 8080
  service_subnet_ids                        = data.aws_subnets.prod-private.ids
  service_extra_sgs                         = [data.aws_security_group.dev-rds-client-sg-for-machine-learning.id, aws_security_group.ml-rds-client-sg.id]
  service_envs                              = [{ name = "JAVA_OPTS", value = "-Xmx1280m -Xms1280m -Djava.net.preferIPv4Stack=true -Dquarkus.profile=dev -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" }]
  service_cpu_quota                         = 512
  service_memory_quota                      = 2048
  service_health_check_grace_period_seconds = 60
  cpu_architecture                          = "ARM64"
}

data "aws_security_group" "dev-rds-client-sg-for-machine-learning" {
  name   = "dev-rds-client-sg-for-machine-learning"
  vpc_id = data.aws_vpc.prod.id
}

data "aws_caller_identity" "current" {}

data "aws_caller_identity" "frever-account" {
  provider = aws.frever
}

data "aws_sqs_queue" "dev-1-video-conversion-job-creation" {
  provider = aws.frever
  name     = "dev-1-video-conversion-job-creation"
}

data "aws_sqs_queue" "content-test-video-conversion-job-creation" {
  provider = aws.frever
  name     = "content-test-video-conversion-job-creation"
}

data "aws_sqs_queue" "content-stage-video-conversion-job-creation" {
  provider = aws.frever
  name     = "content-stage-video-conversion-job-creation"
}

resource "aws_security_group_rule" "allow-access-comfy-ui" {
  security_group_id = module.ml-service-dev.service-security-group.id
  type              = "egress"
  from_port         = 8188
  to_port           = 8188
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_iam_role_policy" "ml-service-dev-task-execution-role-policy" {
  name = "ml-service-task-execution-role-policy"
  role = module.ml-service-dev.service-iam-role.id

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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/frever-dev-postgresql-rds/*", "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/dev-ml-postgresql-rds/*"]
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
        Resource = [module.ml-service-dev.service-logs.arn, data.aws_cloudwatch_log_group.session-manager-log-group.arn]
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
        Resource = flatten([aws_sqs_queue.ml-service-input-queue.arn, values(aws_sqs_queue.ml-service-comfyui-task-queue)[*].arn])
      },
      {
        Action = [
          "sqs:Get*",
          "sqs:List*",
          "sqs:Send*",
        ]
        Sid      = "SendToMediaConvertQueue"
        Effect   = "Allow"
        Resource = [data.aws_sqs_queue.dev-1-video-conversion-job-creation.arn, data.aws_sqs_queue.content-test-video-conversion-job-creation.arn, data.aws_sqs_queue.content-stage-video-conversion-job-creation.arn]
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
          "arn:aws:s3:::frever-content-stage/*",
          "arn:aws:s3:::frever-content-stage",
          "arn:aws:s3:::frever-content-test/*",
          "arn:aws:s3:::frever-content-test",
          "arn:aws:s3:::frever-dev/*",
          "arn:aws:s3:::frever-dev",
          "arn:aws:s3:::video-source-development/*",
          "arn:aws:s3:::video-source-development",
          "arn:aws:s3:::video-source-dev-2/*",
          "arn:aws:s3:::video-source-dev-2",
          "arn:aws:s3:::video-source-content-test/*",
          "arn:aws:s3:::video-source-content-test",
          "arn:aws:s3:::video-source-content-stage/*",
          "arn:aws:s3:::video-source-content-stage",
          "arn:aws:s3:::video-source-content-dev/*",
          "arn:aws:s3:::video-source-content-dev",
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
          "arn:aws:s3:::frever-comfyui-output-dev/*",
          "arn:aws:s3:::frever-comfyui-output-dev",
        ]
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

data "aws_cloudwatch_log_group" "session-manager-log-group" {
  name = "session-manager"
}

data "aws_kms_key" "session-manager-kms-key" {
  key_id = "alias/session-manager-kms-key"
}

resource "aws_sqs_queue" "ml-service-input-queue" {
  name = "dev-ml-service-input-queue"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 90
}

resource "aws_sqs_queue" "ml-service-input-queue-deadletter" {
  name = "dev-ml-service-input-queue-deadletter"
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
}

resource "aws_sqs_queue" "ml-service-comfyui-task-queue" {
  for_each = toset(local.comfyui-task-types)
  name     = "dev-ml-service-comfyui-${each.key}-task-queue"
  // 14 days
  message_retention_seconds  = 1209600
  visibility_timeout_seconds = 600
}

resource "aws_sqs_queue" "ml-service-comfyui-task-queue-deadletter" {
  for_each = toset(local.comfyui-task-types)
  name     = "dev-ml-service-comfyui-${each.key}-task-queue-deadletter"
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
    maxReceiveCount     = 10
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

resource "aws_db_instance" "postgresql-rds" {
  identifier                   = "dev-ml-service-pg"
  allocated_storage            = 20
  max_allocated_storage        = 100
  db_name                      = "frever"
  engine                       = "postgres"
  engine_version               = "17.2"
  instance_class               = "db.t4g.micro"
  multi_az                     = false
  username                     = "Master"
  kms_key_id                   = data.aws_kms_alias.rds.target_key_arn
  manage_master_user_password  = true
  parameter_group_name         = "frever-postgresql-17-logical-replication"
  skip_final_snapshot          = true
  db_subnet_group_name         = data.aws_db_subnet_group.isolated.name
  vpc_security_group_ids       = [aws_security_group.ml-rds-sg.id]
  backup_retention_period      = "14"
  backup_window                = "03:00-03:30"
  maintenance_window           = "sun:04:00-sun:04:30"
  storage_type                 = "gp3"
  performance_insights_enabled = "true"
  monitoring_interval          = 60
  monitoring_role_arn          = data.aws_iam_role.rds-monitoring-role.arn
  storage_encrypted            = "true"
}

resource "aws_security_group" "ml-rds-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "dev-ml-service-rds-sg"
  description = "The SG for the PostgreSQL RDS, used by ml-service in dev env."
  tags = {
    Name = "dev-ml-service-rds-sg"
  }
}

resource "aws_security_group" "ml-rds-client-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "dev-ml-service-rds-client-sg"
  description = "The RDS client SG for the PostgreSQL RDS, used by ml-service in dev env."
  tags = {
    Name = "dev-ml-service-rds-client-sg"
  }
}

resource "aws_security_group_rule" "rds-postgresql-in-from-client" {
  security_group_id        = aws_security_group.ml-rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ml-rds-client-sg.id
}

resource "aws_security_group_rule" "rds-postgresql-client-out" {
  security_group_id        = aws_security_group.ml-rds-client-sg.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ml-rds-sg.id
}

resource "aws_sqs_queue" "comfyui-message-queue" {
  provider = aws.frever
  // 14 days
  message_retention_seconds = 1209600
  name                      = "comfyui-message-queue-dev"
}

resource "aws_sqs_queue" "comfyui-message-queue-deadletter" {
  provider = aws.frever
  // 14 days
  message_retention_seconds = 1209600
  name                      = "comfyui-message-queue-deadletter-dev"
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
  name = "comfyui-message-dev"
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

