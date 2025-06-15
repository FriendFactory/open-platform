resource "aws_kms_key" "session-manager-kms-key" {
  description             = "For the loggings from Session Manager in VPC ${var.vpc_name}"
  deletion_window_in_days = 7
}

resource "aws_kms_alias" "session-manager-kms-key-alias" {
  name          = "alias/${var.vpc_name}-session-manager-kms-key"
  target_key_id = aws_kms_key.session-manager-kms-key.key_id
}

resource "aws_cloudwatch_log_group" "session-manager-log-group" {
  depends_on        = [aws_kms_key_policy.session-manager-kms-key-policy]
  name              = "${var.vpc_name}-session-manager"
  retention_in_days = 7
  kms_key_id        = aws_kms_key.session-manager-kms-key.arn
}

resource "aws_kms_key_policy" "session-manager-kms-key-policy" {
  key_id = aws_kms_key.session-manager-kms-key.id
  policy = jsonencode({
    Id = "${var.vpc_name}-session-manager-kms-key-policy"
    Statement = [
      {
        Action = "kms:*"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }

        Resource = "*"
        Sid      = "allow KMS operations from current account"
      },
      {
        Action = [
          "kms:Encrypt*",
          "kms:Decrypt*",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:Describe*"
        ]
        Effect = "Allow"
        Principal = {
          Service = "logs.eu-central-1.amazonaws.com"
        }
        Resource = "*"
        Sid      = "allow KMS operations from aws-log service"
        Condition = {
          ArnEquals = {
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:log-group:${var.vpc_name}-session-manager"
          }
        }
      },
    ]
    Version = "2012-10-17"
  })
}

data "aws_caller_identity" "current" {}

resource "aws_ecs_cluster" "ecs-cluster" {
  name = var.ecs_cluster_name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  configuration {
    execute_command_configuration {
      kms_key_id = aws_kms_key.session-manager-kms-key.arn
      logging    = "OVERRIDE"

      log_configuration {
        cloud_watch_encryption_enabled = true
        cloud_watch_log_group_name     = aws_cloudwatch_log_group.session-manager-log-group.name
      }
    }
  }
  tags = {
    "AWS.SSM.AppManager.ECS.Cluster.ARN" = "arn:aws:ecs:eu-central-1:${data.aws_caller_identity.current.account_id}:cluster/${var.ecs_cluster_name}"
  }
}

resource "aws_ecs_cluster_capacity_providers" "fargate-spot" {
  cluster_name = aws_ecs_cluster.ecs-cluster.name

  capacity_providers = ["FARGATE_SPOT"]

  default_capacity_provider_strategy {
    base              = 1
    weight            = 100
    capacity_provider = "FARGATE_SPOT"
  }
}

output "session_manager_cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.session-manager-log-group
}

output "session-manager-kms-key" {
  value = aws_kms_key.session-manager-kms-key
}
