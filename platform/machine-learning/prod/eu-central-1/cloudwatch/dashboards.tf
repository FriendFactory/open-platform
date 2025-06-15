locals {
  service_to_target_groups = {
    video-gen                       = ["video-gen-tg", "video-gen-public-tg"]
    ml-service                      = ["ml-service-tg"]
  }
  target_groups = flatten(values(local.service_to_target_groups))
  services      = keys(local.service_to_target_groups)
}

resource "aws_cloudwatch_dashboard" "service-dashboards" {
  for_each       = toset(keys(local.service_to_target_groups))
  dashboard_name = "${each.key}-metrics"

  dashboard_body = templatefile("service-dashboard.tpl", {
    service-name          = each.key
    target-groups-service = local.service_to_target_groups[each.key]
    all-target-groups     = data.aws_lb_target_group.ml-service-target-groups
    private-alb           = data.aws_lb.machine-learning-prod
    public-alb            = data.aws_lb.machine-learning-prod-public
  })
}

