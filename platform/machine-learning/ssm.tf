locals {
  envs             = ["dev", "prod"]
  redshift-secrets = ["redshift-host", "redshift-username", "redshift-password"]
  redshift-secrets-by-envs = distinct(flatten([
    for env in local.envs : [
      for secret in local.redshift-secrets : {
        env    = env
        secret = secret
      }
    ]
  ]))
  postgresql-db-instances = ["main", "video", "machine-learning"]
  postgresql-credentials  = ["username", "password"]
  postgresql-credentials-by-db-instances = distinct(flatten([
    for db in local.postgresql-db-instances : [
      for secret in local.postgresql-credentials : {
        db     = db
        secret = secret
      }
    ]
  ]))
  frever-dev-vpc-rds-ssm-entries = ["host", "username", "password"]
  ml-rds-ssm-entries             = ["username", "password"]
}

resource "aws_ssm_parameter" "ml-rds-ssm" {
  for_each = toset(local.ml-rds-ssm-entries)
  name     = "/ml-postgresql-rds/${each.value}"
  type     = endswith(each.value, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "dev-ml-rds-ssm" {
  for_each = toset(local.ml-rds-ssm-entries)
  name     = "/dev-ml-postgresql-rds/${each.value}"
  type     = endswith(each.value, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "ixia-prod-rds-ssm" {
  for_each = toset(local.ml-rds-ssm-entries)
  name     = "/ixia-prod-postgresql-rds/${each.value}"
  type     = endswith(each.value, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "redshift-credentials" {
  for_each = { for entry in local.redshift-secrets-by-envs : "${entry.env}.${entry.secret}" => entry }
  name     = "/msk/${each.value.env}/${each.value.secret}"
  type     = endswith(each.value.secret, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "postgresql-rds-credentials" {
  for_each = { for entry in local.postgresql-credentials-by-db-instances : "${entry.db}.${entry.secret}" => entry }
  name     = "/postgresql-rds/${each.value.db}/${each.value.secret}"
  type     = endswith(each.value.secret, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "frever-dev-vpc-rds-ssm" {
  for_each = toset(local.frever-dev-vpc-rds-ssm-entries)
  name     = "/frever-dev-postgresql-rds/${each.value}"
  type     = endswith(each.value, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_kms_key" "session-manager-kms-key" {
  description             = "For the loggings from Session Manager"
  deletion_window_in_days = 7
}

resource "aws_kms_alias" "session-manager-kms-key-alias" {
  name          = "alias/session-manager-kms-key"
  target_key_id = aws_kms_key.session-manager-kms-key.key_id
}

resource "aws_cloudwatch_log_group" "session-manager-log-group" {
  depends_on        = [aws_kms_key_policy.session-manager-kms-key-policy]
  name              = "session-manager"
  retention_in_days = 7
  kms_key_id        = aws_kms_key.session-manager-kms-key.arn
}

resource "aws_kms_key_policy" "session-manager-kms-key-policy" {
  key_id = aws_kms_key.session-manager-kms-key.id
  policy = jsonencode({
    Id = "session-manager-kms-key-policy"
    Statement = [
      {
        Action = "kms:*"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }

        Resource = "*"
        Sid      = "Enable IAM User Permissions"
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
        Sid      = "Enable IAM User Permissions"
        Condition = {
          ArnEquals = {
            "kms:EncryptionContext:aws:logs:arn" = "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:log-group:session-manager"
          }
        }
      },
    ]
    Version = "2012-10-17"
  })
}
