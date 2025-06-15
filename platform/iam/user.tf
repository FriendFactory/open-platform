# resource "aws_iam_user" "stefanos_antaris" {
#   name = "stefanos_antaris"
# }
#
# resource "aws_iam_user_group_membership" "stefanos_antaris" {
#   user = aws_iam_user.stefanos_antaris.name
#   groups = [
#     "modulai"
#   ]
# }

# resource "aws_iam_user_policy" "machine-learning-policy" {
#   name = "machine-learning"
#   user = aws_iam_user.stefanos_antaris.name
#   policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Sid    = "AllowEcrManageImages"
#         Effect = "Allow"
#         Action = [
#           "ecr:CompleteLayerUpload",
#           "ecr:InitiateLayerUpload",
#           "ecr:PutImage",
#           "ecr:UploadLayerPart",
#           "ecr:BatchCheckLayerAvailability",
#           "ecr:BatchGetImage",
#           "ecr:BatchDeleteImage",
#           "ecr:Describe*",
#           "ecr:Get*",
#           "ecr:List*"
#         ]
#         Resource = ["arn:aws:ecr:eu-central-1:722913253728:repository/videoquality*", "arn:aws:ecr:eu-central-1:722913253728:repository/personalisation*"]
#       },
#       {
#         Sid = "S3BucketRead"
#         Action = [
#           "s3:ListBucket",
#           "s3:GetBucket*",
#         ]
#         Effect   = "Allow"
#         Resource = ["arn:aws:s3:::frever-content*"]
#       },
#       {
#         Sid    = "S3ObjectRead"
#         Effect = "Allow"
#         Action = [
#           "s3:Get*Object*",
#         ]
#         Resource = ["arn:aws:s3:::frever-content*/*"]
#       },
#       {
#         Action   = "ssm:DescribeParameters"
#         Sid      = "DescribeParameters"
#         Effect   = "Allow"
#         Resource = "*"
#       },
#       {
#         Action = [
#           "ssm:GetParameterHistory",
#           "ssm:GetParametersByPath",
#           "ssm:GetParameters",
#           "ssm:GetParameter",
#         ]
#         Sid      = "UseSsmParameters"
#         Effect   = "Allow"
#         Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/feed-recsys/*"
#       },
#     ]
#   })
# }

# resource "aws_iam_user_policy_attachment" "allow-stefanos_antaris-manage-own-password" {
#   user       = aws_iam_user.stefanos_antaris.name
#   policy_arn = aws_iam_policy.allow-users-to-manage-their-own-credentials-and-mfa-devices.arn
# }

locals {
  music_reporting_user_names = ["wcm"]
}

resource "aws_iam_user" "frever-music-reporting" {
  for_each = toset(local.music_reporting_user_names)
  name     = "frever-music-reporting-${each.key}"
}

resource "aws_iam_user_policy" "frever-music-reporting-policy" {
  for_each = toset(local.music_reporting_user_names)
  name     = "frever-music-reporting-policy-user-${each.key}"
  user     = aws_iam_user.frever-music-reporting[each.key].name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid = "S3BucketRead"
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:s3:::frever-music-reporting"]
        Condition = {
          StringLike = {
            "s3:prefix" : [
              "${each.key}",
              "${each.key}/*",
            ]
          }
        }
      },
      {
        Sid    = "S3ObjectRead"
        Effect = "Allow"
        Action = [
          "s3:Get*Object*",
        ]
        Resource = ["arn:aws:s3:::frever-music-reporting/${each.key}/*"]
      },
    ]
  })
}
