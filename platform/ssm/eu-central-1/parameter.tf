locals {
  eks-clusters = ["content-prod", "content-stage", "content-test", "dev", "dev-2", "ixia-prod"]
  app-secrets  = ["auth-certificate", "auth-certificatePassword", "auth-clientSecret", "google-api-key", "cs-auth", "cs-main", "cs-main-replica","cs-video", "oneSignal-apiKey", "oneSignal-appId", "twilio-messagingServiceSid", "twilio-secret", "twilio-sid", "twilio-verifyServiceSid", "data-protector-key", "music-provider-oauth-consumer-key", "music-provider-oauth-consumer-secret", "hive-text-moderation-key", "hive-visual-moderation-key", "blokur-apiToken", "apps-flyer-token", "stableDiffusion-apiKey", "replicate-apiKey", "klingAccessKey", "klingSecretKey", "pixVerseApiKey"]
  app-secrets-by-eks-clusters = distinct(flatten([
    for cluster in local.eks-clusters : [
      for secret in local.app-secrets : {
        cluster = cluster
        secret  = secret
      }
    ]
  ]))
  feed-recsys-secrets = ["redshift-host", "redshift-username", "redshift-password"]
  ixia-eks-clusters = ["dev", "ixia-prod"]
  ixia-app-secrets = ["appStoreIssuerId", "appStoreKeyData", "appStoreKeyId", "appStoreSharedSecret"]
  ixia-app-secrets-by-eks-clusters = distinct(flatten([
    for cluster in local.ixia-eks-clusters : [
      for secret in local.ixia-app-secrets : {
        cluster = cluster
        secret  = secret
      }
    ]
  ]))

}

resource "aws_ssm_parameter" "app-secret-in-eks-cluster" {
  for_each = { for entry in local.app-secrets-by-eks-clusters : "${entry.cluster}.${entry.secret}" => entry }
  name     = "/${each.value.cluster}/secrets/${each.value.secret}"
  type     = "SecureString"
  value    = "changeme"
  tier     = (each.value.cluster == "content-prod" || each.value.cluster == "ixia-prod") && each.value.secret == "auth-certificate" ? "Advanced" : "Standard"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "app-secret-in-ixia-eks-cluster" {
  for_each = { for entry in local.ixia-app-secrets-by-eks-clusters : "${entry.cluster}.${entry.secret}" => entry }
  name     = "/${each.value.cluster}/secrets/${each.value.secret}"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "k8s-tls-ca" {
  for_each = toset(local.eks-clusters)
  name     = "/k8s-tls-ca/${each.key}"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "prometheus-service-account-token" {
  for_each = toset(local.eks-clusters)
  name     = "/prometheus-service-account-token/${each.key}"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "redshift-credentials-for-feed-recsys" {
  for_each = toset(local.feed-recsys-secrets)
  name     = "/feed-recsys/${each.key}"
  type     = endswith(each.key, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}
resource "aws_ssm_parameter" "k8s-tls-ca-prod-us" {
  name     = "/k8s-tls-ca/prod-us"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "prometheus-service-account-token-prod-us" {
  name     = "/prometheus-service-account-token/prod-us"
  type     = "SecureString"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

