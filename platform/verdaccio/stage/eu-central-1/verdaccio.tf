locals {
  verdaccio_port = 4873
}

resource "aws_instance" "verdaccio" {
  ami                    = data.aws_ami.amazon-linux-2.id
  instance_type          = "t3.small"
  key_name               = "platform"
  subnet_id              = data.aws_subnets.content-stage-private.ids[1]
  vpc_security_group_ids = [aws_security_group.verdaccio.id]
  iam_instance_profile   = aws_iam_instance_profile.verdaccio.name

  tags = {
    Name  = "verdaccio"
    Owner = "platform"
  }
}

data "aws_ami" "amazon-linux-2" {
  owners = ["amazon"]
  filter {
    name   = "name"
    values = ["amzn2-ami-kernel-5.10-hvm-2.0.20220426.0-x86_64-gp2"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

resource "aws_iam_instance_profile" "verdaccio" {
  name = "verdaccio"
  role = aws_iam_role.verdaccio.name
}

resource "aws_iam_role" "verdaccio" {
  name = "verdaccio"
  path = "/"

  assume_role_policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "sts:AssumeRole",
            "Principal": {
               "Service": "ec2.amazonaws.com"
            },
            "Effect": "Allow",
            "Sid": ""
        }
    ]
}
EOF

  managed_policy_arns = [data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn]
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

data "aws_vpc" "content-stage" {
  tags = {
    Name = "content-stage"
  }
}

data "aws_subnets" "content-stage-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-stage.id]
  }
  tags = {
    Name = "content-stage-private-eu-central-1*"
  }
}

data "aws_subnets" "content-stage-public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-stage.id]
  }
  tags = {
    Name = "content-stage-public-eu-central-1*"
  }
}

resource "aws_security_group" "verdaccio" {
  name        = "verdaccio-sg"
  description = "SG for verdaccio instance"
  vpc_id      = data.aws_vpc.content-stage.id
  tags = {
    "Name" = "verdaccio"
  }
}

data "aws_security_group" "content-stage-ssh-bastion" {
  name   = "content-stage-ssh-bastion"
  vpc_id = data.aws_vpc.content-stage.id
}

resource "aws_security_group_rule" "verdaccio-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.verdaccio.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-stage-ssh-bastion-to-verdaccio" {
  type              = "ingress"
  security_group_id = aws_security_group.verdaccio.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-stage-ssh-bastion.id
}

resource "aws_security_group_rule" "verdaccio-instance-from-verdaccio-alb" {
  type              = "ingress"
  security_group_id = aws_security_group.verdaccio.id
  from_port         = local.verdaccio_port
  to_port           = local.verdaccio_port
  protocol          = "tcp"

  source_security_group_id = aws_security_group.verdaccio-alb.id
}

resource "aws_security_group" "verdaccio-alb" {
  name        = "verdaccio-alb-sg"
  description = "SG for verdaccio application load balancer"
  vpc_id      = data.aws_vpc.content-stage.id
  tags = {
    "Name" = "verdaccio-alb"
  }
}

resource "aws_security_group_rule" "verdaccio-alb-to-verdaccio-instance" {
  type              = "egress"
  security_group_id = aws_security_group.verdaccio-alb.id
  from_port         = local.verdaccio_port
  to_port           = local.verdaccio_port
  protocol          = "tcp"

  source_security_group_id = aws_security_group.verdaccio.id
}

resource "aws_security_group_rule" "verdaccio-alb-from-anywhere-https" {
  type              = "ingress"
  security_group_id = aws_security_group.verdaccio-alb.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_lb" "verdaccio" {
  name                       = "verdaccio-ec2"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.verdaccio-alb.id]
  subnets                    = data.aws_subnets.content-stage-public.ids
  enable_deletion_protection = true
}

resource "aws_lb_target_group" "verdaccio" {
  name     = "verdaccio-alb-tg"
  port     = local.verdaccio_port
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.content-stage.id
  health_check {
    healthy_threshold   = 2
    interval            = 15
    path                = "/-/ping"
    unhealthy_threshold = 2
  }
}

resource "aws_lb_target_group_attachment" "verdaccio" {
  target_group_arn = aws_lb_target_group.verdaccio.arn
  target_id        = aws_instance.verdaccio.id
  port             = local.verdaccio_port
}

data "aws_acm_certificate" "frever-api" {
  domain   = "frever-api.com"
  statuses = ["ISSUED"]
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.verdaccio.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-1-2017-01"
  certificate_arn   = data.aws_acm_certificate.frever-api.arn
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "verdaccio" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.verdaccio.arn
  }

  condition {
    host_header {
      values = ["package-registry.frever-api.com"]
    }
  }

  condition {
    path_pattern {
      values = ["/*"]
    }
  }
}

data "aws_route53_zone" "frever-api" {
  name = "frever-api.com"
}

resource "aws_route53_record" "package-registry" {
  zone_id = data.aws_route53_zone.frever-api.zone_id
  name    = "package-registry.frever-api.com"
  type    = "A"
  alias {
    name                   = aws_lb.verdaccio.dns_name
    zone_id                = aws_lb.verdaccio.zone_id
    evaluate_target_health = true
  }
}
