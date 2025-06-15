data "aws_ecr_repository" "timers" {
  name = "timers"
}

module "timers" {
  depends_on                                 = [module.test-ecs-cluster]
  source                                     = "../../../tf-modules/ecs-service"
  vpc_name                                   = "content-test"
  region                                     = "eu-central-1"
  ecs_cluster_name                           = "test"
  service_name                               = "timers"
  lb_names                                   = [module.test-ecs-cluster.ecs-alb.name]
  lb_sg_name                                 = module.test-ecs-cluster.ecs-alb-sg.name
  lb_listener_priority                       = 10
  service_host_header                        = { (module.test-ecs-cluster.ecs-alb.name) = module.test-ecs-cluster.ecs-alb-root-url }
  service_health_check_path                  = "hello"
  service_container_port                     = 8080
  service_container_image_url                = "${data.aws_ecr_repository.timers.repository_url}:test"
  service_subnet_ids                         = module.test-ecs-cluster.ecs-subnet-ids
  service_envs                               = [{ name = "JAVA_OPTS", value = "-Xmx1536m -Xms1536m -Djava.net.preferIPv4Stack=true -Dquarkus.profile=test -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" }]
  service_cpu_quota                          = 512
  service_memory_quota                       = 2048
  service_health_check_grace_period_seconds  = 60
  service_deployment_minimum_healthy_percent = 0
  service_deployment_maximum_percent         = 100
  cpu_architecture                           = "ARM64"
  target_group_name                          = "test-timers-tg"
  service_role_name                          = "test-timers-task-execution-role"
  cloudwatch_log_group_name                  = "test-timers-service-logs"
}

data "aws_caller_identity" "current" {}

resource "aws_security_group_rule" "timers-postgresql-out" {
  security_group_id = module.timers.service-security-group.id
  type              = "egress"
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  // test db is in another VPC and publicly accessible 
  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_iam_role_policy" "timers-task-execution-role-policy" {
  name = "test-timers-task-execution-role-policy"
  role = module.timers.service-iam-role.id

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
        Resource = [module.timers.service-logs.arn, module.test-ecs-cluster.session_manager_cloudwatch_log_group.arn]
      },
      {
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey",
        ]
        Sid      = "UseKmsKey"
        Effect   = "Allow"
        Resource = module.test-ecs-cluster.session-manager-kms-key.arn
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/timers/test/*"]
      },
      {
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:SetQueueAttributes",
          "sqs:ChangeMessageVisibility",
          "sqs:PurgeQueue",
          "sqs:*MessageMoveTask",
          "sqs:Get*",
          "sqs:List*",
          "sqs:Send*",
        ]
        Sid      = "ReceiveMessageFromSQS"
        Effect   = "Allow"
        Resource = [aws_sqs_queue.timers-input-queue.arn]
      },
    ]
  })
}

resource "aws_sqs_queue" "timers-input-queue" {
  // 14 days
  message_retention_seconds = 1209600
  name                      = "test-timers-input-queue"
}

resource "aws_sqs_queue" "timers-input-queue-deadletter" {
  // 14 days
  message_retention_seconds = 1209600
  name                      = "test-timers-input-queue-deadletter"
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue",
    sourceQueueArns   = [aws_sqs_queue.timers-input-queue.arn]
  })
}

resource "aws_sqs_queue_redrive_policy" "timers-input-queue-redrive" {
  queue_url = aws_sqs_queue.timers-input-queue.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.timers-input-queue-deadletter.arn
    maxReceiveCount     = 10
  })
}

data "aws_iam_policy_document" "sqs-policy-document" {
  statement {
    sid    = "AllowSns"
    effect = "Allow"

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.timers-input-queue.arn]

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.video-template-mapping.arn, aws_sns_topic.group-changed.arn, aws_sns_topic.template-updated.arn, aws_sns_topic.group-deleted.arn, aws_sns_topic.group-unfollowed.arn, aws_sns_topic.video-unliked.arn, aws_sns_topic.group-followed.arn, aws_sns_topic.outfit-changed.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "sqs-policy" {
  queue_url = aws_sqs_queue.timers-input-queue.id
  policy    = data.aws_iam_policy_document.sqs-policy-document.json
}

resource "aws_sns_topic" "video-template-mapping" {
  name = "test-video-template-mapping"
}

resource "aws_sns_topic_subscription" "video-template-mapping-sqs-target" {
  topic_arn            = aws_sns_topic.video-template-mapping.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "group-changed" {
  name = "test-group-changed"
}

resource "aws_sns_topic_subscription" "group-changed-sqs-target" {
  topic_arn            = aws_sns_topic.group-changed.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "template-updated" {
  name = "test-template-updated"
}

resource "aws_sns_topic_subscription" "template-updated-sqs-target" {
  topic_arn            = aws_sns_topic.template-updated.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "group-deleted" {
  name = "test-group-deleted"
}

resource "aws_sns_topic_subscription" "group-deleted-sqs-target" {
  topic_arn            = aws_sns_topic.group-deleted.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "group-unfollowed" {
  name = "test-group-unfollowed"
}

resource "aws_sns_topic_subscription" "group-unfollowed-sqs-target" {
  topic_arn            = aws_sns_topic.group-unfollowed.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "group-followed" {
  name = "test-group-followed"
}

resource "aws_sns_topic_subscription" "group-followed-sqs-target" {
  topic_arn            = aws_sns_topic.group-followed.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "video-unliked" {
  name = "test-video-unliked"
}

resource "aws_sns_topic_subscription" "video-unliked-sqs-target" {
  topic_arn            = aws_sns_topic.video-unliked.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

resource "aws_sns_topic" "outfit-changed" {
  name = "test-outfit-changed"
}

resource "aws_sns_topic_subscription" "outfit-changed-sqs-target" {
  topic_arn            = aws_sns_topic.outfit-changed.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.timers-input-queue.arn
  raw_message_delivery = true
}

