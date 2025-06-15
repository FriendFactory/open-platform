resource "aws_ssm_parameter" "db-username" {
  name        = "/cms-authorization/db-username"
  type        = "SecureString"
  value       = "changeme"
  description = "DB username"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "db-password" {
  name        = "/cms-authorization/db-password"
  type        = "SecureString"
  value       = "changeme"
  description = "DB password"
  lifecycle {
    ignore_changes = [value]
  }
}

data "aws_caller_identity" "current" {}

data "aws_iam_openid_connect_provider" "content-stage-openid-connect-provider" {
  url = "https://oidc.eks.eu-central-1.amazonaws.com/id/A01450063EA0ADDD801ADF701C18ED27"
}

data "aws_kms_key" "ssm" {
  key_id = "alias/aws/ssm"
}

resource "aws_iam_role" "cms-authorization-app-role" {
  name = "cms-authorization-app-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Sid    = "AppServiceRoleAssume"
        Effect = "Allow"
        Principal = {
          Federated = data.aws_iam_openid_connect_provider.content-stage-openid-connect-provider.arn
        }
        Condition = {
          StringEquals = {
            "oidc.eks.eu-central-1.amazonaws.com/id/A01450063EA0ADDD801ADF701C18ED27:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "oidc.eks.eu-central-1.amazonaws.com/id/A01450063EA0ADDD801ADF701C18ED27:sub" = [
              "system:serviceaccount:cms:default"
            ]
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "app-policy" {
  name = "cms-authorization-app-policy"
  role = aws_iam_role.cms-authorization-app-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = "ssm:DescribeParameters"
        Sid      = "DescribeParameters"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "ssm:PutParameter",
          "ssm:DeleteParameter",
          "ssm:GetParameterHistory",
          "ssm:GetParametersByPath",
          "ssm:GetParameters",
          "ssm:GetParameter",
          "ssm:DeleteParameters"
        ]
        Sid      = "UseSsmParameters"
        Effect   = "Allow"
        Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/cms-authorization/*"
      },
    ]
  })
}
