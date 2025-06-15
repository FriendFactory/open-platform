locals {
  eks-clusters = ["prod"]
  app-secrets  = ["auth-certificate", "auth-certificatePassword", "auth-clientSecret", "google-api-key", "cs-auth", "cs-main", "cs-main-replica", "cs-video", "oneSignal-apiKey", "oneSignal-appId", "twilio-messagingServiceSid", "twilio-secret", "twilio-sid", "twilio-verifyServiceSid", "data-protector-key", "music-provider-oauth-consumer-key", "music-provider-oauth-consumer-secret", "hive-text-moderation-key", "hive-visual-moderation-key", "blokur-apiToken", "apps-flyer-token", "stableDiffusion-apiKey", "replicate-apiKey"]
  app-secrets-by-eks-clusters = distinct(flatten([
    for cluster in local.eks-clusters : [
      for secret in local.app-secrets : {
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
  tier     = each.value.cluster == "prod" && each.value.secret == "auth-certificate" ? "Advanced" : "Standard"
  lifecycle {
    ignore_changes = [value]
  }
}

