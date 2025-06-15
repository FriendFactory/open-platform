locals {
  vpcs = toset(["Default", "dev-1", "dev-2", "content-test", "content-stage", "content-prod", "load-test"])
}

data "aws_vpc" "vpc" {
  for_each = local.vpcs
  tags = {
    Name = each.key
  }
}

resource "aws_route53_zone" "frever-ml-dev-internal" {
  name = "frever-ml-dev-internal.com"

  vpc {
    vpc_id = data.aws_vpc.vpc["content-prod"].id
  }

  lifecycle {
    ignore_changes = [vpc]
  }
}

data "aws_vpc" "machine-learning-vpc" {
  provider = aws.frever-machine-learning
  tags = {
    Name = "prod"
  }
}

resource "aws_route53_vpc_association_authorization" "machine-learning-vpc-association-authorization" {
  vpc_id  = data.aws_vpc.machine-learning-vpc.id
  zone_id = aws_route53_zone.frever-ml-dev-internal.id
}

resource "aws_route53_zone_association" "machine-learning-vpc-zone-association" {
  provider = aws.frever-machine-learning
  vpc_id   = aws_route53_vpc_association_authorization.machine-learning-vpc-association-authorization.vpc_id
  zone_id  = aws_route53_vpc_association_authorization.machine-learning-vpc-association-authorization.zone_id
}

