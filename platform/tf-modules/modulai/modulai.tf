locals {
  eks-clusters = {
    content-prod  = "oidc.eks.eu-central-1.amazonaws.com/id/9BC2EECFE5BDA6BAE0666D54701E5A2A",
    content-stage = "oidc.eks.eu-central-1.amazonaws.com/id/A01450063EA0ADDD801ADF701C18ED27",
    content-test  = "oidc.eks.eu-central-1.amazonaws.com/id/E21A6985A828AABB8923A742C957B12B",
    dev-1         = "oidc.eks.eu-central-1.amazonaws.com/id/F98AFA2F57998F810C7CD6122BB43286",
    dev-2         = "oidc.eks.eu-central-1.amazonaws.com/id/F9CD4C83ACCFD1A47C4B23F568F857AB",
    dev           = "oidc.eks.eu-central-1.amazonaws.com/id/BE823FA14947E637022AD48174D47F52",
  }
  s3-arn-list = length(var.s3_bucket_cross_env_access) == 0 ? tolist([aws_s3_bucket.modulai-s3-bucket.arn]) : concat([aws_s3_bucket.modulai-s3-bucket.arn], values(data.aws_s3_bucket.s3-cross-env)[*].arn)
  ami-mapping = {
    "amli2"    = data.aws_ami.amazon-linux-2.id
    "ubuntu22" = data.aws_ami.amazon-ubuntu-22.id
  }
}

data "aws_ami" "amazon-linux-2" {
  owners = ["amazon"]
  filter {
    name   = "name"
    values = ["amzn2-ami-kernel-5.10-hvm-2.0.20220606.1-x86_64-gp2"]
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

data "aws_ami" "amazon-ubuntu-22" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-arm64-server-*"]
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

data "aws_subnets" "vpc-public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  tags = {
    Name = "${var.vpc_name}-public-${var.region}*"
  }
}

data "aws_subnets" "vpc-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  tags = {
    Name = "${var.vpc_name}-private-${var.region}*"
  }
}

data "aws_iam_role" "AmazonSageMaker-ExecutionRole-modulai" {
  name = "AmazonSageMaker-ExecutionRole-modulai"
}

data "aws_eip" "eips-for-modulai-instance" {
  for_each  = toset(var.modulai_instance_eips)
  public_ip = each.key
}

data "aws_s3_bucket" "modulai-sagemaker-studio" {
  bucket = "modulai-sagemaker-studio"
}

data "aws_kms_key" "modulai" {
  key_id = "alias/modulai"
}

data "aws_caller_identity" "current" {}

data "aws_iam_openid_connect_provider" "eks-irsa" {
  for_each = local.eks-clusters
  url      = "https://${each.value}"
}

resource "aws_security_group" "modulai" {
  name        = "${var.vpc_name}-modulai"
  description = "SG for modulai instance(s) in VPC ${var.vpc_name}"
  vpc_id      = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-modulai"
  }
}

resource "aws_security_group_rule" "modulai-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.modulai.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "modulai-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.modulai.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "modulai-ssh-out" {
  type              = "egress"
  security_group_id = aws_security_group.modulai.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "ssh-to-modulai" {
  type              = "ingress"
  security_group_id = aws_security_group.modulai.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  description       = "Allow ssh"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group" "modulai-sagemaker" {
  name        = "${var.vpc_name}-modulai-sagemaker"
  description = "SG for modulai SageMaker in VPC ${var.vpc_name}"
  vpc_id      = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-modulai-sagemaker"
  }
}

resource "aws_security_group_rule" "modulai-sagemaker-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "modulai-sagemaker-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "modulai-sagemaker-out-sg" {
  type              = "egress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 0
  to_port           = 0
  protocol          = "tcp"

  source_security_group_id = aws_security_group.modulai-sagemaker.id
}

resource "aws_security_group_rule" "modulai-sagemaker-in-sg" {
  type              = "ingress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 0
  to_port           = 0
  protocol          = "tcp"

  source_security_group_id = aws_security_group.modulai-sagemaker.id
}

resource "aws_security_group_rule" "modulai-sagemaker-out-efs" {
  type              = "egress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 2049
  to_port           = 2049
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "modulai-sagemaker-in-efs" {
  type              = "ingress"
  security_group_id = aws_security_group.modulai-sagemaker.id
  from_port         = 2049
  to_port           = 2049
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "modulai" {
  name = "${var.vpc_name}-modulai"
  role = aws_iam_role.modulai.name
}

resource "aws_iam_role" "modulai" {
  name = "${var.vpc_name}-modulai"
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
        },
        {
            "Action": "sts:AssumeRole",
            "Principal": {
               "Service": "ecs-tasks.amazonaws.com"
            },
            "Effect": "Allow",
            "Sid": "EcsTasksAssume",
            "Condition": {
               "ArnLike": {
                 "aws:SourceArn": "arn:aws:ecs:eu-central-1:${data.aws_caller_identity.current.account_id}:*"
               },
               "StringEquals": {
                 "aws:SourceAccount": "${data.aws_caller_identity.current.account_id}"
               }
            }
        },
        {
            "Effect": "Allow",
            "Principal": {
                "Federated": "${data.aws_iam_openid_connect_provider.eks-irsa[var.vpc_name].arn}"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringEquals": {
                    "${local.eks-clusters[var.vpc_name]}:aud": "sts.amazonaws.com"
                },
                "StringLike": {
                    "${local.eks-clusters[var.vpc_name]}:sub": ["system:serviceaccount:modulai*:modulai*"]
                }
            }
        }
    ]
}
EOF

  managed_policy_arns = [data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn, "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"]
}

data "aws_s3_bucket" "s3-cross-env" {
  for_each = toset([for env in var.s3_bucket_cross_env_access : "${env}-modulai"])
  bucket   = each.key
}

resource "aws_iam_role_policy" "modulai-s3-acccess" {
  name = "${var.vpc_name}-modulai-s3-acccess"
  role = aws_iam_role.modulai.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = local.s3-arn-list
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = [for arn in local.s3-arn-list : "${arn}/*"]
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
        Resource = ["arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/modulai/*"]
      },
    ]
  })
}

locals {
  postgresql-db-instances = ["main", "video"]
  postgresql-credentials  = ["host", "username", "password"]
  postgresql-credentials-by-db-instances = distinct(flatten([
    for db in local.postgresql-db-instances : [
      for secret in local.postgresql-credentials : {
        db     = db
        secret = secret
      }
    ]
  ]))
}

resource "aws_ssm_parameter" "postgresql-rds-credentials" {
  for_each = { for entry in local.postgresql-credentials-by-db-instances : "${entry.db}.${entry.secret}" => entry }
  name     = "/modulai/${var.vpc_name}/postgresql/${each.value.db}/${each.value.secret}"
  type     = endswith(each.value.secret, "password") ? "SecureString" : "String"
  value    = "changeme"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_instance" "modulai" {
  count                   = length(var.modulai_instance_eips)
  ami                     = local.ami-mapping[var.ami_type]
  instance_type           = var.modulai_instance_type
  key_name                = "platform"
  subnet_id               = data.aws_subnets.vpc-public.ids[count.index]
  vpc_security_group_ids  = [aws_security_group.modulai.id]
  iam_instance_profile    = aws_iam_instance_profile.modulai.name
  disable_api_termination = true

  tags = {
    Name  = "${var.vpc_name}-modulai-${count.index + 1}"
    Owner = "platform"
  }
}

resource "aws_eip_association" "modulai-eips" {
  count         = length(var.modulai_instance_eips)
  instance_id   = aws_instance.modulai[count.index].id
  allocation_id = data.aws_eip.eips-for-modulai-instance[var.modulai_instance_eips[count.index]].id
}

resource "aws_s3_bucket" "modulai-s3-bucket" {
  bucket = "${var.vpc_name}-modulai"
}

resource "aws_s3_bucket_acl" "modulai-s3-acl" {
  bucket = aws_s3_bucket.modulai-s3-bucket.id
  acl    = "private"
}

resource "aws_s3_bucket_policy" "allow-access-from-another-account" {
  bucket = aws_s3_bucket.modulai-s3-bucket.id
  policy = data.aws_iam_policy_document.allow-access-from-another-account.json
}

data "aws_iam_policy_document" "allow-access-from-another-account" {
  statement {
    principals {
      type = "AWS"
      # ID of machine-learning AWS account
      identifiers = ["304552489232"]
    }

    actions = [
      "s3:ListBucket",
      "s3:GetBucket*",
      "s3:*Lifecycle*",
      "s3:Put*Object*",
      "s3:Get*Object*",
      "s3:Delete*Object*",
    ]

    resources = [
      aws_s3_bucket.modulai-s3-bucket.arn,
      "${aws_s3_bucket.modulai-s3-bucket.arn}/*",
    ]
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "modulai-s3-lifecycle" {
  bucket = aws_s3_bucket.modulai-s3-bucket.id

  rule {
    id = "modulai-s3-bucket-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    status = "Enabled"
  }

  dynamic "rule" {
    for_each = toset(["sagemaker/lightfm-output-prod/individual_recommendations", "sagemaker/lightfm-output-dev/individual_recommendations", "sagemaker/lightgbm_output/individual_recommedations"])
    content {
      id = replace("delete-content-after-7-days-in-${rule.key}", "/", "-")

      abort_incomplete_multipart_upload {
        days_after_initiation = 3
      }

      expiration {
        days = 7
      }

      filter {
        prefix = "${rule.key}/"
      }

      status = "Enabled"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.modulai-s3-bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_sagemaker_domain" "modulai-sagemaker-domain" {
  count                   = var.create_sagemaker_domain ? 1 : 0
  domain_name             = "${var.vpc_name}-modulai"
  auth_mode               = "IAM"
  vpc_id                  = data.aws_vpc.vpc.id
  subnet_ids              = data.aws_subnets.vpc-private.ids
  app_network_access_type = "VpcOnly"

  default_user_settings {
    execution_role  = data.aws_iam_role.AmazonSageMaker-ExecutionRole-modulai.arn
    security_groups = [aws_security_group.modulai-sagemaker.id]

    sharing_settings {
      notebook_output_option = "Allowed"
      s3_kms_key_id          = data.aws_kms_key.modulai.arn
      s3_output_path         = "s3://${data.aws_s3_bucket.modulai-sagemaker-studio.bucket}/sharing/${var.vpc_name}"
    }

    jupyter_server_app_settings {
      lifecycle_config_arns = []
      default_resource_spec {
        instance_type       = "system"
        sagemaker_image_arn = "arn:aws:sagemaker:eu-central-1:936697816551:image/jupyter-server-3"
      }
    }
  }
}
