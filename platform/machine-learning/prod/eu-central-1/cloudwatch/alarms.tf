resource "aws_sns_topic" "ml-alerts" {
  name = "ml-alerts"
}

resource "aws_sns_topic_policy" "ml-alerts-policy" {
  arn = aws_sns_topic.ml-alerts.arn

  policy = data.aws_iam_policy_document.sns-topic-policy.json
}

data "aws_iam_policy_document" "sns-topic-policy" {
  policy_id = "ml-alerts-policy"

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
      aws_sns_topic.ml-alerts.arn,
    ]

    sid = "ml-alerts-policy"
  }
}

locals {
  emails = ["x@frever.com", "ml-alerts-aaaajuetkoipmq62wnng7cynn4@ffextendedworkspace.slack.com"]
  service_target_groups = {
    video-gen-tg                    = "video-gen",
    video-gen-public-tg             = "video-gen",
    ml-service-tg                   = "ml-service"
    ml-service-public-tg            = "ml-service"
  }
  request_error_rate_threshold = 3
  response_time_threshold      = 5
  cpu_utilization_threshold    = 70
  memory_utilization_threshold = 70
}

resource "aws_sns_topic_subscription" "sns-topic" {
  for_each  = toset(local.emails)
  topic_arn = aws_sns_topic.ml-alerts.arn
  protocol  = "email"
  endpoint  = each.key
  filter_policy = each.key == "alerts@frever.com" ? jsonencode(
    {
      debug = [
        {
          exists = false
        },
      ]
    }
  ) : null
}

data "aws_lb_target_group" "ml-service-target-groups" {
  for_each = toset(keys(local.service_target_groups))
  name     = each.key
}

data "aws_lb" "machine-learning-prod" {
  name = "machine-learning-prod"
}

data "aws_lb" "machine-learning-prod-public" {
  name = "machine-learning-prod-public"
}

resource "aws_cloudwatch_metric_alarm" "target-response-time-per-service" {
  for_each            = toset(keys(local.service_target_groups))
  alarm_name          = "response-time-too-long-for-${local.service_target_groups[each.key]}-service-from-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  statistic           = "Average"
  period              = "60"
  threshold           = local.response_time_threshold
  treat_missing_data  = "ignore"
  dimensions = {
    LoadBalancer = can(regex("public", each.key)) ? data.aws_lb.machine-learning-prod-public.arn_suffix : data.aws_lb.machine-learning-prod.arn_suffix
    TargetGroup  = data.aws_lb_target_group.ml-service-target-groups[each.key].arn_suffix
  }
  alarm_description = "Response time has exceeded ${local.response_time_threshold} for target ${each.key}"
  alarm_actions     = [aws_sns_topic.ml-alerts.arn]
  ok_actions        = [aws_sns_topic.ml-alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "too-many-server-errors-per-service" {
  for_each            = toset(keys(local.service_target_groups))
  alarm_name          = "too-many-errors-for-${local.service_target_groups[each.key]}-service-from-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  threshold           = local.request_error_rate_threshold
  alarm_description   = "Request error rate has exceeded ${local.request_error_rate_threshold}% for target ${each.key}"
  treat_missing_data  = "ignore"
  alarm_actions       = [aws_sns_topic.ml-alerts.arn]
  ok_actions          = [aws_sns_topic.ml-alerts.arn]

  metric_query {
    id          = "error_rate"
    expression  = "error_count/request_count*100"
    label       = "Error Rate"
    return_data = "true"
  }

  metric_query {
    id = "request_count"

    metric {
      metric_name = "RequestCount"
      namespace   = "AWS/ApplicationELB"
      period      = "60"
      stat        = "Sum"
      unit        = "Count"

      dimensions = {
        LoadBalancer = can(regex("public", each.key)) ? data.aws_lb.machine-learning-prod-public.arn_suffix : data.aws_lb.machine-learning-prod.arn_suffix
        TargetGroup  = data.aws_lb_target_group.ml-service-target-groups[each.key].arn_suffix
      }
    }
  }

  metric_query {
    id = "error_count"

    metric {
      metric_name = "HTTPCode_Target_5XX_Count"
      namespace   = "AWS/ApplicationELB"
      period      = "60"
      stat        = "Sum"
      unit        = "Count"

      dimensions = {
        LoadBalancer = can(regex("public", each.key)) ? data.aws_lb.machine-learning-prod-public.arn_suffix : data.aws_lb.machine-learning-prod.arn_suffix
        TargetGroup  = data.aws_lb_target_group.ml-service-target-groups[each.key].arn_suffix
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "service-cpu-utilization-high" {
  for_each            = toset(local.services)
  alarm_name          = "service-${each.key}-CPU-Utillization-high"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = local.cpu_utilization_threshold
  dimensions = {
    ServiceName = each.key
    ClusterName = "machine-learning"
  }
  alarm_description  = "Service ${each.key} CPU Utilization has passsed ${local.cpu_utilization_threshold}"
  treat_missing_data = "ignore"
  alarm_actions      = [aws_sns_topic.ml-alerts.arn]
  ok_actions         = [aws_sns_topic.ml-alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "service-memory-utilization-high" {
  for_each            = toset(local.services)
  alarm_name          = "service-${each.key}-Memory-Utillization-high"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = local.memory_utilization_threshold
  dimensions = {
    ServiceName = each.key
    ClusterName = "machine-learning"
  }
  alarm_description  = "Service ${each.key} Memory Utilization has passsed ${local.memory_utilization_threshold}"
  treat_missing_data = "ignore"
  alarm_actions      = [aws_sns_topic.ml-alerts.arn]
  ok_actions         = [aws_sns_topic.ml-alerts.arn]
}

