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

resource "aws_security_group" "observability" {
  name        = "${var.vpc_name}-observability"
  description = "SG for observability instance(s) in VPC ${var.vpc_name}"
  vpc_id      = data.aws_vpc.vpc.id
  tags = {
    "Name" = "${var.vpc_name}-observability"
  }
}

data "aws_security_group" "ssh-bastion" {
  name   = "${var.vpc_name}-ssh-bastion"
  vpc_id = data.aws_vpc.vpc.id
}

resource "aws_security_group_rule" "observability-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.observability.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "ssh-bastion-to-observability" {
  type              = "ingress"
  security_group_id = aws_security_group.observability.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  description       = "Allow bastion ssh"

  source_security_group_id = data.aws_security_group.ssh-bastion.id
}

resource "aws_security_group_rule" "loki-service-port-from-vpc" {
  type              = "ingress"
  security_group_id = aws_security_group.observability.id
  from_port         = 3100
  to_port           = 3100
  protocol          = "tcp"
  description       = "Loki port"

  cidr_blocks = [data.aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "grafana-service-port-from-vpc" {
  type              = "ingress"
  security_group_id = aws_security_group.observability.id
  from_port         = 3000
  to_port           = 3000
  protocol          = "tcp"
  description       = "Grafana port"

  cidr_blocks = [data.aws_vpc.vpc.cidr_block]
}

data "aws_security_group" "eks-nodes-sg" {
  name = var.eks_cluster_name == "" ? "eks-cluster-sg-${var.vpc_name}-*" : "eks-cluster-sg-${var.eks_cluster_name}-*"
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  name = "AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "observability" {
  name = "${var.vpc_name}-observability"
  role = aws_iam_role.observability.name
}

resource "aws_iam_role" "observability" {
  name = "${var.vpc_name}-observability"
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

resource "aws_iam_role_policy_attachment" "observability-attach" {
  role       = aws_iam_role.observability.name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_iam_role_policy" "loki-s3-acccess" {
  name = "s3-access"
  role = aws_iam_role.observability.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = "${aws_s3_bucket.loki-s3-bucket.arn}"
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
        ]
        Effect   = "Allow"
        Resource = "${aws_s3_bucket.loki-s3-bucket.arn}/*"
      },
    ]
  })
}

resource "aws_iam_role_policy" "grafana-cloudwatch-acccess" {
  name  = "cloudwatch-access"
  role  = aws_iam_role.observability.id
  count = var.enable_cloudwatch_metrics_in_grafana ? 1 : 0

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid = "AllowReadingMetricsFromCloudWatch",
        Action = [
          "cloudwatch:DescribeAlarmsForMetric",
          "cloudwatch:DescribeAlarmHistory",
          "cloudwatch:DescribeAlarms",
          "cloudwatch:ListMetrics",
          "cloudwatch:GetMetricData",
          "cloudwatch:GetInsightRuleReport"
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Sid = "AllowReadingLogsFromCloudWatch",
        Action = [
          "logs:DescribeLogGroups",
          "logs:GetLogGroupFields",
          "logs:StartQuery",
          "logs:StopQuery",
          "logs:GetQueryResults",
          "logs:GetLogEvents"
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Sid = "AllowReadingTagsInstancesRegionsFromEC2",
        Action = [
          "ec2:DescribeTags",
          "ec2:DescribeInstances",
          "ec2:DescribeRegions"
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Sid = "AllowReadingResourcesForTags",
        Action = [
          "tag:GetResources",
        ]
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "sts:AssumeRole",
        ]
        Effect   = "Allow"
        Resource = aws_iam_role.observability.arn
        Sid      = "AllowAssumeRole"
      },
    ]
  })
}

resource "aws_instance" "observability" {
  count                   = var.observability_instance_count
  ami                     = var.observability_instance_ami != null ? var.observability_instance_ami : var.use_amazon_linux_2023 ? data.aws_ami.al-2023-ami.id : data.aws_ami.amazon-linux-2.id
  instance_type           = var.observability_instance_type
  key_name                = "platform"
  subnet_id               = data.aws_subnets.vpc-private.ids[count.index]
  vpc_security_group_ids  = [aws_security_group.observability.id, data.aws_security_group.eks-nodes-sg.id]
  iam_instance_profile    = aws_iam_instance_profile.observability.name
  disable_api_termination = true

  tags = {
    Name  = "${var.vpc_name}-observability-${count.index + 1}"
    Owner = "platform"
  }
}

data "aws_route53_zone" "frever-internal" {
  name         = "frever-internal.com"
  private_zone = true
}

resource "aws_route53_record" "observability-instance" {
  count   = var.observability_instance_count == 1 ? 1 : 0
  zone_id = data.aws_route53_zone.frever-internal.zone_id
  name    = "${var.vpc_name}-observability.${data.aws_route53_zone.frever-internal.name}"
  type    = "A"
  ttl     = "300"
  records = [aws_instance.observability[0].private_ip]
}

resource "aws_s3_bucket" "loki-s3-bucket" {
  bucket = var.long-loki-s3-bucket-name ? "frever-${var.vpc_name}-loki" : "${var.vpc_name}-loki"
}

resource "aws_s3_bucket_ownership_controls" "loki-s3-bucket-ownership-controls" {
  bucket = aws_s3_bucket.loki-s3-bucket.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "loki-s3-acl" {
  depends_on = [aws_s3_bucket_ownership_controls.loki-s3-bucket-ownership-controls]
  bucket     = aws_s3_bucket.loki-s3-bucket.id
  acl        = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "loki-s3-lifecycle" {
  bucket = aws_s3_bucket.loki-s3-bucket.id

  rule {
    id = "loki-s3-bucket-lifecycle"

    filter {
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.loki-s3-bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "observability-sg" {
  description = "The security group of the observability instance(s)."
  value       = aws_security_group.observability
}


