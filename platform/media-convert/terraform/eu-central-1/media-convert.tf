data "aws_ami" "amazon-linux-2" {
  owners = ["amazon"]
  filter {
    name   = "name"
    values = ["amzn2-ami-kernel-5.10-hvm-2.0.20221103.3-arm64-gp2"]
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

data "aws_vpc" "content-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_subnets" "content-prod-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-prod.id]
  }
  tags = {
    Name = "content-prod-private-eu-central-1a"
  }
}

data "aws_security_group" "content-prod-ssh-bastion" {
  name   = "content-prod-ssh-bastion"
  vpc_id = data.aws_vpc.content-prod.id
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_security_group" "media-convert" {
  name        = "media-convert-sg"
  description = "SG for media-convert"
  vpc_id      = data.aws_vpc.content-prod.id
  tags        = {
    "Name" = "media-convert"
  }
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-to-media-convert" {
  type              = "ingress"
  security_group_id = aws_security_group.media-convert.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-prod-ssh-bastion.id
}

resource "aws_security_group_rule" "media-convert-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.media-convert.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_iam_instance_profile" "platform-media-convert" {
  name = "platform-media-convert"
  role = aws_iam_role.platform-media-convert.name
}

resource "aws_iam_role" "platform-media-convert" {
  name = "platform-media-convert"
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

resource "aws_instance" "instance" {
  ami                     = data.aws_ami.amazon-linux-2.id
  instance_type           = "c6g.xlarge"
  key_name                = "platform"
  subnet_id               = data.aws_subnets.content-prod-private.ids[0]
  vpc_security_group_ids  = [aws_security_group.media-convert.id]
  iam_instance_profile    = aws_iam_instance_profile.platform-media-convert.name
  disable_api_termination = true

  tags = {
    Name  = "platform-media-convert"
    Owner = "platform"
  }
}
