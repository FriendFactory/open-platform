resource "aws_sns_topic" "alerts" {
  name = "system-alerts"
}

resource "aws_sns_topic_policy" "default" {
  arn = aws_sns_topic.alerts.arn

  policy = data.aws_iam_policy_document.sns_topic_policy.json
}

data "aws_iam_policy_document" "sns_topic_policy" {
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
      aws_sns_topic.alerts.arn,
    ]

    sid = "__default_statement_ID"
  }
}

data "aws_iam_policy_document" "slack-sns-policy" {
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
      data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn,
    ]

    sid = "__default_statement_ID"
  }
}

locals {
  emails = ["xie.xiaodong@frever.com", "a5w3n6w3l8t6n2q8@ffextendedworkspace.slack.com", "alerts@frever.com"]
  name_mappings = {
    adminser = "admin-service"
    assetser = "asset-service"
    authserv = "auth-service"
    clientse = "client-service"
    mainserv = "main-service"
    notifica = "notification-service"
    videoser = "video-service"
    chatserv = "chat-service"
  }
  service_target_groups = sort([for arn in data.aws_resourcegroupstaggingapi_resources.target-groups.resource_tag_mapping_list[*].resource_arn : element(split("/", arn), 1)])
  rds_dbs               = ["production-main", "production-auth"]
}

resource "aws_sns_topic_subscription" "sns-topic" {
  for_each  = toset(local.emails)
  topic_arn = aws_sns_topic.alerts.arn
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

data "aws_lb" "content-prod" {
  name = "content-prod"
}

data "aws_lb_target_group" "content-prod-services" {
  for_each = toset(local.service_target_groups)
  name     = each.key
}

data "aws_resourcegroupstaggingapi_resources" "target-groups" {
  tag_filter {
    key    = "elbv2.k8s.aws/cluster"
    values = ["content-prod"]
  }
  resource_type_filters = ["elasticloadbalancing:targetgroup"]
}

# https://github.com/FriendFactory/Server/blob/master/deploy/environment/frever-monitoring/cdk/lib/frever-monitoring-stack.ts#L18
data "aws_sns_topic" "send-to-slack-aws-alarms-channel" {
  name = "FreverMonitoringStack-Alarms04B5A0BF-WC0J6XJ59IFB"
}

resource "aws_sns_topic_policy" "slack-sns-policy" {
  arn = data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn

  policy = data.aws_iam_policy_document.slack-sns-policy.json
}

# "k8s-app15-adminser-ff7466ffd1"
# "k8s-app16-adminser-d4fdeb53a9"
resource "aws_cloudwatch_metric_alarm" "target_response_time" {
  for_each            = toset(local.service_target_groups)
  alarm_name          = "${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}-long-response-time"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  statistic           = "Average"
  period              = "60"
  threshold           = "5"
  treat_missing_data  = "ignore"
  dimensions = {
    LoadBalancer = data.aws_lb.content-prod.arn_suffix
    TargetGroup  = data.aws_lb_target_group.content-prod-services[each.key].arn_suffix
  }
  alarm_description = "This metric monitors the response time for ${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "target_number_of_healthy_hosts" {
  for_each            = toset(local.service_target_groups)
  alarm_name          = "${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}-too-few-healthy-hosts"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = "1"
  metric_name         = "HealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  statistic           = "Average"
  period              = "60"
  threshold           = "2"
  treat_missing_data  = "ignore"
  dimensions = {
    LoadBalancer = data.aws_lb.content-prod.arn_suffix
    TargetGroup  = data.aws_lb_target_group.content-prod-services[each.key].arn_suffix
  }
  alarm_description = "This metric monitors the number of healthy hosts for ${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "too-many-server-errors" {
  alarm_name          = "too-many-errors-from-LB-${data.aws_lb.content-prod.name}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  threshold           = "5"
  alarm_description   = "LB ${data.aws_lb.content-prod.name} request error rate has exceeded 5%"
  treat_missing_data  = "ignore"
  alarm_actions       = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions          = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]

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
        LoadBalancer = data.aws_lb.content-prod.arn_suffix
      }
    }
  }

  metric_query {
    id = "error_count"

    metric {
      metric_name = "HTTPCode_ELB_5XX_Count"
      namespace   = "AWS/ApplicationELB"
      period      = "60"
      stat        = "Sum"
      unit        = "Count"

      dimensions = {
        LoadBalancer = data.aws_lb.content-prod.arn_suffix
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "too-many-server-errors-per-service" {
  for_each            = toset(local.service_target_groups)
  alarm_name          = "too-many-errors-for-${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  threshold           = "5"
  alarm_description   = "LB ${data.aws_lb.content-prod.name} request error rate has exceeded 5% for target ${substr(each.key, 4, 5)}-${local.name_mappings[substr(each.key, 10, 8)]}"
  treat_missing_data  = "ignore"
  alarm_actions       = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions          = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]

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
        LoadBalancer = data.aws_lb.content-prod.arn_suffix
        TargetGroup  = data.aws_lb_target_group.content-prod-services[each.key].arn_suffix
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
        LoadBalancer = data.aws_lb.content-prod.arn_suffix
        TargetGroup  = data.aws_lb_target_group.content-prod-services[each.key].arn_suffix
      }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "rds-db-connection" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-many-db-connections-to-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "500"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors the number of DB connections for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-cpu-utilization" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-cpu-utilization-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "75"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors the CPU utilization for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-disk-queue-depth" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-disk-queue-depth-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "DiskQueueDepth"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "2"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors the disk queue depth for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-disk-read-latency" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-disk-read-latency-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "ReadLatency"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "0.02"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors the disk read latency for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-disk-write-latency" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-disk-write-latency-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "WriteLatency"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "0.1"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors the disk write latency for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-cpu-load" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-cpu-load-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "DBLoadCPU"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = each.key == "production-auth" ? "2" : "6"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors CPU load (the number of active sessions where the wait event type is CPU) for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-load" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-high-load-for-rds-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "DBLoad"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = each.key == "production-auth" ? "2" : "6"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors load (the number of active sessions for the DB engine) for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds-db-free-storage-space" {
  for_each            = toset(local.rds_dbs)
  alarm_name          = "too-low-free-storage-space-for-rds-${each.key}"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  statistic           = "Average"
  period              = "60"
  threshold           = "10000000000"
  treat_missing_data  = "ignore"
  dimensions = {
    DBInstanceIdentifier = each.key
  }
  alarm_description = "This metric monitors free storage space for RDS ${each.key}"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

data "aws_instances" "eks-content-prod-cluster-app-group-ec2-instances" {
  filter {
    name   = "tag:eks:cluster-name"
    values = [data.aws_eks_cluster.content-prod.name]
  }

  filter {
    name   = "tag:eks:nodegroup-name"
    values = [data.aws_eks_node_group.content-prod-app.node_group_name]
  }
}

data "aws_eks_cluster" "content-prod" {
  name = "content-prod"
}

data "aws_eks_node_group" "content-prod-app" {
  cluster_name    = data.aws_eks_cluster.content-prod.name
  node_group_name = "content-prod-apps"
}

resource "aws_cloudwatch_metric_alarm" "eks-content-prod-cluster-app-group-ec2-instances-cpu-utilization" {
  for_each            = toset(data.aws_instances.eks-content-prod-cluster-app-group-ec2-instances.ids)
  alarm_name          = "too-high-cpu-utilization-for-ec2-instance-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  statistic           = "Average"
  period              = "60"
  threshold           = "70"
  treat_missing_data  = "ignore"
  dimensions = {
    InstanceId = each.key
  }
  alarm_description = "This metric monitors the CPU utilization for EC2 instance ${each.key} in EKS content-prod cluster content-prod-app group"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "eks-content-prod-pod-cpu-utilization" {
  for_each            = toset(local.service_target_groups)
  alarm_name          = "too-high-pod-cpu-utilization-for-${each.key}-in-content-prod-eks-cluster"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "pod_cpu_utilization"
  namespace           = "ContainerInsights"
  statistic           = "Average"
  period              = "60"
  threshold           = "70"
  treat_missing_data  = "ignore"
  dimensions = {
    ClusterName = data.aws_eks_cluster.content-prod.name
    Namespace   = data.aws_eks_cluster.content-prod.name
    Service     = each.key
  }
  alarm_description = "This metric monitors pod CPU utilization for ${each.key} in EKS content-prod cluster"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "eks-content-prod-pod-memory-utilization" {
  for_each            = toset(local.service_target_groups)
  alarm_name          = "too-high-pod-memory-utilization-for-${each.key}-in-content-prod-eks-cluster"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "pod_memory_utilization"
  namespace           = "ContainerInsights"
  statistic           = "Average"
  period              = "60"
  threshold           = "70"
  treat_missing_data  = "ignore"
  dimensions = {
    ClusterName = data.aws_eks_cluster.content-prod.name
    Namespace   = data.aws_eks_cluster.content-prod.name
    Service     = each.key
  }
  alarm_description = "This metric monitors pod memory utilization for ${each.key} in EKS content-prod cluster"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "media-convert-long-transcoding-time" {
  alarm_name          = "media-convert-prod-queue-long-transcoding-time"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "TranscodingTime"
  namespace           = "AWS/MediaConvert"
  statistic           = "Average"
  period              = "60"
  threshold           = "50000"
  treat_missing_data  = "ignore"
  dimensions = {
    Queue = "arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod"
  }
  alarm_description = "This metric monitors the average TranscodingTime for AWS media-convert prod queue"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "media-convert-long-standby-time" {
  alarm_name          = "media-convert-prod-queue-long-standby-time"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "StandbyTime"
  namespace           = "AWS/MediaConvert"
  statistic           = "Average"
  period              = "60"
  threshold           = "40000"
  treat_missing_data  = "ignore"
  dimensions = {
    Queue = "arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod"
  }
  alarm_description = "This metric monitors the average StandbyTime for AWS media-convert prod queue"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

resource "aws_cloudwatch_metric_alarm" "media-convert-large-error-count" {
  alarm_name          = "media-convert-prod-queue-too-many-errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "2"
  metric_name         = "JobsErroredCount"
  namespace           = "AWS/MediaConvert"
  statistic           = "Sum"
  period              = "60"
  threshold           = "3"
  treat_missing_data  = "ignore"
  dimensions = {
    Queue = "arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod"
  }
  alarm_description = "This metric monitors the jobs error count for AWS media-convert prod queue"
  alarm_actions     = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
  ok_actions        = [aws_sns_topic.alerts.arn, data.aws_sns_topic.send-to-slack-aws-alarms-channel.arn]
}

data "aws_elasticache_replication_group" "prod-redis" {
  replication_group_id = "content-prod-cache-cluster-on"
}

resource "aws_cloudwatch_metric_alarm" "redis-engine-cpu-utilization" {
  for_each            = toset(data.aws_elasticache_replication_group.prod-redis.member_clusters)
  alarm_name          = "prod-redis-engine-cpu-utilization-high-${each.key}"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = "3"
  metric_name         = "EngineCPUUtilization"
  namespace           = "AWS/ElastiCache"
  statistic           = "Maximum"
  period              = "60"
  threshold           = "80"
  treat_missing_data  = "ignore"
  dimensions = {
    CacheNodeId    = "0001"
    CacheClusterId = each.key
  }
  alarm_description = "This metric monitors the Redis Engine CPU utilization"
  alarm_actions     = [aws_sns_topic.alerts.arn]
  ok_actions        = [aws_sns_topic.alerts.arn]
}

