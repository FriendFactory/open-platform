data "aws_iam_policy" "power-user-access" {
  name = "PowerUserAccess"
}

data "aws_iam_policy" "administrator-access" {
  name = "AdministratorAccess"
}

resource "aws_iam_policy" "manage-ml-services" {
  name        = "manage-ml-services"
  description = "Allow users to manage ML services"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "CloudWatchAndLogsRead"
        Effect = "Allow"
        Action = [
          "cloudwatch:Describe*",
          "cloudwatch:Get*",
          "cloudwatch:List*",
          "logs:Describe*",
          "logs:Get*",
          "logs:List*",
          "logs:Filter*",
        ]
        Resource = "*"
      },
      {
        Sid    = "Ecs"
        Effect = "Allow"
        Action = [
          "ecs:Describe*",
          "ecs:List*",
          "ecs:Get*",
          "ecs:UpdateService",
        ]
        Resource = "*"
      },
      {
        Sid    = "Ecr"
        Effect = "Allow"
        Action = [
          "ecr:Describe*",
          "ecr:List*",
          "ecr:*Get*",
          "ecr:*Check*",
        ]
        Resource = "*"
      },
      {
        Action = [
          "ssm:DescribeParameter*",
        ]
        Sid      = "SsmDescribe"
        Effect   = "Allow"
        Resource = ["*"]
      },
      {
        Action = [
          "ssm:GetParameter*",
        ]
        Sid      = "SsmGet"
        Effect   = "Allow"
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/msk/*", "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/postgresql-rds/*"]
      },
      {
        Sid = "S3BucketRead"
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:*Lifecycle*",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-machine-learning-sagemaker*"]
      },
      {
        Sid    = "S3ObjectRead"
        Effect = "Allow"
        Action = [
          "s3:Delete*Object*",
          "s3:Get*Object*",
          "s3:Put*Object*",
        ]
        Resource = ["arn:aws:s3:::frever-machine-learning-sagemaker*/*"]
      },
    ]
  })
}

resource "aws_iam_policy" "allow-users-to-manage-their-own-credentials-and-mfa-devices" {
  name        = "allow-users-to-manage-their-own-credentials-and-mfa-devices"
  description = "Allow users to manage their own credentials and MFA devices"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowViewAccountInfo"
        Effect = "Allow"
        Action = [
          "iam:GetAccountPasswordPolicy",
          "iam:GetAccountSummary",
          "iam:ListVirtualMFADevices"
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowManageOwnPasswords"
        Effect = "Allow"
        Action = [
          "iam:ChangePassword",
          "iam:GetUser"
        ]
        Resource = "arn:aws:iam::*:user/$${aws:username}"
      },
      {
        Sid    = "AllowManageOwnAccessKeys"
        Effect = "Allow"
        Action = [
          "iam:CreateAccessKey",
          "iam:DeleteAccessKey",
          "iam:ListAccessKeys",
          "iam:UpdateAccessKey"
        ]
        Resource = "arn:aws:iam::*:user/$${aws:username}"
      },
      {
        Sid    = "AllowManageOwnSSHPublicKeys"
        Effect = "Allow"
        Action = [
          "iam:DeleteSSHPublicKey",
          "iam:GetSSHPublicKey",
          "iam:ListSSHPublicKeys",
          "iam:UpdateSSHPublicKey",
          "iam:UploadSSHPublicKey"
        ]
        Resource = "arn:aws:iam::*:user/$${aws:username}"
      },
      {
        Sid    = "AllowManageOwnVirtualMFADevice"
        Effect = "Allow"
        Action = [
          "iam:CreateVirtualMFADevice"
        ]
        Resource = "arn:aws:iam::*:mfa/*"
      },
      {
        Sid    = "AllowManageOwnUserMFA"
        Effect = "Allow"
        Action = [
          "iam:DeactivateMFADevice",
          "iam:EnableMFADevice",
          "iam:GetUser",
          "iam:ListMFADevices",
          "iam:ResyncMFADevice"
        ]
        Resource = "arn:aws:iam::*:user/$${aws:username}"
      },
      {
        Sid    = "AllowPassRoleAndGetRole"
        Effect = "Allow"
        Action = [
          "iam:PassRole",
          "iam:GetRole"
        ]
        Resource = [aws_iam_role.msk-playground-role.arn, aws_iam_role.sage-maker-who-to-follow-execution-role.arn, aws_iam_role.sage-maker-template-recsys-execution-role.arn]
      },
      {
        Sid    = "DenySecretManager"
        Effect = "Deny"
        Action = [
          "secretsmanager:*",
        ]
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_user" "xxd" {
  name = "xxd"
  tags = {
    "AKIAUN2F6GEILJAG72MB" = "xxd-cli"
  }
}

resource "aws_iam_user_policy_attachment" "xxd-administrator" {
  user       = aws_iam_user.xxd.name
  policy_arn = data.aws_iam_policy.administrator-access.arn
}

locals {
  power-users = ["sergii", "erik", "filipausmaa"]
}

resource "aws_iam_user" "power-user" {
  for_each = toset(local.power-users)
  name     = each.value
}

resource "aws_iam_user_policy_attachment" "allow-power-user-manage-own-password" {
  for_each   = toset(local.power-users)
  user       = aws_iam_user.power-user[each.value].name
  policy_arn = aws_iam_policy.allow-users-to-manage-their-own-credentials-and-mfa-devices.arn
}

resource "aws_iam_user_policy_attachment" "power-user-policy" {
  for_each   = toset(local.power-users)
  user       = aws_iam_user.power-user[each.value].name
  policy_arn = data.aws_iam_policy.power-user-access.arn
}

resource "aws_iam_role" "msk-playground-role" {
  name = "msk-playground-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Sid    = "KafkaAssume"
        Effect = "Allow"
        Principal = {
          Service = "kafkaconnect.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Sid    = "Ec2Assume"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "msk-playground" {
  name = "msk-playground"
  role = aws_iam_role.msk-playground-role.name
}

data "aws_caller_identity" "current" {}

resource "aws_iam_role_policy" "msk-playground-policy" {
  name = "msk-playground-role-policy"
  role = aws_iam_role.msk-playground-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:AlterCluster",
          "kafka-cluster:DescribeCluster"
        ]
        Sid      = "MskClusterPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:cluster/FeatureStoreDev/*"
      },
      {
        Action = [
          "kafka-cluster:*Topic*",
          "kafka-cluster:WriteData",
          "kafka-cluster:ReadData"
        ]
        Sid      = "MskTopicPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:topic/FeatureStoreDev/*"
      },
      {
        Action = [
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DescribeGroup"
        ]
        Sid      = "MskGroupPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:group/FeatureStoreDev/*"
      },
    ]
  })
}

data "aws_kms_key" "modulai-key" {
  provider = aws.frever
  key_id   = "alias/modulai"
}

resource "aws_iam_policy" "allow-access-s3-modulai-bucket-in-frever-aws-account" {
  name        = "allow-access-s3-bucket-in-frever-aws-account"
  description = "Allow access to S3 bucket in frever AWS account"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowS3Access"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:*Lifecycle*",
          "s3:Put*Object*",
          "s3:Get*Object*",
          "s3:Delete*Object*",
        ]
        Resource = [
          "arn:aws:s3:::*-modulai",
          "arn:aws:s3:::modulai-*",
          "arn:aws:s3:::*-modulai/*",
          "arn:aws:s3:::modulai-*/*"
        ]
      },
      {
        Sid    = "AllowDecryptS3Contents"
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = [
          data.aws_kms_key.modulai-key.arn
        ]
      }
    ]
  })
}

resource "aws_iam_policy" "allow-access-s3-content-bucket-in-frever-aws-account" {
  name        = "allow-access-s3-content-bucket-in-frever-aws-account"
  description = "Allow access to S3 content bucket in frever AWS account"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowS3Access"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:Get*Object*",
        ]
        Resource = [
          "arn:aws:s3:::frever-content*",
          "arn:aws:s3:::frever-content*/*",
        ]
      },
    ]
  })
}
