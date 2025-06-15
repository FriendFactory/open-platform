locals {
  db-credential-placeholder = {
    username = "username"
    password = "password"
  }
  kafka-version = "2.8.1"
}

resource "aws_kms_key" "kms" {
  description = "Used by MSK"
}

resource "aws_kms_alias" "msk-kms-alias" {
  name          = "alias/msk-storage-key"
  target_key_id = aws_kms_key.kms.key_id
}

# resource "aws_cloudwatch_log_group" "msk-broker-logs" {
#   name              = "msk-broker-logs"
#   retention_in_days = 14
# }

# resource "aws_cloudwatch_log_group" "msk-debezium-main-connector-logs" {
#   name              = "msk-debezium-main-connecter-logs"
#   retention_in_days = 14
# }

# resource "aws_cloudwatch_log_group" "msk-debezium-video-connector-logs" {
#   name              = "msk-debezium-video-connecter-logs"
#   retention_in_days = 14
# }

resource "aws_security_group" "msk-sg" {
  vpc_id = aws_vpc.prod.id
  name   = "msk-sg"
  tags = {
    Name = "msk-sg"
  }
}

resource "aws_security_group_rule" "msk-sg-https-out" {
  security_group_id = aws_security_group.msk-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "msk-sg-kafka-in" {
  security_group_id = aws_security_group.msk-sg.id
  type              = "ingress"
  from_port         = 9092
  to_port           = 9098
  protocol          = "tcp"
  # cidr_blocks       = concat(aws_subnet.prod-private[*].cidr_block, data.aws_subnet.frever-prod-private[*].cidr_block)
  cidr_blocks = concat(aws_subnet.prod-private[*].cidr_block, formatlist(data.aws_vpc.frever-prod.cidr_block))
}

# resource "aws_appautoscaling_target" "msk-storage" {
#   max_capacity       = 1000
#   min_capacity       = 1
#   resource_id        = aws_msk_cluster.machine-learning-msk.arn
#   scalable_dimension = "kafka:broker-storage:VolumeSize"
#   service_namespace  = "kafka"
# }

# resource "aws_appautoscaling_policy" "kafka-broker-scaling-policy" {
#   name               = "${aws_msk_cluster.machine-learning-msk.cluster_name}-broker-scaling"
#   policy_type        = "TargetTrackingScaling"
#   resource_id        = aws_msk_cluster.machine-learning-msk.arn
#   scalable_dimension = aws_appautoscaling_target.msk-storage.scalable_dimension
#   service_namespace  = aws_appautoscaling_target.msk-storage.service_namespace
#
#   target_tracking_scaling_policy_configuration {
#     predefined_metric_specification {
#       predefined_metric_type = "KafkaBrokerStorageUtilization"
#     }
#
#     disable_scale_in = true
#     target_value     = 70
#   }
# }

# resource "aws_msk_configuration" "machine-learning-msk-configuration" {
#   kafka_versions = [local.kafka-version]
#   name           = "machine-learning-msk-configuration"
#
#   server_properties = <<PROPERTIES
# auto.create.topics.enable = true
# default.replication.factor = 3
# min.insync.replicas = 2
# num.io.threads = 8
# num.network.threads = 5
# num.partitions = 1
# num.replica.fetchers = 2
# replica.lag.time.max.ms = 30000
# socket.receive.buffer.bytes = 102400
# socket.request.max.bytes = 104857600
# socket.send.buffer.bytes = 102400
# unclean.leader.election.enable = true
# zookeeper.session.timeout.ms = 18000
# delete.topic.enable = true
# log.retention.hours = 168
# log.retention.bytes = 214748364800
# PROPERTIES
# }

# resource "aws_msk_cluster" "machine-learning-msk" {
#   cluster_name           = "machine-learning-prod-msk"
#   kafka_version          = local.kafka-version
#   number_of_broker_nodes = 3
#
#   broker_node_group_info {
#     instance_type  = "kafka.m5.large"
#     client_subnets = aws_subnet.prod-private[*].id
#
#     storage_info {
#       ebs_storage_info {
#         volume_size = 300
#       }
#     }
#     security_groups = [aws_security_group.msk-sg.id]
#   }
#
#   encryption_info {
#     encryption_at_rest_kms_key_arn = aws_kms_key.kms.arn
#   }
#
#   configuration_info {
#     arn      = aws_msk_configuration.machine-learning-msk-configuration.arn
#     revision = aws_msk_configuration.machine-learning-msk-configuration.latest_revision
#   }
#
#   client_authentication {
#     sasl {
#       iam = true
#     }
#   }
#
#   logging_info {
#     broker_logs {
#       cloudwatch_logs {
#         enabled   = true
#         log_group = aws_cloudwatch_log_group.msk-broker-logs.name
#       }
#     }
#   }
# }

# resource "aws_s3_bucket" "msk-debezium" {
#   bucket = "frever-msk-debezium-postgresql"
# }
#
# resource "aws_s3_bucket_acl" "msk-debezium-s3-acl" {
#   bucket = aws_s3_bucket.msk-debezium.id
#   acl    = "private"
# }
#
# resource "aws_s3_bucket_lifecycle_configuration" "msk-debezium-s3-lifecycle" {
#   bucket = aws_s3_bucket.msk-debezium.id
#
#   rule {
#     id = "msk-debezium-s3-lifecycle"
#
#     abort_incomplete_multipart_upload {
#       days_after_initiation = 3
#     }
#
#     status = "Enabled"
#   }
# }
#
# resource "aws_s3_bucket_public_access_block" "msk-debezium-s3-block-public-access" {
#   bucket                  = aws_s3_bucket.msk-debezium.id
#   block_public_acls       = true
#   block_public_policy     = true
#   ignore_public_acls      = true
#   restrict_public_buckets = true
# }
#
# resource "aws_mskconnect_custom_plugin" "debezium-msk-postgresql" {
#   name         = "debezium-msk-postgresql"
#   content_type = "ZIP"
#   location {
#     s3 {
#       bucket_arn = aws_s3_bucket.msk-debezium.arn
#       file_key   = "debezium-msk-postgresql-secret-manager.zip"
#     }
#   }
# }
#
# resource "aws_mskconnect_worker_configuration" "debezium-postgresql-worker-configuration" {
#   name                    = "msk-debezium-postgresql-worker-configuration"
#   properties_file_content = <<EOT
# key.converter=org.apache.kafka.connect.json.JsonConverter
# key.converter.schemas.enable=false
# value.converter=org.apache.kafka.connect.json.JsonConverter
# value.converter.schemas.enable=false
# config.providers.secretManager.class=com.github.jcustenborder.kafka.config.aws.SecretsManagerConfigProvider
# config.providers=secretManager
# config.providers.secretManager.param.aws.region=eu-central-1
# EOT
# }

# resource "aws_iam_role" "debezium-postgresql-service-execution-role" {
#   name = "debezium-postgresql-service-execution-role"
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Action = "sts:AssumeRole"
#         Sid    = "KafkaAssume"
#         Effect = "Allow"
#         Principal = {
#           Service = "kafkaconnect.amazonaws.com"
#         }
#         Condition = {
#           StringEquals = {
#             "aws:SourceAccount" = data.aws_caller_identity.current.account_id
#           }
#           # ArnLike = {
#           #   "aws:SourceArn" = "MSK-Connector-ARN"
#           # }
#         }
#       }
#     ]
#   })
# }

# resource "aws_iam_role_policy" "debezium-postgresql-service-execution-role-policy" {
#   name = "debezium-postgresql-service-execution-role-policy"
#   role = aws_iam_role.debezium-postgresql-service-execution-role.id
#
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Action = [
#           "kafka-cluster:Connect",
#           "kafka-cluster:DescribeCluster"
#         ]
#         Sid      = "MskClusterPermission"
#         Effect   = "Allow"
#         Resource = aws_msk_cluster.machine-learning-msk.arn
#       },
#       {
#         Action = [
#           "kafka-cluster:ReadData",
#           "kafka-cluster:WriteData",
#           "kafka-cluster:CreateTopic",
#           "kafka-cluster:DescribeTopic"
#         ]
#         Sid      = "MskTopicPermission"
#         Effect   = "Allow"
#         Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:topic/${aws_msk_cluster.machine-learning-msk.cluster_name}/*"
#       },
#       {
#         Action = [
#           "kafka-cluster:AlterGroup",
#           "kafka-cluster:DescribeGroup"
#         ]
#         Sid      = "MskGroupPermission"
#         Effect   = "Allow"
#         Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:group/${aws_msk_cluster.machine-learning-msk.cluster_name}/*"
#       },
#       {
#         Action = [
#           "secretsmanager:GetResourcePolicy",
#           "secretsmanager:GetSecretValue",
#           "secretsmanager:DescribeSecret",
#           "secretsmanager:ListSecretVersionIds"
#         ]
#         Sid      = "SecretManagerPermission"
#         Effect   = "Allow"
#         Resource = [aws_secretsmanager_secret.prod-main-db-credential.arn, aws_secretsmanager_secret.prod-video-db-credential.arn]
#       },
#     ]
#   })
# }

# resource "aws_security_group" "msk-debezium-connector-sg" {
#   vpc_id = aws_vpc.prod.id
#   name   = "msk-debezium-connector-sg"
# }
#
# resource "aws_security_group_rule" "msk-debezium-connector-sg-https-out" {
#   security_group_id = aws_security_group.msk-debezium-connector-sg.id
#   type              = "egress"
#   from_port         = 443
#   to_port           = 443
#   protocol          = "tcp"
#   cidr_blocks       = ["0.0.0.0/0"]
#   ipv6_cidr_blocks  = ["::/0"]
# }
#
# resource "aws_security_group_rule" "msk-debezium-connector-sg-access-kafka" {
#   security_group_id        = aws_security_group.msk-debezium-connector-sg.id
#   type                     = "egress"
#   from_port                = 9092
#   to_port                  = 9098
#   protocol                 = "tcp"
#   source_security_group_id = aws_security_group.msk-sg.id
# }
#
# resource "aws_security_group_rule" "msk-debezium-connector-sg-access-postgresql" {
#   security_group_id        = aws_security_group.msk-debezium-connector-sg.id
#   type                     = "egress"
#   from_port                = 5432
#   to_port                  = 5432
#   protocol                 = "tcp"
#   source_security_group_id = data.aws_security_group.frever-prod-postgresql.id
# }

data "aws_security_group" "frever-prod-postgresql" {
  provider = aws.frever
  vpc_id   = data.aws_vpc.frever-prod.id
  filter {
    name   = "tag:Name"
    values = ["content-prod-db"]
  }
}

# resource "aws_secretsmanager_secret" "prod-main-db-credential" {
#   name = "prod-main-db-credential"
#   tags = {
#     "Purpose" = "For machine-learning msk-debezium-connector"
#   }
# }
#
# resource "aws_secretsmanager_secret_version" "prod-main-db-credential-value" {
#   secret_id     = aws_secretsmanager_secret.prod-main-db-credential.id
#   secret_string = jsonencode(local.db-credential-placeholder)
#   lifecycle {
#     ignore_changes = [secret_string]
#   }
# }

# resource "aws_secretsmanager_secret" "prod-video-db-credential" {
#   name = "prod-video-db-credential"
#   tags = {
#     "Purpose" = "For machine-learning msk-debezium-connector"
#   }
# }
#
# resource "aws_secretsmanager_secret_version" "prod-video-db-credential-value" {
#   secret_id     = aws_secretsmanager_secret.prod-video-db-credential.id
#   secret_string = jsonencode(local.db-credential-placeholder)
#   lifecycle {
#     ignore_changes = [secret_string]
#   }
# }

data "aws_db_instance" "prod-main-db" {
  provider               = aws.frever
  db_instance_identifier = "production-main"
}

# data "aws_db_instance" "prod-video-db" {
#   provider               = aws.frever
#   db_instance_identifier = "production-video"
# }

# Video DB is merged into Main DB
# resource "aws_mskconnect_connector" "debezium-postgresql-video" {
#   name = "debezium-postgresql-video-v1"
#
#   kafkaconnect_version = "2.7.1"
#
#   capacity {
#     provisioned_capacity {
#       mcu_count    = 2
#       worker_count = 1
#     }
#   }
#
#   connector_configuration = {
#     "connector.class"                                 = "io.debezium.connector.postgresql.PostgresConnector"
#     "tasks.max"                                       = "1"
#     "database.hostname"                               = data.aws_db_instance.prod-video-db.address
#     "database.user"                                   = "$${secretManager:${aws_secretsmanager_secret.prod-video-db-credential.name}:username}",
#     "database.password"                               = "$${secretManager:${aws_secretsmanager_secret.prod-video-db-credential.name}:password}",
#     "database.dbname"                                 = "db"
#     "topic.prefix"                                    = "video",
#     "plugin.name"                                     = "pgoutput",
#     "schema.history.internal.kafka.bootstrap.servers" = aws_msk_cluster.machine-learning-msk.bootstrap_brokers
#     "include.schema.changes"                          = "true"
#     "schema.include.list"                             = "public"
#     "table.exclude.list"                              = "public.VideoReport,public.VideoReportReason"
#     "publication.autocreate.mode"                     = "filtered"
#     "heartbeat.interval.ms"                           = 60000
#     "heartbeat.action.query"                          = "SELECT pg_logical_emit_message(false, 'heartbeat', now()::varchar);"
#   }
#
#   kafka_cluster {
#     apache_kafka_cluster {
#       bootstrap_servers = aws_msk_cluster.machine-learning-msk.bootstrap_brokers_sasl_iam
#
#       vpc {
#         security_groups = [aws_security_group.msk-debezium-connector-sg.id]
#         subnets         = aws_subnet.prod-private[*].id
#       }
#     }
#   }
#
#   kafka_cluster_client_authentication {
#     authentication_type = "IAM"
#   }
#
#   kafka_cluster_encryption_in_transit {
#     encryption_type = "TLS"
#   }
#
#   plugin {
#     custom_plugin {
#       arn      = aws_mskconnect_custom_plugin.debezium-msk-postgresql.arn
#       revision = aws_mskconnect_custom_plugin.debezium-msk-postgresql.latest_revision
#     }
#   }
#
#   worker_configuration {
#     arn      = aws_mskconnect_worker_configuration.debezium-postgresql-worker-configuration.arn
#     revision = aws_mskconnect_worker_configuration.debezium-postgresql-worker-configuration.latest_revision
#   }
#
#   log_delivery {
#     worker_log_delivery {
#       cloudwatch_logs {
#         enabled   = true
#         log_group = aws_cloudwatch_log_group.msk-debezium-video-connector-logs.name
#       }
#     }
#   }
#
#   service_execution_role_arn = aws_iam_role.debezium-postgresql-service-execution-role.arn
# }

# resource "aws_mskconnect_connector" "debezium-postgresql-main" {
#   name = "debezium-postgresql-main-v1"
#
#   kafkaconnect_version = "2.7.1"
#
#   capacity {
#     provisioned_capacity {
#       mcu_count    = 2
#       worker_count = 1
#     }
#   }
#
#   connector_configuration = {
#     "connector.class"                                 = "io.debezium.connector.postgresql.PostgresConnector"
#     "tasks.max"                                       = "1"
#     "database.hostname"                               = data.aws_db_instance.prod-main-db.address
#     "database.user"                                   = "$${secretManager:${aws_secretsmanager_secret.prod-main-db-credential.name}:username}",
#     "database.password"                               = "$${secretManager:${aws_secretsmanager_secret.prod-main-db-credential.name}:password}",
#     "database.dbname"                                 = "db"
#     "topic.prefix"                                    = "main",
#     "plugin.name"                                     = "pgoutput",
#     "schema.history.internal.kafka.bootstrap.servers" = aws_msk_cluster.machine-learning-msk.bootstrap_brokers
#     "include.schema.changes"                          = "true"
#     "schema.include.list"                             = "public"
#     "table.exclude.list"                              = "public.AppleSignInEmail,public.Caption,public.EditorSettings,public.Font,public.MarketingScreenshot,public.SpawnPositionSpaceSize,public.StorageFile,public.ur"
#     "publication.autocreate.mode"                     = "filtered"
#     "heartbeat.interval.ms"                           = 60000
#     "heartbeat.action.query"                          = "SELECT pg_logical_emit_message(false, 'heartbeat', now()::varchar);"
#   }
#
#   kafka_cluster {
#     apache_kafka_cluster {
#       bootstrap_servers = aws_msk_cluster.machine-learning-msk.bootstrap_brokers_sasl_iam
#
#       vpc {
#         security_groups = [aws_security_group.msk-debezium-connector-sg.id]
#         subnets         = aws_subnet.prod-private[*].id
#       }
#     }
#   }
#
#   kafka_cluster_client_authentication {
#     authentication_type = "IAM"
#   }
#
#   kafka_cluster_encryption_in_transit {
#     encryption_type = "TLS"
#   }
#
#   plugin {
#     custom_plugin {
#       arn      = aws_mskconnect_custom_plugin.debezium-msk-postgresql.arn
#       revision = aws_mskconnect_custom_plugin.debezium-msk-postgresql.latest_revision
#     }
#   }
#
#   worker_configuration {
#     arn      = aws_mskconnect_worker_configuration.debezium-postgresql-worker-configuration.arn
#     revision = aws_mskconnect_worker_configuration.debezium-postgresql-worker-configuration.latest_revision
#   }
#
#   log_delivery {
#     worker_log_delivery {
#       cloudwatch_logs {
#         enabled   = true
#         log_group = aws_cloudwatch_log_group.msk-debezium-main-connector-logs.name
#       }
#     }
#   }
#
#   service_execution_role_arn = aws_iam_role.debezium-postgresql-service-execution-role.arn
# }
