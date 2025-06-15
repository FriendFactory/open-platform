resource "aws_lb" "content-prod" {
  name               = "content-prod"
  internal           = false
  load_balancer_type = "application"
  idle_timeout       = 120
  security_groups    = [data.aws_security_group.content-prod-alb.id]
  subnets            = data.aws_subnets.public.ids

  enable_deletion_protection = true

  access_logs {
    bucket  = data.aws_s3_bucket.access-logs.bucket
    prefix  = "content-prod"
    enabled = true
  }

  tags = {
    "elbv2.k8s.aws/cluster"    = "content-prod"
    "ingress.k8s.aws/resource" = "LoadBalancer"
    "ingress.k8s.aws/stack"    = "frever-content-prod-z"
  }
}

data "aws_security_group" "content-prod-alb" {
  name = "k8s-frevercontentprod-*"
}

data "aws_subnets" "public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-prod.id]
  }

  filter {
    name   = "tag:Name"
    values = ["content-prod-public-eu-central-1*"]
  }
}

data "aws_vpc" "content-prod" {
  filter {
    name   = "tag:Name"
    values = ["content-prod"]
  }
}

data "aws_s3_bucket" "access-logs" {
  bucket = "prod-lb-access-logs-${data.aws_region.current.name}"
}

data "aws_region" "current" {}

