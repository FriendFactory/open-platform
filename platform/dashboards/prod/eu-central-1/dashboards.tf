resource "aws_cloudwatch_dashboard" "kpis" {
  dashboard_name = "KPIs"

  dashboard_body = <<EOF
{
  "start": "start",
  "end": "end",
  "periodOverride": "auto",
  "widgets": [
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 0,
      "properties": {
        "view": "timeSeries",
        "title": "DatabaseConnections-production-main",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DatabaseConnections",
            "DBInstanceIdentifier",
            "production-main"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 0,
      "properties": {
        "view": "timeSeries",
        "title": "DatabaseConnections-production-video",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DatabaseConnections",
            "DBInstanceIdentifier",
            "production-video"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 0,
      "properties": {
        "view": "timeSeries",
        "title": "DatabaseConnections-production-auth",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DatabaseConnections",
            "DBInstanceIdentifier",
            "production-auth"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 0,
      "properties": {
        "view": "timeSeries",
        "title": "ReadLatency-production-main",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "ReadLatency",
            "DBInstanceIdentifier",
            "production-main",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 6,
      "properties": {
        "view": "timeSeries",
        "title": "WriteLatency-production-main",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "WriteLatency",
            "DBInstanceIdentifier",
            "production-main",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 6,
      "properties": {
        "view": "timeSeries",
        "title": "ReadLatency-production-video",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "ReadLatency",
            "DBInstanceIdentifier",
            "production-video",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 6,
      "properties": {
        "view": "timeSeries",
        "title": "WriteLatency-production-video",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "WriteLatency",
            "DBInstanceIdentifier",
            "production-video",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 6,
      "properties": {
        "view": "timeSeries",
        "title": "ReadLatency-production-auth",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "ReadLatency",
            "DBInstanceIdentifier",
            "production-auth",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 12,
      "properties": {
        "view": "timeSeries",
        "title": "WriteLatency-production-auth",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "WriteLatency",
            "DBInstanceIdentifier",
            "production-auth",
            {
              "period": 60
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 12,
      "properties": {
        "view": "timeSeries",
        "title": "NumberOfMessagesSent-asset-copy",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/SQS",
            "NumberOfMessagesSent",
            "QueueName",
            "AssetCopying-Content-Production",
            {
              "stat": "Sum"
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 12,
      "properties": {
        "view": "timeSeries",
        "title": "NumberOfMessagesSent-media-convert",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/SQS",
            "NumberOfMessagesSent",
            "QueueName",
            "media-convert-production",
            {
              "stat": "Sum"
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 12,
      "properties": {
        "view": "timeSeries",
        "title": "NumberOfMessagesSent-video-convert",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/SQS",
            "NumberOfMessagesSent",
            "QueueName",
            "prod-video-conversion-job-creation",
            {
              "stat": "Sum"
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 18,
      "properties": {
        "view": "timeSeries",
        "title": "CPUUtilization-content-prod-cache",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ElastiCache",
            "CPUUtilization",
            "CacheClusterId",
            "content-prod-cache"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 18,
      "properties": {
        "view": "timeSeries",
        "title": "DatabaseMemoryUsagePercentage-content-prod-cache",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ElastiCache",
            "DatabaseMemoryUsagePercentage",
            "CacheClusterId",
            "content-prod-cache"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 18,
      "properties": {
        "view": "timeSeries",
        "title": "GetTypeCmdsLatency-content-prod-cache",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ElastiCache",
            "GetTypeCmdsLatency",
            "CacheClusterId",
            "content-prod-cache"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 18,
      "properties": {
        "view": "timeSeries",
        "title": "SetTypeCmdsLatency-content-prod-cache",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ElastiCache",
            "SetTypeCmdsLatency",
            "CacheClusterId",
            "content-prod-cache"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 24,
      "properties": {
        "view": "timeSeries",
        "title": "StandbyTime-arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/MediaConvert",
            "StandbyTime",
            "Queue",
            "arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 24,
      "properties": {
        "view": "timeSeries",
        "title": "JobsErroredCount-arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/MediaConvert",
            "JobsErroredCount",
            "Queue",
            "arn:aws:mediaconvert:eu-central-1:722913253728:queues/prod"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 24,
      "properties": {
        "view": "timeSeries",
        "title": "node_cpu_utilization-content-prod",
        "region": "eu-central-1",
        "metrics": [
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "ClusterName",
            "content-prod"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 24,
      "properties": {
        "view": "timeSeries",
        "title": "cluster_failed_node_count-content-prod",
        "region": "eu-central-1",
        "metrics": [
          [
            "ContainerInsights",
            "cluster_failed_node_count",
            "ClusterName",
            "content-prod"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 30,
      "properties": {
        "view": "timeSeries",
        "title": "TargetResponseTime-app/content-prod/27eea2c95a05b83a",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ApplicationELB",
            "TargetResponseTime",
            "LoadBalancer",
            "app/content-prod/27eea2c95a05b83a"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 6,
      "y": 30,
      "properties": {
        "view": "timeSeries",
        "title": "HTTPCode_Target_5XX_Count-app/content-prod/27eea2c95a05b83a",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ApplicationELB",
            "HTTPCode_Target_5XX_Count",
            "LoadBalancer",
            "app/content-prod/27eea2c95a05b83a",
            {
              "stat": "Sum"
            }
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 12,
      "y": 30,
      "properties": {
        "view": "timeSeries",
        "title": "DB Load production-main",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DBLoad",
            "DBInstanceIdentifier",
            "production-main"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 18,
      "y": 30,
      "properties": {
        "view": "timeSeries",
        "title": "DB Load production-video",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DBLoad",
            "DBInstanceIdentifier",
            "production-video"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": 36,
      "properties": {
        "view": "timeSeries",
        "title": "DB Load production-auth",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/RDS",
            "DBLoad",
            "DBInstanceIdentifier",
            "production-auth"
          ]
        ],
        "yAxis": {}
      }
    },
    {
      "height": 6,
      "properties": {
        "legend": {
          "position": "right"
        },
        "metrics": [
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-07b572ad2501c35f4",
            "NodeName",
            "ip-10-0-12-180.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0d8eaca155757f3fe",
            "NodeName",
            "ip-10-0-8-107.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0ce7cf7588f139c29",
            "NodeName",
            "ip-10-0-1-102.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0bbe53e7aff28122c",
            "NodeName",
            "ip-10-0-3-106.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0757a54a60d2b4dd6",
            "NodeName",
            "ip-10-0-3-130.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0bf3b038cbd8f4f9e",
            "NodeName",
            "ip-10-0-1-126.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-0541e57e0c856f2a7",
            "NodeName",
            "ip-10-0-1-145.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_cpu_utilization",
            "InstanceId",
            "i-093cfab31ddb61137",
            "NodeName",
            "ip-10-0-12-16.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ]
        ],
        "period": 60,
        "region": "eu-central-1",
        "title": "content-prod cluster CPU Utilization",
        "view": "timeSeries"
      },
      "type": "metric",
      "width": 24,
      "x": 0,
      "y": 42
    },
    {
      "height": 6,
      "properties": {
        "legend": {
          "position": "right"
        },
        "metrics": [
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0d8eaca155757f3fe",
            "NodeName",
            "ip-10-0-8-107.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0541e57e0c856f2a7",
            "NodeName",
            "ip-10-0-1-145.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-07b572ad2501c35f4",
            "NodeName",
            "ip-10-0-12-180.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0ce7cf7588f139c29",
            "NodeName",
            "ip-10-0-1-102.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0bbe53e7aff28122c",
            "NodeName",
            "ip-10-0-3-106.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0bf3b038cbd8f4f9e",
            "NodeName",
            "ip-10-0-1-126.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-0757a54a60d2b4dd6",
            "NodeName",
            "ip-10-0-3-130.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ],
          [
            "ContainerInsights",
            "node_memory_utilization",
            "InstanceId",
            "i-093cfab31ddb61137",
            "NodeName",
            "ip-10-0-12-16.eu-central-1.compute.internal",
            "ClusterName",
            "content-prod",
            {
              "stat": "Average"
            }
          ]
        ],
        "period": 60,
        "region": "eu-central-1",
        "title": "content-prod cluster Memory Utilization",
        "view": "timeSeries"
      },
      "type": "metric",
      "width": 24,
      "x": 0,
      "y": 48
    }
  ]
}
EOF
}

