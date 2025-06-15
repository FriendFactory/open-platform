resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  client_id_list = [
    "sts.amazonaws.com",
  ]

  // https://github.com/aws-actions/configure-aws-credentials
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1", "1c58a3a8518e8759bf075b76b750d4f2df264fcd"]
}

locals {
  projects = {
    # "template-recommendation" = {
    #   repos   = ["repo:FriendFactory/template-recsys:*", "repo:FriendFactory/template-recsys-pipeline:*"],
    #   used-by = "template-recsys and template-recsys-pipeline repos"
    # },
    # "follow-recommendation" = {
    #   repos   = ["repo:FriendFactory/WhoToFollow:*", "repo:FriendFactory/WhoToFollow-pipeline:*"],
    #   used-by = "WhoToFollow and WhoToFollow-pipeline repos"
    # },
    # "creator-recommendation" = {
    #   repos   = ["repo:FriendFactory/creator-recsys:*"],
    #   used-by = "creator-recsys repo"
    # },
    # "crew" = {
    #   repos   = ["repo:FriendFactory/crew:*"],
    #   used-by = "crew repo"
    # },
    "video-gen" = {
      repos   = ["repo:FriendFactory/video-gen:*"],
      used-by = "video-gen repo"
    },
    # "feed-recsys" = {
    #   repos   = ["repo:FriendFactory/feed-recsys:*"],
    #   used-by = "feed-recsys repo"
    # },
  }
}

resource "aws_iam_role" "project-github-actions-role" {
  for_each = local.projects
  name     = "${each.key}-github-actions-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Effect = "Allow"
        Sid    = "GithubAssume"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = each.value.repos
          }
        }
      },
    ]
  })
  tags = {
    used-by = each.value.used-by
  }
}

resource "aws_iam_role_policy_attachment" "attach-policy" {
  for_each   = local.projects
  role       = aws_iam_role.project-github-actions-role[each.key].name
  policy_arn = aws_iam_policy.machine-learning-github-actions-role-acccess.arn
}

resource "aws_iam_policy" "machine-learning-github-actions-role-acccess" {
  name        = "machine-learning-github-actions-role-acccess"
  description = "Permissions for Github actions in Github repos of Machine-Learning related services."

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
        Resource = "arn:aws:ecr:*:${data.aws_caller_identity.current.account_id}:repository/*"
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
        Resource = [aws_iam_role.sage-maker-template-recsys-execution-role.arn, aws_iam_role.sage-maker-who-to-follow-execution-role.arn, "arn:aws:iam::304552489232:role/*-task-execution-role"]
      },
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
        Sid    = "AllowUpdateEcs"
        Effect = "Allow"
        Action = [
          "ecs:UpdateService",
        ]
        Resource = ["arn:aws:ecs:eu-central-1:${data.aws_caller_identity.current.account_id}:service/machine-learning/*"]
      },
    ]
  })
}

