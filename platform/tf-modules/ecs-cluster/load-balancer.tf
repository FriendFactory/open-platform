resource "aws_lb" "alb" {
  name                       = var.lb_name
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.lb-sg.id]
  subnets                    = data.aws_subnets.lb-subnets.ids
  enable_deletion_protection = true
  idle_timeout               = var.lb_idle_timeout
  access_logs {
    bucket  = aws_s3_bucket.lb-access-logs.id
    prefix  = var.lb_name
    enabled = true
  }
}

data "aws_vpc" "vpc" {
  tags = {
    Name = var.vpc_name
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_subnets" "ecs-subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  filter {
    name   = "tag:Name"
    values = ["${var.ecs_subnet_name_prefix}*"]
  }
}

data "aws_subnet" "ecs-subnet" {
  for_each = toset(data.aws_subnets.ecs-subnets.ids)
  id       = each.value
}

data "aws_subnets" "lb-subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  filter {
    name   = "tag:Name"
    values = ["${var.lb_subnet_name_prefix}*"]
  }
}

resource "aws_security_group" "lb-sg" {
  name        = "${var.lb_name}-load-balancer-sg"
  description = "${var.lb_name} load balancer sg"
  vpc_id      = data.aws_vpc.vpc.id
}

resource "aws_security_group_rule" "http-to-alb" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "alb-to-private-subnet-http" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [for s in data.aws_subnet.ecs-subnet : s.cidr_block]
}

resource "aws_security_group_rule" "alb-to-private-subnet-8080" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "egress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  cidr_blocks       = [for s in data.aws_subnet.ecs-subnet : s.cidr_block]
}

data "aws_region" "current" {}

resource "aws_s3_bucket" "lb-access-logs" {
  bucket = "${var.vpc_name}-ecs-lb-access-logs-${data.aws_region.current.name}"
}

resource "aws_s3_bucket_ownership_controls" "lb-access-logs-ownership-controls" {
  bucket = aws_s3_bucket.lb-access-logs.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "lb-access-logs-s3-acl" {
  depends_on = [aws_s3_bucket_ownership_controls.lb-access-logs-ownership-controls]
  bucket     = aws_s3_bucket.lb-access-logs.id
  acl        = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "lb-access-logs-s3-lifecycle" {
  bucket = aws_s3_bucket.lb-access-logs.id

  rule {
    id = "lb-access-logs-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 20
    }

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.lb-access-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
resource "aws_s3_bucket_policy" "lb-access-logs-bucket-policy" {
  bucket = aws_s3_bucket.lb-access-logs.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnableLbToPutAccessLogsIntoBucket",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::054676820928:root"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
    },
    {
      "Sid": "AWSLogDeliveryWrite",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
      "Condition": {
        "StringEquals": {
          "s3:x-amz-acl": "bucket-owner-full-control"
        }
      }
    },
    {
      "Sid": "AWSLogDeliveryAclCheck",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:GetBucketAcl",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}"
    }
  ]
}
POLICY
}

data "aws_route53_zone" "route53-zone" {
  name         = var.aws_route53_zone_name
  private_zone = var.aws_route53_zone_private
}

resource "aws_route53_record" "route53-record" {
  zone_id = data.aws_route53_zone.route53-zone.zone_id
  name    = "${var.vpc_name}-ecs.${var.aws_route53_zone_name}"
  type    = "A"
  alias {
    name                   = aws_lb.alb.dns_name
    zone_id                = aws_lb.alb.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_listener" "machine-learning-services" {
  load_balancer_arn = aws_lb.alb.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

output "ecs-alb" {
  description = "The application load balancer created for the ecs cluster."
  value       = aws_lb.alb
}

output "ecs-alb-sg" {
  description = "The application load balancer security group."
  value       = aws_security_group.lb-sg
}

output "ecs-subnet-ids" {
  description = "The subnet ids for ecs services to run in."
  value       = data.aws_subnets.ecs-subnets.ids
}

output "ecs-alb-root-url" {
  description = "The root URL for the ECS ALB, from Route53."
  value       = aws_route53_record.route53-record.name
}

