module "pre-prod-backend" {
  source               = "../../../tf-modules/frever-backend/"
  vpc_name             = "pre-prod"
  cidr_block           = "10.250.0.0/16"
  bastion_instance_ami = "ami-02e106be39680b912"
  require_rds          = false
}

data "aws_kms_alias" "rds" {
  name = "alias/aws/rds"
}

data "aws_iam_role" "rds-monitoring-role" {
  name = "rds-monitoring-role"
}

locals {
  dbs = [
    {
      "db_name_postfix" : "main",
      "allocated_storage" : 100,
      "source_db_instance_identifier" : "production-main"
    },
    {
      "db_name_postfix" : "auth",
      "allocated_storage" : 50,
      "source_db_instance_identifier" : "production-auth"
    }
  ]
}

resource "aws_db_instance" "pre-prod-postgresql-rds" {
  for_each                     = { for entry in local.dbs : "${entry.db_name_postfix}" => entry }
  identifier                   = "pre-prod-${each.key}"
  allocated_storage            = each.value.allocated_storage
  max_allocated_storage        = each.value.allocated_storage * 5
  engine                       = "postgres"
  engine_version               = "14.12"
  instance_class               = "db.t4g.small"
  username                     = "Master"
  kms_key_id                   = data.aws_kms_alias.rds.target_key_arn
  manage_master_user_password  = true
  parameter_group_name         = "frever-postgresql-14"
  skip_final_snapshot          = true
  db_subnet_group_name         = module.pre-prod-backend.db-subnet-group.name
  vpc_security_group_ids       = [module.pre-prod-backend.rds-sg.id]
  backup_retention_period      = "30"
  backup_window                = "03:00-03:30"
  maintenance_window           = "sun:04:00-sun:04:30"
  storage_type                 = "gp3"
  performance_insights_enabled = "true"
  monitoring_interval          = 60
  monitoring_role_arn          = data.aws_iam_role.rds-monitoring-role.arn
  storage_encrypted            = "true"

  restore_to_point_in_time {
    use_latest_restorable_time    = true
    source_db_instance_identifier = each.value.source_db_instance_identifier
  }

  timeouts {
    create = "2h30m"
    update = "1h"
    delete = "40m"
  }
}

