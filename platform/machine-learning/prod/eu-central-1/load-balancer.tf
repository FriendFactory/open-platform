resource "aws_lb" "machine-learning" {
  name                       = "machine-learning-prod"
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.lb_sg.id]
  subnets                    = [for subnet in aws_subnet.prod-public : subnet.id]
  enable_deletion_protection = true
  access_logs {
    bucket  = aws_s3_bucket.prod-lb-access-logs.id
    prefix  = "machine-learning-prod"
    enabled = true
  }
}

resource "aws_lb" "machine-learning-public" {
  name                       = "machine-learning-prod-public"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.lb_sg.id]
  subnets                    = [for subnet in aws_subnet.prod-public : subnet.id]
  enable_deletion_protection = true
  access_logs {
    bucket  = aws_s3_bucket.prod-lb-access-logs.id
    prefix  = "machine-learning-prod-public"
    enabled = true
  }
}

resource "aws_security_group" "lb_sg" {
  name        = "machine-learning-prod-load-balancer-sg"
  description = "machine-learning-prod load balancer sg"
  vpc_id      = aws_vpc.prod.id
}

resource "aws_security_group_rule" "http-to-alb" {
  security_group_id = aws_security_group.lb_sg.id
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [aws_vpc.prod.cidr_block, data.aws_vpc.frever-prod.cidr_block, data.aws_vpc.frever-prod-us.cidr_block]
}

resource "aws_security_group_rule" "alb-to-private-subnet-http" {
  security_group_id = aws_security_group.lb_sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = aws_subnet.prod-private[*].cidr_block
}

resource "aws_security_group_rule" "alb-to-private-subnet-8080" {
  security_group_id = aws_security_group.lb_sg.id
  type              = "egress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  cidr_blocks       = aws_subnet.prod-private[*].cidr_block
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

resource "aws_s3_bucket" "prod-lb-access-logs" {
  bucket = "machine-learning-prod-lb-access-logs-${data.aws_region.current.name}"
}

resource "aws_s3_bucket_acl" "prod-lb-access-logs-s3-acl" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id
  acl    = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "prod-lb-access-logs-s3-lifecycle" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id

  rule {
    id = "machine-learning-prod-lb-access-logs-lifecycle"

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
  bucket = aws_s3_bucket.prod-lb-access-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
resource "aws_s3_bucket_policy" "prod-lb-access-logs-bucket-policy" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id

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
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
    },
    {
      "Sid": "AWSLogDeliveryWrite",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
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
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}"
    }
  ]
}
POLICY
}

data "aws_route53_zone" "frever-ml-internal" {
  provider     = aws.frever
  name         = "frever-ml-internal.com"
  private_zone = true
}

resource "aws_route53_record" "frever-ml-internal" {
  provider = aws.frever
  zone_id  = data.aws_route53_zone.frever-ml-internal.zone_id
  name     = "frever-ml-internal.com"
  type     = "A"
  alias {
    name                   = aws_lb.machine-learning.dns_name
    zone_id                = aws_lb.machine-learning.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_listener" "machine-learning-services" {
  load_balancer_arn = aws_lb.machine-learning.arn
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

data "aws_route53_zone" "frever-ml" {
  provider     = aws.frever
  name         = "frever-ml.com"
  private_zone = false
}

resource "aws_acm_certificate" "frever-ml" {
  domain_name       = "frever-ml.com"
  validation_method = "DNS"
  key_algorithm     = "EC_prime256v1"
}

resource "aws_acm_certificate" "frever-ml-star" {
  domain_name               = "*.frever-ml.com"
  validation_method         = "DNS"
  key_algorithm             = "EC_prime256v1"
  subject_alternative_names = ["frever-ml.com"]
}

resource "aws_route53_record" "frever-ml-validation" {
  provider = aws.frever
  for_each = {
    for dvo in aws_acm_certificate.frever-ml.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.frever-ml.zone_id
}

resource "aws_route53_record" "frever-ml-star-validation" {
  provider = aws.frever
  for_each = {
    for dvo in aws_acm_certificate.frever-ml-star.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.frever-ml.zone_id
}

resource "aws_route53_record" "frever-ml" {
  provider = aws.frever
  zone_id  = data.aws_route53_zone.frever-ml.zone_id
  name     = "frever-ml.com"
  type     = "A"
  alias {
    name                   = aws_lb.machine-learning-public.dns_name
    zone_id                = aws_lb.machine-learning-public.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_listener" "machine-learning-services-public" {
  load_balancer_arn = aws_lb.machine-learning-public.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-1-2017-01"
  certificate_arn   = aws_acm_certificate.frever-ml.arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb" "machine-learning-ixia-prod" {
  name                       = "machine-learning-ixia-prod"
  internal                   = true
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.ixia-prod-lb-sg.id]
  subnets                    = [for subnet in aws_subnet.prod-public : subnet.id]
  enable_deletion_protection = true
  access_logs {
    bucket  = aws_s3_bucket.prod-lb-access-logs.id
    prefix  = "machine-learning-ixia-prod"
    enabled = true
  }
}

resource "aws_security_group" "ixia-prod-lb-sg" {
  name        = "machine-learning-ixia-prod-lb-sg"
  description = "machine-learning-ixia-prod load balancer sg"
  vpc_id      = aws_vpc.prod.id
}

resource "aws_security_group_rule" "ixia-prod-lb-http-to-alb" {
  security_group_id = aws_security_group.ixia-prod-lb-sg.id
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [aws_vpc.prod.cidr_block, data.aws_vpc.frever-ixia-prod.cidr_block]
}

resource "aws_security_group_rule" "ixia-prod-lb-to-private-subnet-http" {
  security_group_id = aws_security_group.ixia-prod-lb-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = aws_subnet.prod-private[*].cidr_block
}

resource "aws_security_group_rule" "ixia-prod-lb-to-private-subnet-8080" {
  security_group_id = aws_security_group.ixia-prod-lb-sg.id
  type              = "egress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  cidr_blocks       = aws_subnet.prod-private[*].cidr_block
}

resource "aws_route53_record" "frever-ixia-prod-ml-internal" {
  provider = aws.frever
  zone_id  = data.aws_route53_zone.frever-ml-internal.zone_id
  name     = "ixia-prod.frever-ml-internal.com"
  type     = "A"
  alias {
    name                   = aws_lb.machine-learning-ixia-prod.dns_name
    zone_id                = aws_lb.machine-learning-ixia-prod.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_listener" "machine-learning-ixia-prod-services" {
  load_balancer_arn = aws_lb.machine-learning-ixia-prod.arn
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

