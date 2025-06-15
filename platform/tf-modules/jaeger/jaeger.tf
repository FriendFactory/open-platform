data "aws_ami" "amazon-linux-2" {
  owners      = ["amazon"]
  most_recent = true
  filter {
    name   = "name"
    values = ["amzn2-ami-kernel-5.10-hvm-2.0.*-arm64-gp2"]
  }
  filter {
    name   = "architecture"
    values = ["arm64"]
  }
  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

data "aws_ami" "al-2023-ami" {
  owners      = ["amazon"]
  most_recent = true
  filter {
    name   = "name"
    values = ["al2023-ami-2023*-kernel-*"]
  }
  filter {
    name   = "architecture"
    values = ["arm64"]
  }
  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

data "aws_vpc" "vpc" {
  tags = {
    Name = var.vpc_name
  }
}

data "aws_subnets" "vpc-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  tags = {
    Name = "${var.vpc_name}-private*"
  }
}

resource "aws_security_group" "jaeger" {
  name        = "${var.vpc_name}-jaeger"
  description = "SG for Jaeger in VPC ${var.vpc_name}"
  vpc_id      = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-jaeger"
  }
}

data "aws_security_group" "ssh-bastion" {
  name   = "${var.vpc_name}-ssh-bastion"
  vpc_id = data.aws_vpc.vpc.id
}

resource "aws_security_group_rule" "jaeger-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "ssh-bastion-to-jaeger" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.ssh-bastion.id
}

resource "aws_security_group_rule" "cassandra-7000-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 7000
  to_port           = 7000
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-7000-out" {
  type              = "egress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 7000
  to_port           = 7000
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-7001-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 7001
  to_port           = 7001
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-7001-out" {
  type              = "egress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 7001
  to_port           = 7001
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-9160-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 9160
  to_port           = 9160
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-9160-out" {
  type              = "egress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 9160
  to_port           = 9160
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "cassandra-9042-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 9042
  to_port           = 9042
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "cassandra-9042-out" {
  count             = var.jaeger_instance_count == 3 ? 1 : 0
  type              = "egress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = 9042
  to_port           = 9042
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jaeger.id
}

resource "aws_security_group_rule" "jaeger-16686-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_query_ui_port
  to_port           = local.jaeger_query_ui_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

# resource "aws_security_group_rule" "jaeger-16686-out" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger.id
#   from_port         = local.jaeger_query_ui_port
#   to_port           = local.jaeger_query_ui_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger-lb[0].id
# }

resource "aws_security_group_rule" "jaeger-14268-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_collector_port
  to_port           = local.jaeger_collector_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "jaeger-14269-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_admin_port
  to_port           = local.jaeger_admin_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "jaeger-otlp-grpc-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_otlp_grpc_port
  to_port           = local.jaeger_otlp_grpc_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "jaeger-otlp-http-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_otlp_http_port
  to_port           = local.jaeger_otlp_http_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "jaeger-otlp-healthcheck-in" {
  type              = "ingress"
  security_group_id = aws_security_group.jaeger.id
  from_port         = local.jaeger_otlp_healthcheck_port
  to_port           = local.jaeger_otlp_healthcheck_port
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "jaeger" {
  name = "${var.vpc_name}-jaeger"
  role = aws_iam_role.jaeger.name
}

resource "aws_iam_role" "jaeger" {
  name = "${var.vpc_name}-jaeger"
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
}

resource "aws_iam_role_policy_attachment" "jaeger-attach" {
  role       = aws_iam_role.jaeger.name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_instance" "jaeger" {
  count                   = var.jaeger_instance_count
  ami                     = var.jaeger_instance_ami != null ? var.jaeger_instance_ami : var.use_amazon_linux_2023 ? data.aws_ami.al-2023-ami.id : data.aws_ami.amazon-linux-2.id
  instance_type           = var.jaeger_instance_type
  key_name                = "platform"
  subnet_id               = data.aws_subnets.vpc-private.ids[count.index]
  vpc_security_group_ids  = [aws_security_group.jaeger.id]
  iam_instance_profile    = aws_iam_instance_profile.jaeger.name
  disable_api_termination = true

  tags = {
    Name  = "${var.vpc_name}-jaeger-${count.index + 1}"
    Owner = "platform"
  }
}

data "aws_route53_zone" "frever-internal" {
  name         = "frever-internal.com"
  private_zone = true
}

resource "aws_route53_record" "jaeger-collector-instance" {
  count   = var.jaeger_instance_count == 1 ? 1 : 0
  zone_id = data.aws_route53_zone.frever-internal.zone_id
  name    = "${var.vpc_name}-jaeger.${data.aws_route53_zone.frever-internal.name}"
  type    = "A"
  ttl     = "300"
  records = [aws_instance.jaeger[0].private_ip]
}

# resource "aws_lb" "jaeger" {
#   count                      = var.jaeger_instance_count == 3 ? 1 : 0
#   name                       = "${var.vpc_name}-jaeger"
#   internal                   = "true"
#   load_balancer_type         = "application"
#   subnets                    = data.aws_subnets.vpc-private.ids
#   enable_deletion_protection = true
#   security_groups            = [aws_security_group.jaeger-lb[0].id]
# }

locals {
  jaeger_collector_port        = 14268
  jaeger_admin_port            = 14269
  jaeger_query_ui_port         = 16686
  jaeger_otlp_grpc_port        = 4317
  jaeger_otlp_http_port        = 4318
  jaeger_otlp_healthcheck_port = 8888
}

# resource "aws_security_group" "jaeger-lb" {
#   count       = var.jaeger_instance_count == 3 ? 1 : 0
#   name        = "${var.vpc_name}-jaeger-lb"
#   description = "SG for Jaeger Local Balancer in VPC ${var.vpc_name}"
#   vpc_id      = data.aws_vpc.vpc.id
#   tags = {
#     "Name" = "${var.vpc_name}-jaeger-lb"
#   }
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-collector" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_collector_port
#   to_port           = local.jaeger_collector_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-query-ui" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_query_ui_port
#   to_port           = local.jaeger_query_ui_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-admin" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_admin_port
#   to_port           = local.jaeger_admin_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-otlp-grpc" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_otlp_grpc_port
#   to_port           = local.jaeger_otlp_grpc_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-otlp-http" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_otlp_http_port
#   to_port           = local.jaeger_otlp_http_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-to-jaeger-otlp-healthcheck" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "egress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_otlp_healthcheck_port
#   to_port           = local.jaeger_otlp_healthcheck_port
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.jaeger.id
# }
#
# resource "aws_security_group_rule" "jaeger-lb-jaeger-collector-in-vpc" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "ingress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_collector_port
#   to_port           = local.jaeger_collector_port
#   protocol          = "tcp"
#
#   cidr_blocks = [data.aws_vpc.vpc.cidr_block]
# }
#
# resource "aws_security_group_rule" "jaeger-lb-jaeger-query-ui-in-vpc" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "ingress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_query_ui_port
#   to_port           = local.jaeger_query_ui_port
#   protocol          = "tcp"
#
#   cidr_blocks = [data.aws_vpc.vpc.cidr_block]
# }
#
# resource "aws_security_group_rule" "jaeger-lb-jaeger-otlp-grpc-in-vpc" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "ingress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_otlp_grpc_port
#   to_port           = local.jaeger_otlp_grpc_port
#   protocol          = "tcp"
#
#   cidr_blocks = [data.aws_vpc.vpc.cidr_block]
# }
#
# resource "aws_security_group_rule" "jaeger-lb-jaeger-otlp-http-in-vpc" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   type              = "ingress"
#   security_group_id = aws_security_group.jaeger-lb[0].id
#   from_port         = local.jaeger_otlp_http_port
#   to_port           = local.jaeger_otlp_http_port
#   protocol          = "tcp"
#
#   cidr_blocks = [data.aws_vpc.vpc.cidr_block]
# }
#
# resource "aws_lb_target_group" "jaeger-collector" {
#   name     = "jaeger-collector"
#   count    = var.jaeger_instance_count == 3 ? 1 : 0
#   port     = local.jaeger_collector_port
#   protocol = "HTTP"
#   vpc_id   = data.aws_vpc.vpc.id
#   health_check {
#     healthy_threshold   = 2
#     interval            = 15
#     port                = local.jaeger_admin_port
#     path                = "/ping"
#     unhealthy_threshold = 2
#   }
# }
#
# resource "aws_lb_target_group_attachment" "jaeger-collector" {
#   count            = var.jaeger_instance_count == 3 ? 3 : 0
#   target_group_arn = aws_lb_target_group.jaeger-collector[0].arn
#   target_id        = aws_instance.jaeger[count.index].id
#   port             = local.jaeger_collector_port
# }
#
# resource "aws_lb_listener" "jaeger-collector" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   load_balancer_arn = aws_lb.jaeger[0].arn
#   port              = local.jaeger_collector_port
#   protocol          = "HTTP"
#   default_action {
#     type             = "forward"
#     target_group_arn = aws_lb_target_group.jaeger-collector[0].arn
#   }
# }
#
# resource "aws_lb_target_group" "otel-collector" {
#   name     = "otel-collector"
#   count    = var.jaeger_instance_count == 3 ? 1 : 0
#   port     = local.jaeger_otlp_http_port
#   protocol = "HTTP"
#   vpc_id   = data.aws_vpc.vpc.id
#   health_check {
#     healthy_threshold   = 2
#     interval            = 15
#     port                = local.jaeger_otlp_healthcheck_port
#     path                = "/metrics"
#     unhealthy_threshold = 2
#   }
# }
#
# resource "aws_lb_target_group_attachment" "otel-collector" {
#   count            = var.jaeger_instance_count == 3 ? 3 : 0
#   target_group_arn = aws_lb_target_group.otel-collector[0].arn
#   target_id        = aws_instance.jaeger[count.index].id
#   port             = local.jaeger_otlp_http_port
# }
#
# resource "aws_lb_listener" "otel-collector" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   load_balancer_arn = aws_lb.jaeger[0].arn
#   port              = local.jaeger_otlp_http_port
#   protocol          = "HTTP"
#   default_action {
#     type             = "forward"
#     target_group_arn = aws_lb_target_group.otel-collector[0].arn
#   }
# }
#
# resource "aws_lb_target_group" "jaeger-query-ui" {
#   name     = "jaeger-query-ui"
#   count    = var.jaeger_instance_count == 3 ? 1 : 0
#   port     = local.jaeger_query_ui_port
#   protocol = "HTTP"
#   vpc_id   = data.aws_vpc.vpc.id
#   health_check {
#     healthy_threshold   = 2
#     interval            = 15
#     port                = local.jaeger_admin_port
#     path                = "/ping"
#     unhealthy_threshold = 2
#   }
# }
#
# resource "aws_lb_target_group_attachment" "jaeger-query-ui" {
#   count            = var.jaeger_instance_count == 3 ? 3 : 0
#   target_group_arn = aws_lb_target_group.jaeger-query-ui[0].arn
#   target_id        = aws_instance.jaeger[count.index].id
#   port             = local.jaeger_query_ui_port
# }
#
# resource "aws_lb_listener" "jaeger-query-ui" {
#   count             = var.jaeger_instance_count == 3 ? 1 : 0
#   load_balancer_arn = aws_lb.jaeger[0].arn
#   port              = local.jaeger_query_ui_port
#   protocol          = "HTTP"
#   default_action {
#     type             = "forward"
#     target_group_arn = aws_lb_target_group.jaeger-query-ui[0].arn
#   }
# }

# resource "aws_route53_record" "jaeger-alb" {
#   count   = var.jaeger_instance_count == 3 ? 1 : 0
#   zone_id = data.aws_route53_zone.frever-internal.zone_id
#   name    = "${var.vpc_name}-jaeger.${data.aws_route53_zone.frever-internal.name}"
#   type    = "A"
#   alias {
#     name                   = aws_lb.jaeger[0].dns_name
#     zone_id                = aws_lb.jaeger[0].zone_id
#     evaluate_target_health = true
#   }
# }

resource "aws_route53_record" "jaeger-ec2" {
  count   = var.jaeger_instance_count == 3 ? 1 : 0
  zone_id = data.aws_route53_zone.frever-internal.zone_id
  name    = "${var.vpc_name}-jaeger.${data.aws_route53_zone.frever-internal.name}"
  type    = "A"
  ttl     = 60
  records = aws_instance.jaeger[*].private_ip
}
