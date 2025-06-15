locals {
  environments = ["prod", "stage", "test", "dev", "ixia-prod"]
  app-secrets = ["main/username", "main/password", "redshift/username", "redshift/password"]
  app-secrets-by-environments = distinct(flatten([
    for environment in local.environments : [
      for secret in local.app-secrets : {
        environment = environment
        secret      = secret
      }
    ]
  ]))
}

resource "aws_ssm_parameter" "app-secret" {
  for_each = {for entry in local.app-secrets-by-environments : "${entry.environment}.${entry.secret}" => entry}
  name     = "/timers/${each.value.environment}/${each.value.secret}"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

# The service terraform code is in "platform/ecs/"