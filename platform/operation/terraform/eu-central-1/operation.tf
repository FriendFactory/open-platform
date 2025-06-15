locals {
  dbs = ["main", "video", "auth"]
}

resource "aws_ssm_parameter" "db-url" {
  for_each    = toset(local.dbs)
  name        = "/prod-db-url/${each.key}"
  type        = "SecureString"
  value       = "changeme"
  description = "DB URL for production RDS ${each.key}"
  lifecycle {
    ignore_changes = [value]
  }
}

data "aws_vpc" "content-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_subnets" "content-prod-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-prod.id]
  }
  tags = {
    Name = "content-prod-private-eu-central-1a"
  }
}

# data "aws_ami" "amazon-linux-2" {
#   owners = ["amazon"]
#   filter {
#     name   = "name"
#     values = ["amzn2-ami-kernel-5.10-hvm-2.0.20220606.1-arm64-gp2"]
#   }
#   filter {
#     name   = "architecture"
#     values = ["arm64"]
#   }
#   filter {
#     name   = "root-device-type"
#     values = ["ebs"]
#   }
# }

resource "aws_security_group" "platform" {
  name        = "platform-sg"
  description = "SG for platform"
  vpc_id      = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "platform"
  }
}

data "aws_security_group" "content-prod-ssh-bastion" {
  name   = "content-prod-ssh-bastion"
  vpc_id = data.aws_vpc.content-prod.id
}

data "aws_security_group" "content-prod-db" {
  name   = "content-prod-db"
  vpc_id = data.aws_vpc.content-prod.id
}

data "aws_security_group" "content-prod-cassandra" {
  name   = "content-prod-jaeger"
  vpc_id = data.aws_vpc.content-prod.id
}

resource "aws_security_group_rule" "platform-to-content-prod-db" {
  type              = "egress"
  security_group_id = aws_security_group.platform.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-prod-db.id
}

resource "aws_security_group_rule" "platform-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.platform.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "to-cassandra" {
  type              = "egress"
  security_group_id = aws_security_group.platform.id
  from_port         = 9042
  to_port           = 9042
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-prod-cassandra.id
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-to-platform" {
  type              = "ingress"
  security_group_id = aws_security_group.platform.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-prod-ssh-bastion.id
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "platform-operation" {
  name = "platform-operation"
  role = aws_iam_role.platform-operation.name
}

resource "aws_iam_role" "platform-operation" {
  name = "platform-operation"
  path = "/"

  assume_role_policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Principal": {
               "Service": "ec2.amazonaws.com"
            },
            "Effect": "Allow",
            "Sid": ""
        }
    ]
}
EOF
}

resource "aws_iam_role_policy_attachments_exclusive" "ssm-managed" {
  role_name   = aws_iam_role.platform-operation.name
  policy_arns = [data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn]
}

data "aws_sns_topic" "system-alerts" {
  name = "system-alerts"
}

# https://github.com/FriendFactory/Server/blob/master/deploy/environment/frever-monitoring/cdk/lib/frever-monitoring-stack.ts#L18
data "aws_sns_topic" "send-to-slack-aws-alarms-channel" {
  name = "FreverMonitoringStack-Alarms04B5A0BF-WC0J6XJ59IFB"
}

data "aws_sns_topic" "modulai-alerts" {
  name = "modulai-alerts"
}

resource "aws_iam_role_policy" "run-platform-operation-app" {
  name = "run-platform-operation-app"
  role = aws_iam_role.platform-operation.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid = "allowQueryParameterStore"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters"
        ]
        Effect = "Allow"
        Resource = [
          "arn:aws:ssm:*:*:parameter/prod-db-url/*", "arn:aws:ssm:*:*:parameter/content-prod/secrets/cs-*",
          "arn:aws:ssm:*:*:parameter/content-prod/secrets/twilio-*"
        ]
      },
      {
        Sid = "allowPublicMessagesToSNSTopic"
        Action = [
          "SNS:Publish"
        ]
        Effect   = "Allow"
        Resource = [data.aws_sns_topic.system-alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn, data.aws_sns_topic.modulai-alerts.arn, aws_sns_topic.platform-messages.arn]
      },
      {
        Sid    = "allowStopRdsTaggedWithAuthStop",
        Effect = "Allow",
        Action = [
          "rds:StartDBCluster",
          "rds:StopDBCluster",
          "rds:ListTagsForResource",
          "rds:DescribeDBInstances",
          "rds:StopDBInstance",
          "rds:DescribeDBClusters",
          "rds:StartDBInstance"
        ],
        Resource = "*"
      },
      {
        Sid = "pollPlatformOperationInputQueue"
        Action = [
          "sqs:Get*",
          "sqs:ChangeMessageVisibility",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage"
        ]
        Effect   = "Allow"
        Resource = [aws_sqs_queue.operation-input-queue.arn]
      },
      {
        Sid = "s3BucketAccess"
        Action = [
          "s3:List*",
        ]
        Effect   = "Allow"
        Resource = [data.aws_s3_bucket.frever-content-stage.arn, data.aws_s3_bucket.frever-content.arn, "arn:aws:s3:::jumpstart-cache-prod-eu-central-1"]
      },
      {
        Sid = "s3ObjectsAccess"
        Action = [
          "s3:Get*",
          "s3:Delete*",
        ]
        Effect   = "Allow"
        Resource = ["${data.aws_s3_bucket.frever-content-stage.arn}/*", "${data.aws_s3_bucket.frever-content.arn}/*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1/*"]
      },
      {
        Sid = "SageMakerCheckEndpointStatus"
        Action = [
          "sagemaker:Describe*",
        ]
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

data "aws_s3_bucket" "frever-content" {
  bucket = "frever-content"
}

data "aws_s3_bucket" "frever-content-stage" {
  bucket = "frever-content-stage"
}

resource "aws_instance" "instance" {
  # ami                     = data.aws_ami.amazon-linux-2.id
  ami                     = "ami-0ceb85bb30095410b"
  instance_type           = "t4g.small"
  key_name                = "platform"
  subnet_id               = data.aws_subnets.content-prod-private.ids[0]
  vpc_security_group_ids  = [aws_security_group.platform.id]
  iam_instance_profile    = aws_iam_instance_profile.platform-operation.name
  disable_api_termination = true

  tags = {
    Name  = "platform-operation"
    Owner = "platform"
  }
}

resource "aws_cloudwatch_metric_alarm" "platform-operation-auto-recovery-alarm-system" {
  alarm_name        = "platform-operation EC2 System Status Check Recovery"
  alarm_description = "Recover platform-operation instance on System Status Check failure"

  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  period              = "120"
  evaluation_periods  = "2"
  statistic           = "Maximum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = "0.99"

  dimensions = {
    InstanceId = aws_instance.instance.id
  }

  alarm_actions = ["arn:aws:automate:eu-central-1:ec2:recover"]
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

resource "aws_sqs_queue" "operation-input-queue" {
  name                       = "platform-operation-input-queue-${data.aws_region.current.name}"
  visibility_timeout_seconds = 150
  message_retention_seconds  = 3600 * 24 * 14

  policy = <<POLICY
  {
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Principal": { "AWS": "${data.aws_caller_identity.current.id}", "Service": "s3.amazonaws.com" },
        "Action": "sqs:SendMessage",
        "Resource": "arn:aws:sqs:*:*:platform-operation-input-queue-${data.aws_region.current.name}",
        "Condition": {
          "ArnEquals": { "aws:SourceArn": "arn:aws:s3:::*" }
        }
      }
    ]
  }
  POLICY
}

resource "aws_sns_topic" "platform-messages" {
  name = "platform-messages"
}

resource "aws_sns_topic_policy" "default" {
  arn = aws_sns_topic.platform-messages.arn

  policy = data.aws_iam_policy_document.sns_topic_policy.json
}

data "aws_iam_policy_document" "sns_topic_policy" {
  policy_id = "AllowEventBridgeAndCloudWatchToPublish"

  statement {
    actions = [
      "SNS:Publish",
    ]

    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = sort(["events.amazonaws.com", "cloudwatch.amazonaws.com"])
    }

    resources = [
      aws_sns_topic.platform-messages.arn,
    ]

    sid = "__default_statement_ID"
  }
}

resource "aws_sns_topic_subscription" "sns-topic" {
  for_each  = toset(local.emails)
  topic_arn = aws_sns_topic.platform-messages.arn
  protocol  = "email"
  endpoint  = each.key
}

locals {
  // platform-messages slack channel email address
  emails = ["xie.xiaodong@frever.com", "platform-messages-aaaahkyg3aufkxlgfsa7ylbrzq@ffextendedworkspace.slack.com"]
}
