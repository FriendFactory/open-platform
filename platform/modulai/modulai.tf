locals {
  modulai-repos = ["personalisation-lightfm-pipeline-datacollection", "personalisation-lightfm-pipeline-dataprocessing", "personalisation-lightfm-pipeline-featureengineering", "personalisation-lightfm-pipeline-train", "personalisation-lightfm-pipeline-inference", "personalisation-lightfm-pipeline-evaluation", "personalisation-lightfm-pipeline-filtering", "personalisation-lightfm-api", "personalisation-lightgbm-api", "personalisation-lightgbm-pipeline-datacollection", "personalisation-lightgbm-pipeline-dataprocessing", "personalisation-lightgbm-pipeline-train", "personalisation-lightgbm-pipeline-inference", "personalisation-lightgbm-pipeline-evaluation", "videoquality-lightgbm-pipeline-dataprocessing", "videoquality-lightgbm-pipeline-datacollection", "videoquality-lightgbm-pipeline-train", "personalisation-ab-testing-assignment", ]
  emails        = ["frever-modulai-alerts-aaaah4zjx43ukvx2x4tvqpagzu@ffextendedworkspace.slack.com"]
  pipelines     = ["prod-lightgbm-pipeline", "prod-pipeline-recommendations"]
}

resource "aws_iam_group" "modulai" {
  name = "modulai"
}

resource "aws_iam_group_policy" "modulai-policy" {
  name  = "modulai-policy"
  group = aws_iam_group.modulai.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid = "ListModulaiS3Buckets"
        Action = [
          "s3:ListBucket",
          "s3:GetBucket*",
          "s3:*Lifecycle*",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1"]
      },
      {
        Sid = "AccessObjectsInModulaiS3Buckets"
        Action = [
          "s3:Put*Object*",
          "s3:Get*Object*",
          "s3:Delete*Object*",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai/*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1/*"]
      },
      {
        Sid = "ListModulaiInstances"
        Action = [
          "ec2:DescribeInstances"
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Sid = "UseModulaiEC2Instances"
        Action = [
          "ec2:StartInstances",
          "ec2:StopInstances",
          "ec2:RebootInstances",
        ]
        Effect   = "Allow"
        Resource = [for k, v in data.aws_instance.modulai-instance : v.arn]
      },
      {
        Sid    = "PermissionToPassARole"
        Effect = "Allow"
        Action = [
          "iam:PassRole"
        ]
        Resource = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${aws_iam_role.modulai-lambda-execution-role.name}", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/service-role/AmazonSageMaker-ExecutionRole*", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/AmazonSageMaker-ExecutionRole*"]
      },
      {
        Sid    = "PermissionToListRoles"
        Effect = "Allow"
        Action = [
          "iam:ListRoles"
        ]
        Resource = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/"
      },
      {
        Sid    = "PermissionsToViewFunctionsInConsole"
        Effect = "Allow"
        Action = [
          "lambda:ListFunctions",
          "lambda:GetAccountSettings"
        ]
        Resource = "*"
      },
      {
        Sid    = "CreateAndUpdateLambdaFunction"
        Effect = "Allow"
        Action = [
          "lambda:AddPermission",
          "lambda:RemovePermission",
          "lambda:DeleteAlias",
          "lambda:PutFunctionConcurrency",
          "lambda:DeleteFunctionConcurrency",
          "lambda:PublishVersion",
          "lambda:DeleteFunction",
          "lambda:InvokeFunction",
          "lambda:Create*",
          "lambda:Get*",
          "lambda:Update*",
          "lambda:List*",
        ]
        Resource = "arn:aws:lambda:*:${data.aws_caller_identity.current.account_id}:function:modulai*"
      },
      {
        Sid    = "DevelopEventSourceMappings"
        Effect = "Allow"
        Action = [
          "lambda:CreateEventSourceMapping",
          "lambda:UpdateEventSourceMapping",
          "lambda:DeleteEventSourceMapping"
        ]
        Resource = "*"
        Condition = {
          "StringEquals" : {
            "lambda:FunctionArn" = "arn:aws:lambda:*:${data.aws_caller_identity.current.account_id}:function:modulai*"
          }
        }
      },
      {
        Sid    = "TagAndEventSourceMappings"
        Effect = "Allow"
        Action = [
          "lambda:UntagResource",
          "lambda:TagResource",
          "lambda:ListTags",
          "lambda:GetEventSourceMapping",
          "lambda:ListEventSourceMappings"
        ]
        Resource = "*"
      },
      {
        Sid    = "PermissionForCloudWatchMetrics"
        Effect = "Allow"
        Action = [
          "cloudwatch:ListMetrics",
          "cloudwatch:ListMetricStreams",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:GetMetricData"
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowAllSageMaker"
        Effect = "Allow"
        Action = [
          "sagemaker:*",
          "codecommit:ListRepositories",
          "secretsmanager:ListSecrets",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DescribeSecurityGroups",
          "ec2:DescribeSubnets",
          "ec2:DescribeVpcs",
          "ec2:CreateNetworkInterface",
          "servicecatalog:ListAcceptedPortfolioShares",
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowAllSageMakerLogs"
        Effect = "Allow"
        Action = [
          "logs:DescribeLogGroups",
          "logs:DescribeLogStreams",
          "logs:GetLogEvents"
        ]
        Resource = ["arn:aws:logs:eu-central-1:722913253728:log-group::log-stream:*", "arn:aws:logs:eu-central-1:722913253728:log-group:/aws/sagemaker/*"]
      },
      {
        Sid    = "AllowKmsList"
        Effect = "Allow"
        Action = [
          "kms:ListAliases"
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowEcrGetAuthToken"
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowSecretManager"
        Effect = "Allow"
        Action = [
          "secretsmanager:*",
        ]
        Resource = "arn:aws:secretsmanager:*:${data.aws_caller_identity.current.account_id}:*-modulai-*"
      },
      {
        Sid    = "AllowEventBridge"
        Effect = "Allow"
        Action = [
          "events:TestEventPattern",
          "events:Describe*",
          "events:List*",
          "events:*Rule",
          "events:*Targets",
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_user_policy" "modulai-ecr-policy" {
  depends_on = [aws_ecr_repository.modulai-ecr]
  name       = "modulai-ecr-policy"
  user       = aws_iam_user.frever-modulai.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEcrManageImages"
        Effect = "Allow"
        Action = [
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:BatchDeleteImage",
          "ecr:Describe*",
          "ecr:Get*",
          "ecr:List*"
        ]
        Resource = values(aws_ecr_repository.modulai-ecr).*.arn
      },
    ]
  })
}

resource "aws_iam_user" "frever-modulai" {
  name = "frever-modulai"

  tags = {
    used-by = "modulai"
  }
}

resource "aws_iam_user_group_membership" "modulai" {
  user = aws_iam_user.frever-modulai.name
  groups = [
    aws_iam_group.modulai.name
  ]
}

resource "aws_iam_role" "modulai-lambda-execution-role" {
  name = "modulai-lambda-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "LambdaAssume"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      },
    ]
  })

  managed_policy_arns = [data.aws_iam_policy.AwsManagedAWSLambdaExecutePolicy.arn]

  tags = {
    used-by = "modulai"
  }
}

resource "aws_iam_role_policy" "modulai-lambda-s3-acccess" {
  name = "modulai-lambda-s3-acccess"
  role = aws_iam_role.modulai-lambda-execution-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = "arn:aws:s3:::*modulai"
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = "arn:aws:s3:::*modulai/*"
      },
    ]
  })
}

resource "aws_iam_role" "modulai-sagemaker-execution-role" {
  name = "AmazonSageMaker-ExecutionRole-modulai"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "SageMakerAssume"
        Principal = {
          Service = "sagemaker.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = "EventBridgeAssume"
        Principal = {
          Service = "events.amazonaws.com"
        }
      },
    ]
  })

  managed_policy_arns = [data.aws_iam_policy.AwsManagedAmazonSageMakerFullAccess.arn]

  tags = {
    used-by = "modulai"
  }
}

resource "aws_iam_role_policy" "modulai-sagemaker-acccess" {
  name = "modulai-sagemaker-acccess"
  role = aws_iam_role.modulai-sagemaker-execution-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1"]
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai/*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1/*"]
      },
      {
        Sid    = "AllowEcrPullImages"
        Effect = "Allow"
        Action = [
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:Describe*",
          "ecr:Get*",
          "ecr:List*"
        ]
        Resource = values(aws_ecr_repository.modulai-ecr).*.arn
      },
      {
        Action = [
          "ssm:GetParameterHistory",
          "ssm:GetParametersByPath",
          "ssm:GetParameters",
          "ssm:GetParameter",
        ]
        Sid      = "UseSsmParameters"
        Effect   = "Allow"
        Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/feed-recsys/*"
      },
    ]
  })
}

data "aws_instances" "modulai-instances" {
  instance_tags = {
    Name = "*modulai*"
  }
  instance_state_names = ["running", "stopped"]
}

data "aws_instance" "modulai-instance" {
  for_each    = toset(data.aws_instances.modulai-instances.ids)
  instance_id = each.key
}

data "aws_iam_policy" "AwsManagedAWSLambdaExecutePolicy" {
  name = "AWSLambdaExecute"
}

data "aws_iam_policy" "AwsManagedAmazonSageMakerFullAccess" {
  name = "AmazonSageMakerFullAccess"
}

data "aws_caller_identity" "current" {}

resource "aws_ecr_repository" "modulai-ecr" {
  for_each             = toset(local.modulai-repos)
  name                 = each.key
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "lifecycle-policy" {
  depends_on = [aws_ecr_repository.modulai-ecr]
  for_each   = toset(local.modulai-repos)
  repository = each.key
  policy     = file("lifecycle-policy.json")
}

resource "aws_kms_key" "modulai" {
}

resource "aws_kms_alias" "modulai" {
  name          = "alias/modulai"
  target_key_id = aws_kms_key.modulai.key_id
}

resource "aws_kms_key_policy" "modulai-key-policy" {
  key_id = aws_kms_key.modulai.id
  policy = jsonencode({
    Id = "key-default-1"
    Statement = [
      {
        Action = "kms:*"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::722913253728:root"
        }

        Resource = "*"
        Sid      = "Enable IAM User Permissions"
      },
      {
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Effect = "Allow"
        Principal = {
          AWS = "*"
        }

        Resource = "*"
        Sid      = "Allow cross account s3 object decrypt from machine-learning account"
        Condition = {
          "StringEquals" : {
            "kms:ViaService"    = "s3.eu-central-1.amazonaws.com"
            "kms:CallerAccount" = "304552489232"
          }
        }
      },
    ]
    Version = "2012-10-17"
  })
}

resource "aws_s3_bucket" "modulai-sagemaker-studio-notebook-sharing" {
  bucket = "modulai-sagemaker-studio"
}

resource "aws_s3_bucket_acl" "modulai-sagemaker-studio-notebook-sharing-s3-acl" {
  bucket = aws_s3_bucket.modulai-sagemaker-studio-notebook-sharing.id
  acl    = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "modulai-sagemaker-studio-notebook-sharing-s3-lifecycle" {
  bucket = aws_s3_bucket.modulai-sagemaker-studio-notebook-sharing.id

  rule {
    id = "modulai-sagemaker-studio-notebook-sharing-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.modulai-sagemaker-studio-notebook-sharing.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "modulai-github-actions-role" {
  name = "modulai-github-actions-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Effect = "Allow"
        Sid    = "GithubAssume"
        Principal = {
          Federated = data.aws_iam_openid_connect_provider.github.arn
        }
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = ["repo:FriendFactory/feed-recsys:*"]
          }
        }
      },
    ]
  })

  tags = {
    used-by = "modulai"
  }
}

resource "aws_iam_role_policy" "modulai-github-actions-role-acccess" {
  depends_on = [aws_ecr_repository.modulai-ecr]
  name       = "modulai-github-actions-role-acccess"
  role       = aws_iam_role.modulai-github-actions-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowEcrGetAuthToken"
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
        ]
        Resource = "*"
      },
      {
        Sid    = "AllowPushPull"
        Effect = "Allow"
        Action = [
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart"
        ]
        Resource = values(aws_ecr_repository.modulai-ecr).*.arn
      },
      {
        Sid    = "AllowUseSageMakerEcr"
        Effect = "Allow"
        Action = [
          "ecr:Describe*",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = ["arn:aws:ecr:eu-central-1:763104351884:repository/pytorch-training"]
      },
      {
        Sid    = "AllowSageMaker"
        Effect = "Allow"
        Action = [
          "sagemaker:CreateAutoMLJob",
          "sagemaker:CreateCompilationJob",
          "sagemaker:CreateFlowDefiniton",
          "sagemaker:CreateHyperParameterTuningJob",
          "sagemaker:CreateModel",
          "sagemaker:CreateProcessingJob",
          "sagemaker:CreateTrainingJob",
          "sagemaker:*Pipeline*"
        ]
        Resource = "*"
      },
      {
        Sid    = "PermissionToPassARole"
        Effect = "Allow"
        Action = [
          "iam:PassRole"
        ]
        Resource = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${aws_iam_role.modulai-lambda-execution-role.name}", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/service-role/AmazonSageMaker-ExecutionRole*", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/AmazonSageMaker-ExecutionRole*", "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/*/modulai-github-actions*"]
      },
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1"]
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::*modulai/*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1/*"]
      },
    ]
  })
}

resource "aws_cloudwatch_event_rule" "notifySlackWhenPersonalisationLightgbmApiImagePushed" {
  name          = "notifySlackWhenPersonalisationLightgbmApiImagePushed"
  description   = "Notify through Slack when personalisation-lightgbm-api:api_latest docker image tag pushed to ECR repo"
  event_pattern = <<EOF
{
  "source": ["aws.ecr"],
  "detail-type": ["ECR Image Action"],
  "detail": {
    "action-type": ["PUSH"],
    "result": ["SUCCESS"],
    "repository-name": ["personalisation-lightgbm-api"],
    "image-tag": ["api_latest"]
  }
}
EOF
}

data "aws_sns_topic" "platform-messages" {
  name = "platform-messages"
}

resource "aws_cloudwatch_event_target" "sns" {
  rule      = aws_cloudwatch_event_rule.notifySlackWhenPersonalisationLightgbmApiImagePushed.name
  target_id = "SendToSNS"
  arn       = data.aws_sns_topic.platform-messages.arn
}

resource "aws_cloudwatch_event_rule" "startSageMakerPipelineWhenImagePushed" {
  name          = "recommenders-pipeline-rule"
  description   = "Start SageMaker pipeline when prod_filter_* docker image tag pushed to ECR repo"
  event_pattern = <<EOF
{
  "source": ["aws.ecr"],
  "detail-type": ["ECR Image Action"],
  "detail": {
    "action-type": ["PUSH"],
    "result": ["SUCCESS"],
    "repository-name": ["frever-modulai-feed-ml"],
    "image-tag": [{"prefix": "prod_filter_" }]
  }
}
EOF
}

resource "aws_sns_topic" "modulai-alerts" {
  name = "modulai-alerts"
}

resource "aws_sns_topic_policy" "modulai-alerts-sns-topic-policy" {
  arn = aws_sns_topic.modulai-alerts.arn

  policy = data.aws_iam_policy_document.sns-topic-policy.json
}

data "aws_iam_policy_document" "sns-topic-policy" {
  policy_id = "__default_policy_ID"

  statement {
    actions = [
      "SNS:Publish",
    ]

    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = sort(["events.amazonaws.com", "cloudwatch.amazonaws.com"])
    }

    resources = [
      aws_sns_topic.modulai-alerts.arn,
    ]

    sid = "__default_statement_ID"
  }
}

resource "aws_cloudwatch_metric_alarm" "sagemaker-pipeline-execution" {
  for_each            = toset(local.pipelines)
  alarm_name          = "${each.key}-execution-failed"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = "ExecutionFailed"
  namespace           = "AWS/Sagemaker/ModelBuildingPipeline"
  statistic           = "Average"
  period              = "60"
  threshold           = "1"
  treat_missing_data  = "ignore"
  dimensions = {
    PipelineName = each.key
  }
  alarm_description = "This metric monitors the execution status for SageMaker ${each.key}"
  alarm_actions     = [aws_sns_topic.modulai-alerts.arn]
  ok_actions        = [aws_sns_topic.modulai-alerts.arn]
}

resource "aws_sns_topic_subscription" "sns-topic" {
  for_each  = toset(local.emails)
  topic_arn = aws_sns_topic.modulai-alerts.arn
  protocol  = "email"
  endpoint  = each.key
}

