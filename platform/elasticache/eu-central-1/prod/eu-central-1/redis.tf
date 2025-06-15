resource "aws_elasticache_replication_group" "prod-redis" {
  replication_group_id       = "content-prod-cache-cluster-on"
  description                = "content-prod-cache with cluster mode on"
  node_type                  = "cache.m6g.large"
  port                       = 6379
  parameter_group_name       = "default.redis7.cluster.on"
  automatic_failover_enabled = true
  engine_version             = "7.0"
  multi_az_enabled           = true
  subnet_group_name          = aws_elasticache_subnet_group.content-prod.name
  security_group_ids         = [aws_security_group.content-prod-redis-sg.id]
  num_node_groups            = 1
  replicas_per_node_group    = 1
  apply_immediately          = true

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis-slow-log.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis-engine-log.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }
}

resource "aws_kms_key" "prod-redis-log-kms-key" {
  description             = "For the logs from Prod ElastiCache"
  deletion_window_in_days = 30
}

resource "aws_kms_alias" "prod-redis-log-kms-key-alias" {
  name          = "alias/prod-redis-log-kms-key"
  target_key_id = aws_kms_key.prod-redis-log-kms-key.key_id
}

resource "aws_cloudwatch_log_group" "redis-slow-log" {
  name              = "prod-redis-slow-log"
  retention_in_days = 14
  kms_key_id        = aws_kms_key.prod-redis-log-kms-key.arn
}

resource "aws_cloudwatch_log_group" "redis-engine-log" {
  name              = "prod-redis-engine-log"
  retention_in_days = 14
  kms_key_id        = aws_kms_key.prod-redis-log-kms-key.arn
}

resource "aws_kms_key_policy" "prod-redis-log-kms-key-policy" {
  key_id = aws_kms_key.prod-redis-log-kms-key.id
  policy = jsonencode({
    Id = "prod-redis-log-kms-key-policy"
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
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:log-group:prod-redis-*"
          }
        }
      },
    ]
    Version = "2012-10-17"
  })
}

data "aws_caller_identity" "current" {}

data "aws_vpc" "content-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_subnets" "content-prod-elasticache" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-prod.id]
  }
  filter {
    name   = "tag:Name"
    values = ["content-prod-elasticache-eu-central-1*"]
  }
}

resource "aws_elasticache_subnet_group" "content-prod" {
  name       = "content-prod"
  subnet_ids = data.aws_subnets.content-prod-elasticache.ids
}

resource "aws_security_group" "content-prod-redis-sg" {
  vpc_id      = data.aws_vpc.content-prod.id
  name        = "content-prod-redis-sg"
  description = "The SG for the Elasticache Redis in content-prod vpc."
  tags = {
    Name = "content-prod-redis-sg"
  }
}

resource "aws_security_group_rule" "redis-in" {
  security_group_id = aws_security_group.content-prod-redis-sg.id
  type              = "ingress"
  from_port         = 6379
  to_port           = 6379
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.content-prod.cidr_block]
}

