data "aws_vpc" "vpc" {
  tags = {
    Name = "prod"
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
data "aws_region" "current" {}

data "aws_subnet" "vpc-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  tags = {
    Name = "prod-private-${var.region}*a"
  }
}

data "aws_ami" "deep-learning-oss-nvidia-pytorch-amazon-linux-2023" {
  owners      = ["amazon"]
  most_recent = true
  filter {
    name   = "name"
    values = ["Deep Learning OSS Nvidia Driver AMI GPU PyTorch 2.6* (Amazon Linux 2023)*"]
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

data "aws_iam_policy" "allow-access-s3-bucket-in-frever-aws-account" {
  name = "allow-access-s3-bucket-in-frever-aws-account"
}

data "aws_security_group" "instance-connect-endpoint-sg" {
  vpc_id = data.aws_vpc.vpc.id
  name   = "instance-connect-endpoint-sg"
}

resource "aws_security_group" "comfyui-dev-sg" {
  vpc_id = data.aws_vpc.vpc.id
  name   = "comfyui-dev-sg"
  tags = {
    Name = "comfyui-dev-sg"
  }
}

resource "aws_security_group_rule" "comfyui-dev-sg-https-out" {
  security_group_id = aws_security_group.comfyui-dev-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "comfyui-dev-sg-http-out-within-vpcs" {
  security_group_id = aws_security_group.comfyui-dev-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "comfyui-dev-sg-ec2-instance-connect-endpoint-in" {
  security_group_id        = aws_security_group.comfyui-dev-sg.id
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.instance-connect-endpoint-sg.id
}

resource "aws_security_group_rule" "comfyui-dev-sg-comfy-ui-in-for-machine-learning-eu-prod-vpc" {
  security_group_id = aws_security_group.comfyui-dev-sg.id
  type              = "ingress"
  from_port         = 8188
  to_port           = 8188
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_iam_role" "comfyui-dev-role" {
  name = "comfyui-dev-role"
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

resource "aws_iam_role_policy_attachments_exclusive" "ssm-managed" {
  role_name   = aws_iam_role.comfyui-dev-role.name
  policy_arns = [data.aws_iam_policy.allow-access-s3-bucket-in-frever-aws-account.arn]
}

resource "aws_iam_instance_profile" "comfyui-dev" {
  name = "comfyui-dev"
  role = aws_iam_role.comfyui-dev-role.name
}

resource "aws_iam_role_policy" "comfyui-dev-policy" {
  name = "comfyui-dev-policy"
  role = aws_iam_role.comfyui-dev-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = "ssm:DescribeParameters"
        Sid      = "DescribeParameters"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "ssm:GetParameterHistory",
          "ssm:GetParametersByPath",
          "ssm:GetParameters",
          "ssm:GetParameter",
        ]
        Sid      = "UseSsmParameters"
        Effect   = "Allow"
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/comfyui-dev/*", ]
      },
      {
        Action = [
          "s3:*"
        ]
        Sid    = "S3Permissions"
        Effect = "Allow"
        Resource = [
          "arn:aws:s3:::frever-comfyui-output-dev/*",
          "arn:aws:s3:::frever-comfyui-output-dev"
        ]
      },
    ]
  })
}

resource "aws_instance" "comfyui-instance" {
  # ami                     = data.aws_ami.deep-learning-oss-nvidia-pytorch-amazon-linux-2023.id
  ami                     = "ami-0d9a120181c9b87c3"
  instance_type           = "g6e.xlarge"
  key_name                = "platform-key"
  subnet_id               = data.aws_subnet.vpc-private.id
  vpc_security_group_ids  = [aws_security_group.comfyui-dev-sg.id]
  iam_instance_profile    = aws_iam_instance_profile.comfyui-dev.name
  disable_api_termination = true

  user_data = <<EOF
Content-Type: multipart/mixed; boundary="//"
MIME-Version: 1.0

--//
Content-Type: text/cloud-config; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment;
 filename="cloud-config.txt"

#cloud-config
cloud_final_modules:
- [scripts-user, always]
--//
Content-Type: text/x-shellscript; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment; filename="userdata.txt"

#!/bin/bash
if (($(cat /proc/swaps | wc -l) == 1)); then
  mkswap /dev/nvme1n1
  swapon /dev/nvme1n1
fi
--//--
EOF

  tags = {
    Name  = "comfyui-dev"
    Owner = "platform"
    Usage = "ComfyUi-Video"
  }
}

# data "aws_ami" "frever-comfyui-ami" {
#   most_recent = true
#   name_regex  = "comfyui-amli-2023*"
#   owners      = ["self"]
#
#   filter {
#     name   = "name"
#     values = ["*comfyui-amli-2023*"]
#   }
#
#   filter {
#     name   = "root-device-type"
#     values = ["ebs"]
#   }
#
#   filter {
#     name   = "virtualization-type"
#     values = ["hvm"]
#   }
# }

resource "aws_instance" "comfyui-photo-transformation-instance" {
  # ami                     = data.aws_ami.frever-comfyui-ami.id
  ami                     = "ami-0c42d96b689a83c97"
  instance_type           = "g6e.xlarge"
  key_name                = "platform-key"
  subnet_id               = data.aws_subnet.vpc-private.id
  vpc_security_group_ids  = [aws_security_group.comfyui-dev-sg.id]
  iam_instance_profile    = aws_iam_instance_profile.comfyui-dev.name
  disable_api_termination = true

  user_data = <<EOF
Content-Type: multipart/mixed; boundary="//"
MIME-Version: 1.0

--//
Content-Type: text/cloud-config; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment;
 filename="cloud-config.txt"

#cloud-config
cloud_final_modules:
- [scripts-user, always]
--//
Content-Type: text/x-shellscript; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment; filename="userdata.txt"

#!/bin/bash
if (($(cat /proc/swaps | wc -l) == 1)); then
  mkswap /dev/nvme1n1
  swapon /dev/nvme1n1
fi
--//--
EOF

  tags = {
    Name  = "comfyui-photo-dev"
    Owner = "platform"
    Usage = "ComfyUi-Photo"
  }
}


resource "aws_instance" "comfyui-photo-transformation-instance-2" {
  # ami                     = data.aws_ami.deep-learning-oss-nvidia-pytorch-amazon-linux-2023.id
  ami                     = "ami-09aa5d5188d442d2d"
  instance_type           = "g6e.xlarge"
  key_name                = "platform-key"
  subnet_id               = data.aws_subnet.vpc-private.id
  vpc_security_group_ids  = [aws_security_group.comfyui-dev-sg.id]
  iam_instance_profile    = aws_iam_instance_profile.comfyui-dev.name
  disable_api_termination = true

  user_data = <<EOF
Content-Type: multipart/mixed; boundary="//"
MIME-Version: 1.0

--//
Content-Type: text/cloud-config; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment;
 filename="cloud-config.txt"

#cloud-config
cloud_final_modules:
- [scripts-user, always]
--//
Content-Type: text/x-shellscript; charset="us-ascii"
MIME-Version: 1.0
Content-Transfer-Encoding: 7bit
Content-Disposition: attachment; filename="userdata.txt"

#!/bin/bash
if (($(cat /proc/swaps | wc -l) == 1)); then
  mkswap /dev/nvme1n1
  swapon /dev/nvme1n1
fi
--//--
EOF

  tags = {
    Name  = "comfyui-photo-dev-2"
    Owner = "platform"
    Usage = "ComfyUi-Photo"
  }
}

