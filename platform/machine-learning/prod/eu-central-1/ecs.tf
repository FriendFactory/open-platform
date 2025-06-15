data "aws_cloudwatch_log_group" "session-manager-log-group" {
  name = "session-manager"
}

data "aws_kms_key" "session-manager-kms-key" {
  key_id = "alias/session-manager-kms-key"
}

resource "aws_ecs_cluster" "machine-learning" {
  name = "machine-learning"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  configuration {
    execute_command_configuration {
      kms_key_id = data.aws_kms_key.session-manager-kms-key.arn
      logging    = "OVERRIDE"

      log_configuration {
        cloud_watch_encryption_enabled = true
        cloud_watch_log_group_name     = data.aws_cloudwatch_log_group.session-manager-log-group.name
      }
    }
  }
  tags = {
    "AWS.SSM.AppManager.ECS.Cluster.ARN" = "arn:aws:ecs:eu-central-1:${data.aws_caller_identity.current.account_id}:cluster/machine-learning"
  }
}

resource "aws_ecs_cluster_capacity_providers" "fargate-spot" {
  cluster_name = aws_ecs_cluster.machine-learning.name

  capacity_providers = ["FARGATE_SPOT"]

  default_capacity_provider_strategy {
    base              = 1
    weight            = 100
    capacity_provider = "FARGATE_SPOT"
  }
}

