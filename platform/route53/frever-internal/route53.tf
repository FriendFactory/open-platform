locals {
  vpcs = toset(["Default", "dev-1", "dev-2", "content-test", "content-stage", "content-prod", "load-test", "dev", "ixia-prod"])
}

data "aws_vpc" "vpc" {
  for_each = local.vpcs
  tags = {
    Name = each.key
  }
}

data "aws_vpc" "prod-us-east-2" {
  provider = aws.frever-us
  tags = {
    Name = "prod"
  }
}

resource "aws_route53_zone" "frever-internal" {
  name = "frever-internal.com"

  dynamic "vpc" {
    for_each = data.aws_vpc.vpc
    content {
      vpc_id = vpc.value["id"]
    }
  }

  vpc {
    vpc_id     = data.aws_vpc.prod-us-east-2.id
    vpc_region = "us-east-2"
  }
}

