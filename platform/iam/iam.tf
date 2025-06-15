locals {
  eks-clusters = {
    content-prod  = "oidc.eks.eu-central-1.amazonaws.com/id/9BC2EECFE5BDA6BAE0666D54701E5A2A",
    content-stage = "oidc.eks.eu-central-1.amazonaws.com/id/A01450063EA0ADDD801ADF701C18ED27",
    content-test  = "oidc.eks.eu-central-1.amazonaws.com/id/E21A6985A828AABB8923A742C957B12B",
    dev-2         = "oidc.eks.eu-central-1.amazonaws.com/id/F9CD4C83ACCFD1A47C4B23F568F857AB",
    load-test     = "oidc.eks.eu-central-1.amazonaws.com/id/E630379F2C2606EDF3F46D73E192AC14",
    dev           = "oidc.eks.eu-central-1.amazonaws.com/id/BE823FA14947E637022AD48174D47F52",

  }
}

data "aws_caller_identity" "current" {}

data "aws_kms_key" "ssm" {
  key_id = "alias/aws/ssm"
}

data "aws_iam_openid_connect_provider" "eks-irsa" {
  for_each = local.eks-clusters
  url      = "https://${each.value}"
}

data "aws_iam_policy" "AmazonEBSCSIDriverPolicy" {
  name = "AmazonEBSCSIDriverPolicy"
}

data "aws_iam_policy" "AWSElasticBeanstalkWebTier" {
  name = "AWSElasticBeanstalkWebTier"
}

data "aws_iam_policy" "AWSElasticBeanstalkMulticontainerDocker" {
  name = "AWSElasticBeanstalkMulticontainerDocker"
}

data "aws_iam_policy" "AWSElasticBeanstalkWorkerTier" {
  name = "AWSElasticBeanstalkWorkerTier"
}

resource "aws_iam_role" "aws-eks-csi-driver-role" {
  for_each = local.eks-clusters
  name     = "aws-eks-csi-driver-role-cluster-${each.key}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Sid    = "EbsCsiDriverAssume"
        Effect = "Allow"
        Principal = {
          Federated = data.aws_iam_openid_connect_provider.eks-irsa[each.key].arn
        }
        Condition = {
          StringEquals = {
            "${each.value}:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "${each.value}:sub" = "system:serviceaccount:kube-system:ebs-csi-controller-sa"
          }
        }
      }
    ]
  })
  managed_policy_arns = [data.aws_iam_policy.AmazonEBSCSIDriverPolicy.arn]
}

resource "aws_iam_role" "aws-eks-app-role" {
  for_each = local.eks-clusters
  name     = "aws-eks-app-role-cluster-${each.key}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Sid    = "AppServiceRoleAssume"
        Effect = "Allow"
        Principal = {
          Federated = data.aws_iam_openid_connect_provider.eks-irsa[each.key].arn
        }
        Condition = {
          StringEquals = {
            "${each.value}:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "${each.value}:sub" = ["system:serviceaccount:${each.key}*:default", "system:serviceaccount:app*:default"]
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "app-policy" {
  for_each = { for k, v in local.eks-clusters : k => v if k != "load-test" }
  name     = "cluster-${each.key}-app-policy"
  role     = aws_iam_role.aws-eks-app-role[each.key].id

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
        Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${each.key}/*"
      },
      {
        Action   = "kms:Decrypt"
        Sid      = "DecryptSsmParameters"
        Effect   = "Allow"
        Resource = data.aws_kms_key.ssm.arn
      },
      {
        Action   = ["mediaconvert:CreateJob", "mediaconvert:List*", "mediaconvert:Get*", "mediaconvert:Describe*"]
        Sid      = "MediaConvertPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["elastictranscoder:Read*", "elastictranscoder:List*", "elastictranscoder:*Job", "elastictranscoder:*Preset", "elastictranscoder:*Pipeline*"]
        Sid      = "ElasticTranscoderPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["s3:List*", "s3:DeleteObject*", "s3:Get*", "s3:Put*Object*", "s3:RestoreObject"]
        Sid      = "S3Permissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["ses:SendEmail", "ses:SendRawEmail"]
        Sid      = "SesPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["sqs:Change*", "sqs:DeleteMessage*", "sqs:Get*", "sqs:List*", "sqs:ReceiveMessage", "sqs:Send*", "sqs:SetQueueAttributes"]
        Sid      = "SqsPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["sagemaker:InvokeEndpoint*"]
        Sid      = "SageMakerPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["iam:PassRole"]
        Sid      = "AllowPassRoles"
        Effect   = "Allow"
        Resource = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/*media-convert*role"]
      },
      {
        Action   = ["sns:Publish*"]
        Sid      = "SnsPermissions"
        Effect   = "Allow"
        Resource = "arn:aws:sns:*:${data.aws_caller_identity.current.account_id}:${trimprefix(each.key, "content-")}-*"
      },
    ]
  })
}

resource "aws_iam_role" "transcoding-service-role" {
  name = "transcoding-service-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "Ec2Assume"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })

  managed_policy_arns = [data.aws_iam_policy.AWSElasticBeanstalkWebTier.arn, data.aws_iam_policy.AWSElasticBeanstalkMulticontainerDocker.arn, data.aws_iam_policy.AWSElasticBeanstalkWorkerTier.arn]
}

resource "aws_iam_role_policy" "transcoding-service-role-policy" {
  name = "transcoding-service-role-policy"
  role = aws_iam_role.transcoding-service-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = ["elastictranscoder:Read*", "elastictranscoder:List*", "elastictranscoder:*Job", "elastictranscoder:*Preset", "elastictranscoder:*Pipeline*"]
        Sid      = "ElasticTranscoderPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_instance_profile" "transcoding-service" {
  name = "transcoding-service"
  role = aws_iam_role.transcoding-service-role.name
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
    ]
  })
}

resource "aws_iam_role" "jenkins-ec2-role" {
  name        = "JenkinsEC2Role"
  description = "Allows EC2 instances to call AWS services on your behalf."
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "Ec2Assume"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-lambda" {
  name = "Allow-Lambda-Write"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "lambda:CreateFunction",
          "lambda:CreateFunctionUrlConfig",
          "lambda:UpdateFunctionEventInvokeConfig",
          "lambda:DeleteFunctionCodeSigningConfig",
          "lambda:InvokeFunction",
          "lambda:InvokeFunctionUrl",
          "lambda:DeleteProvisionedConcurrencyConfig",
          "lambda:InvokeAsync",
          "lambda:PutFunctionConcurrency",
          "lambda:UpdateAlias",
          "lambda:UpdateFunctionCode",
          "lambda:PutProvisionedConcurrencyConfig",
          "lambda:DeleteAlias",
          "lambda:PutFunctionEventInvokeConfig",
          "lambda:DeleteFunctionEventInvokeConfig",
          "lambda:DeleteFunction",
          "lambda:PublishVersion",
          "lambda:DeleteFunctionConcurrency",
          "lambda:DeleteFunctionUrlConfig",
          "lambda:CreateAlias",
          "lambda:*FunctionConfiguration",
        ]
        Sid      = "AllowLambdaWrite"
        Effect   = "Allow"
        Resource = "arn:aws:lambda:*:722913253728:function:*"
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-ecr" {
  name = "EcrPutImage"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Sid      = "AllowGetAndPutImage"
        Effect   = "Allow"
        Resource = "arn:aws:ecr:*:722913253728:repository/*"
      },
      {
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Sid      = "AllowGetAuthToken"
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-elb" {
  name = "ElbPermissions"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "elasticloadbalancing:DescribeListeners",
          "elasticloadbalancing:DescribeRules",
          "elasticloadbalancing:CreateRule",
          "elasticloadbalancing:ModifyRule",
          "elasticloadbalancing:SetRulePriorities",
        ]
        Sid      = "AllowManipulateELB"
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-s3" {
  name = "S3Permissions"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Sid      = "S3AllowManipulateObject"
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-ci-db-backups/*", "arn:aws:s3:::builddev/*"]
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-s3-for-env-cloning" {
  name = "S3EnvCloning"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:GetObject",
          "s3:GetObjectTagging",
          "s3:DeleteObject",
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:PutObjectVersionAcl",
          "s3:PutObjectTagging",
        ]
        Sid      = "EnvCloningS3ReadWrite"
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-content-stage/*", "arn:aws:s3:::frever-dev/*", "arn:aws:s3:::frever-dev-testclone/*"]
      },
      {
        Action = [
          "s3:GetObject",
          "s3:GetObjectTagging",
        ]
        Sid      = "EnvCloningS3ReadOnly"
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-content/*"]
      },
      {
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:*Lifecycle*",
        ]
        Sid      = "EnvCloningS3List"
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-content-stage", "arn:aws:s3:::frever-dev", "arn:aws:s3:::frever-dev-testclone", "arn:aws:s3:::frever-content"]
      },
    ]
  })
}

resource "aws_iam_role_policy" "jenkins-role-policy-eks" {
  name = "EksPermissions"
  role = aws_iam_role.jenkins-ec2-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "eks:ListFargateProfiles",
          "eks:DescribeNodegroup",
          "eks:ListNodegroups",
          "eks:ListUpdates",
          "eks:AccessKubernetesApi",
          "eks:ListAddons",
          "eks:DescribeCluster",
          "eks:DescribeAddonVersions",
          "eks:ListClusters",
          "eks:ListIdentityProviderConfigs",
          "iam:ListRoles"
        ]
        Sid      = "EksAllow"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "ssm:GetParameter",
        ]
        Sid      = "ssmAllow"
        Effect   = "Allow"
        Resource = "arn:aws:ssm:*:722913253728:parameter/*"
      },
    ]
  })
}

resource "aws_iam_user" "jenkins-mac-build-instance" {
  name = "jenkins-mac"
  tags = {
    used-by              = "jenkins"
    AKIA2QUH43FQAJ56YVIE = "Access key used to run on Jenkins Mac build machines"
  }
}

resource "aws_iam_access_key" "jenkins-mac-access-key" {
  user = aws_iam_user.jenkins-mac-build-instance.name
}

resource "aws_iam_user_policy" "jenkins-mac-build-policy-s3" {
  name = "S3Permissions"
  user = aws_iam_user.jenkins-mac-build-instance.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:*Lifecycle*",
        ]
        Sid      = "S3AllowListBucket"
        Effect   = "Allow"
        Resource = "arn:aws:s3:::builddev"
      },
      {
        Action = [
          "s3:Put*Object*",
          "s3:Get*Object*",
          "s3:Delete*Object*",
        ]
        Sid      = "S3AllowManipulateObject"
        Effect   = "Allow"
        Resource = "arn:aws:s3:::builddev/*"
      },
    ]
  })
}

resource "aws_iam_policy" "eks-read" {
  name        = "eks-read"
  description = "Allow users to read information about EKS cluster"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEksRead"
        Effect = "Allow"
        Action = [
          "eks:Describe*"
        ]
        Resource = "*"
      },
    ]
  })
}

# https://github.com/kubernetes/autoscaler/blob/master/cluster-autoscaler/cloudprovider/aws/README.md
resource "aws_iam_policy" "eks-full-cluster-auto-scaler" {
  name        = "eks-full-cluster-auto-scaler"
  description = "Permissions required when using ASG Autodiscovery and Dynamic EC2 List Generation (the default behaviour)."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EksAutoscaler"
        Effect = "Allow"
        Action = [
          "autoscaling:DescribeAutoScalingGroups",
          "autoscaling:DescribeAutoScalingInstances",
          "autoscaling:DescribeLaunchConfigurations",
          "autoscaling:DescribeScalingActivities",
          "autoscaling:DescribeTags",
          "autoscaling:SetDesiredCapacity",
          "autoscaling:TerminateInstanceInAutoScalingGroup",
          "ec2:DescribeInstanceTypes",
          "ec2:DescribeLaunchTemplateVersions",
          "ec2:DescribeImages",
          "ec2:GetInstanceTypesFromInstanceRequirements",
          "eks:DescribeNodegroup"
        ]
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_policy" "backend-dev-policy" {
  name        = "frever-backend-dev-policy"
  description = "Additional permissions for backend developers."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EC2InstanceConnect"
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances",
          "ec2-instance-connect:SendSSHPublicKey",
          "ec2-instance-connect:OpenTunnel",
        ]
        Resource = "*"
      },
      {
        Sid    = "S3FullAccess"
        Effect = "Allow"
        Action = [
          "s3:*",
          "s3-object-lambda:*"
        ]
        Resource = "*"
      },
      {
        Sid    = "MediaConvertFullAccess"
        Effect = "Allow"
        Action = [
          "mediaconvert:*",
        ]
        Resource = "*"
      },
      {
        Sid    = "EventBridgeFullAccess"
        Effect = "Allow"
        Action = [
          "events:*",
        ]
        Resource = "*"
      }
    ]
  })
}
