data "aws_key_pair" "platform" {
  key_name = "platform"
}

resource "aws_iam_role" "bastion" {
  name = "${var.vpc_name}-bastion"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Sid    = "Ec2Assume"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "bastion" {
  name = "${var.vpc_name}-bastion"
  role = aws_iam_role.bastion.name
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

resource "aws_eip" "bastion-eip" {
  instance = aws_instance.bastion.id
  domain   = "vpc"
  tags = {
    Name  = "${var.vpc_name}-bastion"
    Owner = "platform"
  }
}

# name needs to fulfill lookup in jaeger and observability modules
resource "aws_security_group" "bastion-sg" {
  vpc_id = aws_vpc.vpc.id
  name   = "${var.vpc_name}-ssh-bastion"
  tags = {
    Name = "${var.vpc_name}-ssh-bastion"
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "bastion-sg-https-out" {
  security_group_id = aws_security_group.bastion-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "bastion-sg-all-out-within-vpc" {
  security_group_id = aws_security_group.bastion-sg.id
  type              = "egress"
  from_port         = 0
  to_port           = 65535
  protocol          = "-1"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "bastion-sg-ec2-instance-connect-endpoint-in" {
  security_group_id        = aws_security_group.bastion-sg.id
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.instance-connect-endpoint-sg.id
}

resource "aws_instance" "bastion" {
  ami                     = var.bastion_instance_ami != "" ? var.bastion_instance_ami : data.aws_ami.al-2023-ami.id
  instance_type           = "t4g.nano"
  key_name                = data.aws_key_pair.platform.key_name
  subnet_id               = aws_subnet.public[0].id
  vpc_security_group_ids  = [aws_security_group.bastion-sg.id, aws_security_group.redis-client-sg.id, aws_security_group.rds-client-sg.id]
  iam_instance_profile    = aws_iam_instance_profile.bastion.name
  disable_api_termination = true

  tags = {
    Name  = "${var.vpc_name}-bastion"
    Owner = "platform"
  }
}

resource "aws_ec2_instance_connect_endpoint" "instance-connect-endpoint" {
  subnet_id          = aws_subnet.private[0].id
  preserve_client_ip = false
  security_group_ids = [aws_security_group.instance-connect-endpoint-sg.id]
}

resource "aws_security_group" "instance-connect-endpoint-sg" {
  vpc_id = aws_vpc.vpc.id
  name   = "${var.vpc_name}-instance-connect-endpoint-sg"
  tags = {
    Name = "${var.vpc_name}-instance-connect-endpoint-sg"
  }
}

resource "aws_security_group_rule" "instance-connect-endpoint-sg-ssh-out" {
  security_group_id = aws_security_group.instance-connect-endpoint-sg.id
  type              = "egress"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

output "bastion-instance-id" {
  description = "The bastion instance id, for ec2 instance connect endpoint."
  value       = aws_instance.bastion.id
}

resource "aws_iam_instance_profile" "nat" {
  name = "${var.vpc_name}-nat"
  role = aws_iam_role.nat.name
}

resource "aws_iam_role" "nat" {
  name = "${var.vpc_name}-nat"
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

resource "aws_instance" "nat" {
  count                   = var.use_nat_instance ? var.nat_count : 0
  ami                     = data.aws_ami.al-2023-ami.id
  instance_type           = "t4g.small"
  key_name                = "platform"
  subnet_id               = aws_subnet.public[0].id
  vpc_security_group_ids  = [aws_security_group.nat-sg[0].id]
  iam_instance_profile    = aws_iam_instance_profile.nat.name
  disable_api_termination = true
  source_dest_check       = false

  tags = {
    Name  = "${var.vpc_name}-nat"
    Owner = "platform"
  }
}

resource "aws_security_group" "nat-sg" {
  count  = var.use_nat_instance ? 1 : 0
  vpc_id = aws_vpc.vpc.id
  name   = "${var.vpc_name}-nat-sg"
  tags = {
    Name = "${var.vpc_name}-nat-sg"
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "nat-sg-ec2-instance-connect-endpoint-in" {
  count                    = var.use_nat_instance ? 1 : 0
  security_group_id        = aws_security_group.nat-sg[0].id
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.instance-connect-endpoint-sg.id
}

resource "aws_security_group_rule" "nat-sg-all-out-within-vpc" {
  count             = var.use_nat_instance ? 1 : 0
  security_group_id = aws_security_group.nat-sg[0].id
  type              = "egress"
  from_port         = 0
  to_port           = 65535
  protocol          = "-1"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "nat-sg-all-in-within-vpc" {
  count             = var.use_nat_instance ? 1 : 0
  security_group_id = aws_security_group.nat-sg[0].id
  type              = "ingress"
  from_port         = 0
  to_port           = 65535
  protocol          = "-1"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "nat-sg-https-out" {
  count             = var.use_nat_instance ? 1 : 0
  security_group_id = aws_security_group.nat-sg[0].id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "nat-sg-http-out" {
  count             = var.use_nat_instance ? 1 : 0
  security_group_id = aws_security_group.nat-sg[0].id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "nat-sg-icmp-out" {
  count             = var.use_nat_instance ? 1 : 0
  security_group_id = aws_security_group.nat-sg[0].id
  type              = "egress"
  from_port         = 0
  to_port           = 65535
  protocol          = "icmp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

output "nat-instance-id" {
  description = "The nat instance id"
  value       = aws_instance.nat.*.id
}

