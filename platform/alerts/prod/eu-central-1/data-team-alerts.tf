locals {
  // data-alerts slack channel email address
  data-team-alerts-emails = ["xie.xiaodong@frever.com", "filip.ausmaa@frever.com", "v0p0k9o3l8f8z2m2@ffextendedworkspace.slack.com", "sergii.tokariev@frever.com"]
  lambda_names            = [for name in data.aws_lambda_functions.all.function_names : name if startswith(name, "analytics")]
}

data "aws_lambda_functions" "all" {}

resource "aws_sns_topic" "data-alerts" {
  name = "data-alerts"
}

resource "aws_sns_topic_policy" "data-alerts-policy" {
  arn = aws_sns_topic.data-alerts.arn

  policy = data.aws_iam_policy_document.data-alerts-iam-policy.json
}

data "aws_iam_policy_document" "data-alerts-iam-policy" {
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
      aws_sns_topic.data-alerts.arn,
    ]

    sid = "__default_statement_ID"
  }
}

resource "aws_sns_topic_subscription" "data-alerts-sns-topic-subscription" {
  for_each  = toset(local.data-team-alerts-emails)
  topic_arn = aws_sns_topic.data-alerts.arn
  protocol  = "email"
  endpoint  = each.key
}

resource "aws_cloudwatch_metric_alarm" "lambda-errors" {
  for_each            = toset(local.lambda_names)
  alarm_name          = "Lambda-${each.key}-Error"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  statistic           = "Sum"
  period              = "60"
  threshold           = "0"
  treat_missing_data  = "ignore"
  dimensions = {
    FunctionName = each.key
  }
  alarm_description = "Lambda: ${each.key} error alarm"
  alarm_actions     = [aws_sns_topic.data-alerts.arn]
  ok_actions        = [aws_sns_topic.data-alerts.arn]
}

# resource "aws_cloudwatch_metric_alarm" "lambda-async-events-dropped" {
#   for_each            = toset(local.lambda_names)
#   alarm_name          = "Lambda-${each.key}-AsyncEventsDropped"
#   comparison_operator = "GreaterThanThreshold"
#   evaluation_periods  = "1"
#   metric_name         = "Errors"
#   namespace           = "AWS/Lambda"
#   statistic           = "Sum"
#   period              = "60"
#   threshold           = "0"
#   treat_missing_data  = "ignore"
#   dimensions = {
#     FunctionName = each.key
#   }
#   alarm_description = "Lambda: ${each.key} AsyncEventsDropped alarm"
#   alarm_actions     = [aws_sns_topic.data-alerts.arn]
#   ok_actions        = [aws_sns_topic.data-alerts.arn]
# }

