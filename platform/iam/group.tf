resource "aws_iam_group" "billing" {
  name = "billing"
}

resource "aws_iam_group_policy_attachment" "manage-own-credential" {
  group      = aws_iam_group.billing.name
  policy_arn = aws_iam_policy.allow-users-to-manage-their-own-credentials-and-mfa-devices.arn
}

data "aws_iam_policy" "billing" {
  arn = "arn:aws:iam::aws:policy/job-function/Billing"
}

resource "aws_iam_group_policy_attachment" "billing" {
  group      = aws_iam_group.billing.name
  policy_arn = data.aws_iam_policy.billing.arn
}

resource "aws_iam_group_policy" "billing-policy" {
  name  = "billing-policy"
  group = aws_iam_group.billing.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid = "BillingAndCostManagementDataExportsReadWrite"
        Action = [
          "bcm-data-exports:CreateExport",
          "bcm-data-exports:GetTable",
          "bcm-data-exports:GetExecution",
          "bcm-data-exports:ListExecutions",
          "bcm-data-exports:GetExport",
          "bcm-data-exports:UpdateExport",
        ]
        Effect   = "Allow"
        Resource = ["arn:aws:bcm-data-exports:*:722913253728:table/*", "arn:aws:bcm-data-exports:*:722913253728:export/*"]
      },
      {
        Sid = "BillingAndCostManagementDataExportsList"
        Action = [
          "bcm-data-exports:ListExports",
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Sid = "stsAssume"
        Action = [
          "sts:AssumeRole",
        ]
        Effect   = "Allow"
        Resource = "arn:aws:iam::832386496210:role/frever-cross-account-access"
      },
    ]
  })
}
