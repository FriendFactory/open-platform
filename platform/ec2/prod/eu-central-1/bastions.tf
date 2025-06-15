# data "aws_ami" "amazon-linux-2" {
#   owners = ["amazon"]
#   filter {
#     name   = "name"
#     values = ["amzn2-ami-kernel-5.10-hvm-2.0.20221004.0-arm64-gp2"]
#   }
#   filter {
#     name   = "architecture"
#     values = ["arm64"]
#   }
#   filter {
#     name   = "root-device-type"
#     values = ["ebs"]
#   }
# }

data "aws_vpc" "vpc" {
  tags = {
    Name = var.vpc_name
  }
}

data "aws_subnet" "vpc-public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  tags = {
    Name = "${var.vpc_name}-public-${var.region}*a"
  }
}

data "aws_security_group" "bastion-limited-access" {
  name   = "${var.vpc_name}-bastion-limited-access"
  vpc_id = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-bastion-limited-access"
  }
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "bastion-limited-access" {
  name = "${var.vpc_name}-bastion-limited-access"
  role = aws_iam_role.bastion-limited-access.name
}

resource "aws_iam_role" "bastion-limited-access" {
  name = "${var.vpc_name}-bastion-limited-access"
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
            "Sid": "Ec2Assume"
        }
    ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "bastion-limited-access-attach" {
  role       = aws_iam_role.bastion-limited-access.name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_instance" "content-prod-bastion-limited-access" {
  # ami                     = data.aws_ami.amazon-linux-2.id
  ami                     = "ami-0b159ae6defdf31ab"
  instance_type           = "t4g.nano"
  key_name                = "platform"
  subnet_id               = data.aws_subnet.vpc-public.id
  vpc_security_group_ids  = [data.aws_security_group.bastion-limited-access.id]
  iam_instance_profile    = aws_iam_instance_profile.bastion-limited-access.name
  disable_api_termination = true

  tags = {
    Name  = "${var.vpc_name}-bastion-limited-access"
    Owner = "platform"
  }
}

data "aws_eip" "eip-for-content-prod-bastion-limited-access-instance" {
  public_ip = "18.198.144.161"
}

resource "aws_eip_association" "content-prod-bastion-limited-access-eip" {
  instance_id   = aws_instance.content-prod-bastion-limited-access.id
  allocation_id = data.aws_eip.eip-for-content-prod-bastion-limited-access-instance.id
}
