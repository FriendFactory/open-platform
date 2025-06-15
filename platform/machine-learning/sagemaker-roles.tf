data "aws_iam_policy" "AwsManagedAmazonSageMakerFullAccess" {
  name = "AmazonSageMakerFullAccess"
}

resource "aws_iam_policy" "machine-learning-sage-maker-pipeline-policy" {
  name        = "machine-learning-sage-maker-pipeline-policy"
  description = "Permissions for Machine-Learning SageMaker pipelines."

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-machine-learning*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1"]
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-machine-learning*/*", "arn:aws:s3:::jumpstart-cache-prod-eu-central-1/*"]
      },
      {
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:AlterCluster",
          "kafka-cluster:DescribeCluster"
        ]
        Sid      = "MskClusterPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:cluster/*/*"
      },
      {
        Action = [
          "kafka-cluster:*Topic*",
          "kafka-cluster:WriteData",
          "kafka-cluster:ReadData"
        ]
        Sid      = "MskTopicPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:topic/*/*"
      },
      {
        Action = [
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DescribeGroup"
        ]
        Sid      = "MskGroupPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:group/*/*"
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
        Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/msk/*"
      },
    ]
  })
}

resource "aws_iam_role" "sage-maker-template-recsys-execution-role" {
  name = "sage-maker-template-recsys-execution-role"

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

  managed_policy_arns = [data.aws_iam_policy.AwsManagedAmazonSageMakerFullAccess.arn, aws_iam_policy.machine-learning-sage-maker-pipeline-policy.arn]
}

resource "aws_iam_role" "sage-maker-who-to-follow-execution-role" {
  name = "sage-maker-who-to-follow-execution-role"

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

  managed_policy_arns = [data.aws_iam_policy.AwsManagedAmazonSageMakerFullAccess.arn, aws_iam_policy.machine-learning-sage-maker-pipeline-policy.arn]
}

