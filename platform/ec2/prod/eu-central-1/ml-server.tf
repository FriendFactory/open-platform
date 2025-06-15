data "aws_instance" "ml-pro-server" {
  filter {
    name   = "tag:Name"
    values = ["ML-Pro-Server"]
  }
}

data "aws_instance" "ml-routing-server" {
  filter {
    name   = "tag:Name"
    values = ["ML-Routing-Server"]
  }
}

resource "aws_cloudwatch_metric_alarm" "ml-server-auto-recovery-alarm-system" {
  for_each          = toset([data.aws_instance.ml-pro-server.id, data.aws_instance.ml-routing-server.id])
  alarm_name        = "ml-server EC2 System Status Check Recovery - ${each.key}"
  alarm_description = "Recover ml-server instance on System Status Check failure for ${each.key}"

  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  period              = "120"
  evaluation_periods  = "2"
  statistic           = "Maximum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = "0.99"

  dimensions = {
    InstanceId = each.key
  }

  alarm_actions = ["arn:aws:automate:eu-central-1:ec2:recover"]
}

