locals {
  vpcs = toset(["Default", "dev-1", "dev-2", "content-test", "content-stage", "content-prod", "load-test", "ixia-prod"])
}

data "aws_vpc" "vpc" {
  for_each = local.vpcs
  tags = {
    Name = each.key
  }
}

resource "aws_route53_zone" "frever-ml-internal" {
  name = "frever-ml-internal.com"

  vpc {
    vpc_id = data.aws_vpc.vpc["content-prod"].id
  }

  lifecycle {
    ignore_changes = [vpc]
  }
}

resource "aws_route53_vpc_association_authorization" "ixia-prod-vpc-association-authorization" {
  vpc_id  = data.aws_vpc.vpc["ixia-prod"].id
  zone_id = aws_route53_zone.frever-ml-internal.id
}

resource "aws_route53_zone_association" "ixia-prod-vpc-zone-association" {
  vpc_id   = aws_route53_vpc_association_authorization.ixia-prod-vpc-association-authorization.vpc_id
  zone_id  = aws_route53_vpc_association_authorization.ixia-prod-vpc-association-authorization.zone_id
}

data "aws_vpc" "machine-learning-vpc" {
  provider = aws.frever-machine-learning
  tags = {
    Name = "prod"
  }
}

resource "aws_route53_vpc_association_authorization" "machine-learning-vpc-association-authorization" {
  vpc_id  = data.aws_vpc.machine-learning-vpc.id
  zone_id = aws_route53_zone.frever-ml-internal.id
}

resource "aws_route53_zone_association" "machine-learning-vpc-zone-association" {
  provider = aws.frever-machine-learning
  vpc_id   = aws_route53_vpc_association_authorization.machine-learning-vpc-association-authorization.vpc_id
  zone_id  = aws_route53_vpc_association_authorization.machine-learning-vpc-association-authorization.zone_id
}

data "aws_vpc" "prod-us-east-2-vpc" {
  provider = aws.frever-us
  tags = {
    Name = "prod"
  }
}

resource "aws_route53_vpc_association_authorization" "prod-us-east-2-vpc-association-authorization" {
  vpc_id  = data.aws_vpc.prod-us-east-2-vpc.id
  zone_id = aws_route53_zone.frever-ml-internal.id
}

resource "aws_route53_zone_association" "prod-us-east-2-vpc-zone-association" {
  provider = aws.frever-us
  vpc_id   = aws_route53_vpc_association_authorization.prod-us-east-2-vpc-association-authorization.vpc_id
  zone_id  = aws_route53_vpc_association_authorization.prod-us-east-2-vpc-association-authorization.zone_id
}

