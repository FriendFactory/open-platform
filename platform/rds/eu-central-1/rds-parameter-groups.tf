locals {
  parameters_14 = [
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
      name = "work_mem", value = "20480"
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
      name = "random_page_cost", value = "1.2"
    },
  ]

  parameters = concat(local.parameters_14, [{ name = "rds.force_ssl", value = "0" }])
}

resource "aws_db_parameter_group" "postgresql-14" {
  name        = "frever-postgresql-14"
  family      = "postgres14"
  description = "Custom settings for PostgreSQL 14, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(local.parameters_14)
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

resource "aws_db_parameter_group" "postgresql-14-logical-replication" {
  name        = "frever-postgresql-14-logical-replication"
  family      = "postgres14"
  description = "Custom settings for PostgreSQL 14 with logical replication enabled, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(concat(local.parameters_14, [{ apply_method = "pending-reboot", name = "rds.logical_replication", value = "1" }]))
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

resource "aws_db_parameter_group" "postgresql-14-logical-replication-and-data-import" {
  name        = "frever-postgresql-14-logical-replication-and-data-import"
  family      = "postgres14"
  description = "Custom settings for PostgreSQL 14 with logical replication enabled and tweaks for data import, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(concat(local.parameters_14, [{ apply_method = "pending-reboot", name = "rds.logical_replication", value = "1" }, { apply_method = "pending-reboot", name = "session_replication_role", value = "replica" }]))
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

resource "aws_db_parameter_group" "postgresql-15" {
  name        = "frever-postgresql-15"
  family      = "postgres15"
  description = "Custom settings for PostgreSQL 15, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(local.parameters)
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
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

resource "aws_db_parameter_group" "postgresql-15-logical-replication-and-data-import" {
  name        = "frever-postgresql-15-logical-replication-and-data-import"
  family      = "postgres15"
  description = "Custom settings for PostgreSQL 15 with logical replication enabled and tweaks for data import, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(concat(local.parameters, [{ apply_method = "pending-reboot", name = "rds.logical_replication", value = "1" }, { apply_method = "pending-reboot", name = "session_replication_role", value = "replica" }]))
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

resource "aws_db_parameter_group" "postgresql-17" {
  name        = "frever-postgresql-17"
  family      = "postgres17"
  description = "Custom settings for PostgreSQL 17, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(local.parameters)
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

resource "aws_db_parameter_group" "postgresql-17-logical-replication" {
  name        = "frever-postgresql-17-logical-replication"
  family      = "postgres17"
  description = "Custom settings for PostgreSQL 17 with logical replication enabled, managed by Terraform"

  dynamic "parameter" {
    for_each = toset(concat(local.parameters, [{ apply_method = "pending-reboot", name = "rds.logical_replication", value = "1" }]))
    content {
      apply_method = can(parameter.key["apply_method"]) ? parameter.key.apply_method : "immediate"
      name         = parameter.key.name
      value        = parameter.key.value
    }
  }
}

