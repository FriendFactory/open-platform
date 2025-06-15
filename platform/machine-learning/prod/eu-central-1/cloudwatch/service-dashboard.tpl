{
  "start": "start",
  "end": "end",
  "periodOverride": "auto",
  "widgets": [
  %{ for i in range(length(target-groups-service)) ~}
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": ${i * 6},
      "properties": {
        "view": "timeSeries",
        "title": "${target-groups-service[i]} Response Time",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ApplicationELB",
            "TargetResponseTime",
            "LoadBalancer",
            "${can(regex("public", target-groups-service[i])) ? public-alb.arn_suffix : private-alb.arn_suffix}",
            "TargetGroup",
            "${all-target-groups[target-groups-service[i]].arn_suffix}"
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
      "y": ${i * 6},
      "properties": {
        "view": "timeSeries",
        "title": "${target-groups-service[i]} HTTPCode 5xx Count",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ApplicationELB",
            "HTTPCode_Target_5XX_Count",
            "LoadBalancer",
            "${can(regex("public", target-groups-service[i])) ? public-alb.arn_suffix : private-alb.arn_suffix}",
            "TargetGroup",
            "${all-target-groups[target-groups-service[i]].arn_suffix}",
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
      "y": ${i * 6},
      "properties": {
        "view": "timeSeries",
        "title": "${target-groups-service[i]} RequestCount",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ApplicationELB",
            "RequestCount",
            "LoadBalancer",
            "${can(regex("public", target-groups-service[i])) ? public-alb.arn_suffix : private-alb.arn_suffix}",
            "TargetGroup",
            "${all-target-groups[target-groups-service[i]].arn_suffix}",
            {
              "stat": "Sum"
            }
          ]
        ],
        "yAxis": {}
      }
    },
    %{ endfor ~}
    {
      "type": "metric",
      "width": 6,
      "height": 6,
      "x": 0,
      "y": ${length(target-groups-service) * 6},
      "properties": {
        "view": "timeSeries",
        "title": "${service-name} CPU Utilization",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ECS",
            "CPUUtilization",
            "ServiceName",
            "${service-name}",
            "ClusterName",
            "machine-learning"
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
      "y": ${length(target-groups-service) * 6},
      "properties": {
        "view": "timeSeries",
        "title": "${service-name} Memory Utilization",
        "region": "eu-central-1",
        "metrics": [
          [
            "AWS/ECS",
            "MemoryUtilization",
            "ServiceName",
            "${service-name}",
            "ClusterName",
            "machine-learning"
          ]
        ],
        "yAxis": {}
      }
    }
  ]
}
