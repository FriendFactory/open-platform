locals {
  parameters = [
    {
      apply_method = "pending-reboot", name = "shared_preload_libraries", value = "pg_stat_statements,auto_explain"
    },
    {
      name = "auto_explain.log_min_duration", value = "500"
    },
    {
      name = "log_min_duration_statement", value = "500"
    },
    {
      name = "compute_query_id", value = "on"
    },
    {
      apply_method = "pending-reboot", name = "track_activity_query_size", value = "40960"
    },
    {
      name = "work_mem", value = "10240"
    },
    {
      name = "autovacuum", value = "1"
    },
    {
      name = "log_connections", value = "1"
    },
    {
      name = "max_standby_streaming_delay", value = "600000"
    },
    {
      name = "rds.force_ssl", value = "0"
    },
  ]
}

resource "aws_db_parameter_group" "postgresql-15-logical-replication" {
  name        = "frever-postgresql-15-logical-replication"
  family      = "postgres15"
  description = "Custom settings for PostgreSQL 15 with logical replication enabled, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(concat(local.parameters, [{ apply_method = "pending-reboot", name = "rds.logical_replication", value = "1" }]))
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

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

data "aws_iam_role" "rds-monitoring-role" {
  name = "rds-monitoring-role"
}

resource "aws_db_subnet_group" "prod-private" {
  name       = "prod-private"
  subnet_ids = data.aws_subnets.prod-private.ids
}

# resource "aws_db_instance" "machine-learning-postgresql-rds-prod" {
#   identifier                   = "machine-learning-postgresql-rds-prod"
#   allocated_storage            = 30
#   max_allocated_storage        = 100
#   db_name                      = "frever"
#   engine                       = "postgres"
#   engine_version               = "15.4"
#   instance_class               = "db.t4g.small"
#   multi_az                     = "true"
#   username                     = "frever"
#   manage_master_user_password  = true
#   parameter_group_name         = aws_db_parameter_group.postgresql-15-logical-replication.name
#   skip_final_snapshot          = true
#   db_subnet_group_name         = aws_db_subnet_group.prod-private.name
#   vpc_security_group_ids       = [aws_security_group.machine-learning-pg-sg.id]
#   backup_retention_period      = "30"
#   backup_window                = "03:00-03:30"
#   maintenance_window           = "sun:04:00-sun:04:30"
#   storage_type                 = "gp3"
#   performance_insights_enabled = "true"
#   monitoring_interval          = 60
#   monitoring_role_arn          = data.aws_iam_role.rds-monitoring-role.arn
#   storage_encrypted            = "true"
# }

resource "aws_security_group" "machine-learning-pg-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "machine-learning-pg-sg"
  description = "The SG for the PostgreSQL used for machine-learning PostgreSQL RDS."
  tags = {
    Name = "machine-learning-pg-sg"
  }
}

resource "aws_security_group_rule" "machine-learning-pg-sg-postgresql-in" {
  security_group_id        = aws_security_group.machine-learning-pg-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.machine-learning-pg-client-sg.id

}

resource "aws_security_group_rule" "machine-learning-pg-sg-access-content-prod-postgresql" {
  security_group_id        = aws_security_group.machine-learning-pg-sg.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.frever-prod-postgresql.id
}

data "aws_vpc" "frever-prod" {
  provider = aws.frever
  tags = {
    Name = "content-prod"
  }
}

data "aws_security_group" "frever-prod-postgresql" {
  provider = aws.frever
  vpc_id   = data.aws_vpc.frever-prod.id
  filter {
    name   = "tag:Name"
    values = ["content-prod-db"]
  }
}

resource "aws_security_group" "machine-learning-pg-client-sg" {
  vpc_id      = data.aws_vpc.prod.id
  name        = "machine-learning-pg-client-sg"
  description = "The SG for the PostgreSQL Client used for machine-learning PostgreSQL RDS."
  tags = {
    Name = "machine-learning-pg-client-sg"
  }
}

resource "aws_security_group_rule" "machine-learning-pg-client-sg-postgresql-out" {
  security_group_id        = aws_security_group.machine-learning-pg-client-sg.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.machine-learning-pg-sg.id

}

