data "aws_vpc" "prod" {
  tags = {
    Name = "prod"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_subnet" "prod-public" {
  count  = length(sort(data.aws_availability_zones.available.names))
  vpc_id = data.aws_vpc.prod.id
  filter {
    name   = "tag:Name"
    values = ["${data.aws_vpc.prod.tags["Name"]}-public-${data.aws_availability_zones.available.names[count.index]}"]
  }
}

data "aws_subnet" "prod-private" {
  count  = length(sort(data.aws_availability_zones.available.names))
  vpc_id = data.aws_vpc.prod.id
  filter {
    name   = "tag:Name"
    values = ["${data.aws_vpc.prod.tags["Name"]}-private-${data.aws_availability_zones.available.names[count.index]}"]
  }
}

resource "aws_lb" "machine-learning-dev" {
  name                       = "machine-learning-dev"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.lb-sg.id]
  subnets                    = [for subnet in data.aws_subnet.prod-public : subnet.id]
  enable_deletion_protection = true
}

resource "aws_security_group" "lb-sg" {
  name        = "machine-learning-dev-load-balancer-sg"
  description = "machine-learning-dev load balancer sg"
  vpc_id      = data.aws_vpc.prod.id
}

resource "aws_security_group_rule" "http-to-alb" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.prod.cidr_block]
}

resource "aws_security_group_rule" "alb-to-private-subnet-http" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = data.aws_subnet.prod-private[*].cidr_block
}

resource "aws_security_group_rule" "alb-to-private-subnet-8080" {
  security_group_id = aws_security_group.lb-sg.id
  type              = "egress"
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"
  cidr_blocks       = data.aws_subnet.prod-private[*].cidr_block
}

data "aws_route53_zone" "frever-ml" {
  provider     = aws.frever
  name         = "frever-ml.com"
  private_zone = false
}

data "aws_acm_certificate" "frever-ml-star" {
  domain    = "*.frever-ml.com"
  statuses  = ["ISSUED"]
  types     = ["AMAZON_ISSUED"]
  key_types = ["EC_prime256v1"]
}

resource "aws_route53_record" "frever-ml-dev" {
  provider = aws.frever
  zone_id  = data.aws_route53_zone.frever-ml.zone_id
  name     = "dev.frever-ml.com"
  type     = "A"
  alias {
    name                   = aws_lb.machine-learning-dev.dns_name
    zone_id                = aws_lb.machine-learning-dev.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_listener" "machine-learning-services" {
  load_balancer_arn = aws_lb.machine-learning-dev.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-1-2017-01"
  certificate_arn   = data.aws_acm_certificate.frever-ml-star.arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

