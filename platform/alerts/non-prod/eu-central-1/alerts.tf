data "aws_sns_topic" "platform-messages" {
  name = "platform-messages"
}

data "aws_instance" "instance" {
  for_each = toset(local.instance-names)
  filter {
    name   = "tag:Name"
    values = [each.value]
  }
}

variable "region" {
}

locals {
  instance-names = ["Jenkins", "dev-observability-1"]
}

resource "aws_cloudwatch_metric_alarm" "ec2-status-check-failed-system" {
  for_each            = toset(local.instance-names)
  alarm_name          = "${each.value}-ec2-instance-status-check-system"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 3
  metric_name         = "StatusCheckFailed_System"
  namespace           = "AWS/EC2"
  statistic           = "Average"
  period              = "60"
  threshold           = "0.85"
  treat_missing_data  = "ignore"
  datapoints_to_alarm = 3
  dimensions = {
    InstanceId = data.aws_instance.instance[each.value].id
  }
  alarm_description = "This metric monitors the status-check (system) of ${each.value} instance"
  alarm_actions     = ["arn:aws:automate:${var.region}:ec2:recover", data.aws_sns_topic.platform-messages.arn]
  ok_actions        = [data.aws_sns_topic.platform-messages.arn]
}

resource "aws_cloudwatch_metric_alarm" "ec2-status-check-failed-instance" {
  for_each            = toset(local.instance-names)
  alarm_name          = "${each.value}-ec2-instance-status-check-instance"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 3
  metric_name         = "StatusCheckFailed_Instance"
  namespace           = "AWS/EC2"
  statistic           = "Average"
  period              = "60"
  threshold           = "0.85"
  treat_missing_data  = "ignore"
  datapoints_to_alarm = 3
  dimensions = {
    InstanceId = data.aws_instance.instance[each.value].id
  }
  alarm_description = "This metric monitors the status-check (instance) of ${each.value} instance"
  alarm_actions     = ["arn:aws:automate:${var.region}:ec2:reboot", data.aws_sns_topic.platform-messages.arn]
  ok_actions        = [data.aws_sns_topic.platform-messages.arn]
}

