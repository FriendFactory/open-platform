data "aws_caller_identity" "current" {}

data "aws_iam_policy" "read-only-access" {
  arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_group" "security-audit" {
  name = "security-audit"
}

resource "aws_iam_group_policy" "modulai-policy" {
  name  = "security-audit-policy"
  group = aws_iam_group.security-audit.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowViewAccountInfo"
        Effect = "Allow"
        Action = [
          "iam:GetAccountPasswordPolicy",
          "iam:GetAccountSummary"
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
    ]
  })
}

resource "aws_iam_group_policy_attachment" "attach-read-only-access" {
  group      = aws_iam_group.security-audit.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_user" "frever-sentor" {
  name = "frever-sentor"

  tags = {
    used-by = "sentor"
  }
}

resource "aws_iam_group_membership" "sentor" {
  name  = "sentor-group-membership"
  users = [aws_iam_user.frever-sentor.name]
  group = aws_iam_group.security-audit.name
}
