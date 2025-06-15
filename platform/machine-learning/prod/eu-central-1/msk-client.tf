resource "aws_key_pair" "platform" {
  key_name   = "platform-key"
  public_key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEuLGJKIWjW2YcdjYmIYLbpBxvupzRS9Die/SbiK8EiN"
}

resource "aws_security_group" "msk-ec2-client-sg" {
  vpc_id = aws_vpc.prod.id
  name   = "msk-ec2-client-sg"
  tags = {
    Name = "msk-ec2-client-sg"
  }
}

resource "aws_security_group_rule" "msk-ec2-client-sg-https-out" {
  security_group_id = aws_security_group.msk-ec2-client-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "msk-ec2-client-sg-http-out-with-vpcs" {
  security_group_id = aws_security_group.msk-ec2-client-sg.id
  type              = "egress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.frever-prod.cidr_block, aws_vpc.prod.cidr_block]
}

resource "aws_security_group_rule" "msk-ec2-client-sg-out-within-vpc" {
  security_group_id = aws_security_group.msk-ec2-client-sg.id
  type              = "egress"
  from_port         = 1024 
  to_port           = 65535
  protocol          = "tcp"
  cidr_blocks       = [aws_vpc.prod.cidr_block]
}

resource "aws_security_group_rule" "msk-ec2-client-sg-access-kafka" {
  security_group_id        = aws_security_group.msk-ec2-client-sg.id
  type                     = "egress"
  from_port                = 9092
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.msk-sg.id
}

data "aws_redshift_cluster" "redshift-analytics" {
  provider           = aws.frever
  cluster_identifier = "redshift-analytics"
}

resource "aws_security_group_rule" "msk-ec2-client-sg-ssh-in" {
  security_group_id = aws_security_group.msk-ec2-client-sg.id
  type              = "ingress"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  # VPC CIDR (for ec2 instance connect endpoint)
  cidr_blocks = [aws_vpc.prod.cidr_block]
}

resource "aws_security_group_rule" "msk-ec2-client-sg-ec2-instance-connect-endpoint-in" {
  security_group_id        = aws_security_group.msk-ec2-client-sg.id
  type                     = "ingress"
  from_port                = 22
  to_port                  = 22
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.instance-connect-endpoint-sg.id
}

resource "aws_security_group_rule" "msk-ec2-client-to-msk-sg-kafka" {
  security_group_id        = aws_security_group.msk-sg.id
  type                     = "ingress"
  from_port                = 9092
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.msk-ec2-client-sg.id
}

data "aws_iam_policy" "allow-access-s3-bucket-in-frever-aws-account" {
  name = "allow-access-s3-bucket-in-frever-aws-account"
}

resource "aws_iam_role" "msk-ec2-client-role" {
  name = "msk-ec2-client-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Sid    = "KafkaAssume"
        Effect = "Allow"
        Principal = {
          Service = "kafkaconnect.amazonaws.com"
        }
      },
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

resource "aws_iam_role_policy_attachments_exclusive" "managed-policy" {
  role_name   = aws_iam_role.msk-ec2-client-role.name
  policy_arns = [data.aws_iam_policy.allow-access-s3-bucket-in-frever-aws-account.arn]
}

resource "aws_iam_instance_profile" "msk-ec2-client" {
  name = "msk-ec2-client"
  role = aws_iam_role.msk-ec2-client-role.name
}

resource "aws_iam_role_policy" "msk-ec2-client-policy" {
  name = "msk-ec2-client-role-policy"
  role = aws_iam_role.msk-ec2-client-role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "kafka-cluster:Connect",
          "kafka-cluster:AlterCluster",
          "kafka-cluster:DescribeCluster"
        ]
        Sid      = "MskClusterPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:cluster/*/*"
      },
      {
        Action = [
          "kafka-cluster:*Topic*",
          "kafka-cluster:WriteData",
          "kafka-cluster:ReadData"
        ]
        Sid      = "MskTopicPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:topic/*/*"
      },
      {
        Action = [
          "kafka-cluster:AlterGroup",
          "kafka-cluster:DescribeGroup"
        ]
        Sid      = "MskGroupPermission"
        Effect   = "Allow"
        Resource = "arn:aws:kafka:eu-central-1:${data.aws_caller_identity.current.account_id}:group/*/*"
      },
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/postgresql-rds/*", ]
      },
    ]
  })
}

# data "aws_ami" "al-2023-ami" {
#   owners      = ["amazon"]
#   most_recent = true
#   filter {
#     name = "name"
#     # values = ["al2023-ami-2023*-kernel-*"]
#     values = ["al2023-ami-2023*20230419*-kernel-*"]
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

data "aws_security_group" "postgresql-rds-replica-client-sg" {
  vpc_id = aws_vpc.prod.id
  name   = "content-prod-db-replica-client"
}

data "aws_security_group" "dev-ml-service-rds-pg-client" {
  name   = "dev-ml-service-rds-client-sg"
  vpc_id = aws_vpc.prod.id
}

data "aws_security_group" "ml-service-ixia-prod-rds-pg-client" {
  name   = "ixia-prod-ml-service-rds-client-sg"
  vpc_id = aws_vpc.prod.id
}

data "aws_security_group" "ixia-prod-rds-client-sg-for-machine-learning" {
  name   = "ixia-prod-rds-client-sg-for-machine-learning"
  vpc_id = aws_vpc.prod.id
}

data "aws_security_group" "frever-dev-vpc-rds-client" {
  vpc_id = aws_vpc.prod.id
  name   = "dev-rds-client-sg-for-machine-learning"
}

resource "aws_eip" "msk-ec2-client-eip" {
  instance = aws_instance.msk-ec2-client.id
  domain   = "vpc"
  tags = {
    Name  = "msk-ec2-client"
    Owner = "platform"
  }
}

resource "aws_instance" "msk-ec2-client" {
  # ami                     = data.aws_ami.al-2023-ami.id
  ami                     = "ami-09109a44d85017c32"
  instance_type           = "t4g.small"
  key_name                = aws_key_pair.platform.key_name
  subnet_id               = aws_subnet.prod-public[0].id
  vpc_security_group_ids  = [aws_security_group.msk-ec2-client-sg.id, data.aws_security_group.postgresql-rds-replica-client-sg.id, data.aws_security_group.frever-dev-vpc-rds-client.id, data.aws_security_group.dev-ml-service-rds-pg-client.id, data.aws_security_group.ml-service-ixia-prod-rds-pg-client.id, data.aws_security_group.ixia-prod-rds-client-sg-for-machine-learning.id]
  iam_instance_profile    = aws_iam_instance_profile.msk-ec2-client.name
  disable_api_termination = true

  tags = {
    Name  = "msk-ec2-client"
    Owner = "platform"
  }
}

resource "aws_ec2_instance_connect_endpoint" "instance-connect-endpoint" {
  subnet_id          = aws_subnet.prod-private[0].id
  preserve_client_ip = false
  security_group_ids = [aws_security_group.instance-connect-endpoint-sg.id]
}

resource "aws_security_group" "instance-connect-endpoint-sg" {
  vpc_id = aws_vpc.prod.id
  name   = "instance-connect-endpoint-sg"
  tags = {
    Name = "instance-connect-endpoint-sg"
  }
}

resource "aws_security_group_rule" "instance-connect-endpoint-sg-ssh-out" {
  security_group_id = aws_security_group.instance-connect-endpoint-sg.id
  type              = "egress"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.frever-prod.cidr_block, aws_vpc.prod.cidr_block]
}

