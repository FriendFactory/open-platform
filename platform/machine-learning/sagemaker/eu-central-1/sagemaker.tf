data "aws_vpc" "prod" {
  tags = {
    Name = "prod"
  }
}

data "aws_vpc" "content-prod" {
  provider = aws.frever
  tags = {
    Name = "content-prod"
  }
}

data "aws_subnets" "prod-private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.prod.id]
  }
  filter {
    name   = "tag:Name"
    values = ["prod-private-*"]
  }
}

data "aws_security_group" "msk-sg" {
  vpc_id = data.aws_vpc.prod.id
  filter {
    name   = "tag:Name"
    values = ["msk-sg"]
  }
}

data "aws_security_group" "neptune-client-sg" {
  vpc_id = data.aws_vpc.prod.id
  tags = {
    "Name" = "machine-learning-neptune-client-sg"
  }
}

data "aws_security_group" "content-prod-db-replica" {
  provider = aws.frever
  vpc_id   = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "content-prod-db-replica"
  }
}

data "aws_iam_role" "sage-maker-template-recsys-execution-role" {
  name = "sage-maker-template-recsys-execution-role"
}

data "aws_iam_role" "sage-maker-who-to-follow-execution-role" {
  name = "sage-maker-who-to-follow-execution-role"
}

locals {
  frever_ml_endpoints_prefix = toset(["feed-recsys", "follow-recsys", "template-recsys"])
}

resource "aws_kms_key" "machine-learning-sagemaker-kms" {
}

resource "aws_kms_alias" "machine-learning-sagemaker-kms-alias" {
  name          = "alias/machine-learning-sagemaker"
  target_key_id = aws_kms_key.machine-learning-sagemaker-kms.key_id
}

resource "aws_s3_bucket" "machine-learning-sagemaker" {
  bucket = "frever-machine-learning-sagemaker"
}

resource "aws_s3_bucket_ownership_controls" "machine-learning-sagemaker-ownership-controls" {
  bucket = aws_s3_bucket.machine-learning-sagemaker.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "machine-learning-sagemaker-s3-acl" {
  depends_on = [aws_s3_bucket_ownership_controls.machine-learning-sagemaker-ownership-controls]
  bucket     = aws_s3_bucket.machine-learning-sagemaker.id
  acl        = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "machine-learning-sagemaker-s3-lifecycle" {
  bucket = aws_s3_bucket.machine-learning-sagemaker.id

  rule {
    id = "machine-learning-sagemaker-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    status = "Enabled"
  }

  dynamic "rule" {
    for_each = local.frever_ml_endpoints_prefix
    content {
      id = "frever-ml-endpoints-logs-lifecycle-${rule.key}"

      expiration {
        days = 45
      }

      filter {
        prefix = "${rule.key}/"
      }

      status = "Enabled"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "machine-learning-sagemaker-s3-block" {
  bucket = aws_s3_bucket.machine-learning-sagemaker.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket_policy" "machine-learning-sagemaker-s3-bucket-policy" {
  bucket = aws_s3_bucket.machine-learning-sagemaker.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnableBucketAccessFromFreverAwsAccount",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::722913253728:root"
      },
      "Action": "s3:*",
      "Resource": "${aws_s3_bucket.machine-learning-sagemaker.arn}"
    },
    {
      "Sid": "EnableBucketContentAccessFromFreverAwsAccount",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::722913253728:root"
      },
      "Action": "s3:*",
      "Resource": "${aws_s3_bucket.machine-learning-sagemaker.arn}/*"
    },
    {
      "Sid": "ListForLambdasFromFreverAccount",
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "s3:List*",
      "Resource": "${aws_s3_bucket.machine-learning-sagemaker.arn}",
      "Condition": {
          "StringLike": {
              "aws:userid": "${data.aws_caller_identity.current.account_id}:awslambda_*"
          }
      }
    },
    {
      "Sid": "ReadForLambdasFromFreverAccount",
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "s3:Get*",
      "Resource": "${aws_s3_bucket.machine-learning-sagemaker.arn}/*",
      "Condition": {
          "StringLike": {
              "aws:userid": "${data.aws_caller_identity.current.account_id}:awslambda_*"
          }
      }
    }
  ]
}
POLICY
}

resource "aws_iam_policy" "machine-learning-sagemaker-s3-read-write-policy" {
  name        = "machine-learning-sagemaker-s3-read-write"
  path        = "/"
  description = "Allow read and write machine-learning-sagemaker s3 bucket"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:List*",
        ]
        Sid      = "ListMachineLearningSageMakerBucket"
        Effect   = "Allow"
        Resource = aws_s3_bucket.machine-learning-sagemaker.arn
      },
      {
        Action = [
          "s3:Get*",
          "s3:Put*",
          "s3:Delete*",
        ]
        Sid      = "ReadWriteMachineLearningSageMakerBucket"
        Effect   = "Allow"
        Resource = "${aws_s3_bucket.machine-learning-sagemaker.arn}/*"
      },
    ]
  })
}

resource "aws_security_group" "machine-learning-sagemaker-pipeline-sg" {
  name        = "machine-learning-sagemaker-pipeline-sg"
  description = "SG for machine-learning SageMaker pipeline"
  vpc_id      = data.aws_vpc.prod.id
  tags = {
    "Name" = "machine-learning-sagemaker-pipeline"
  }
}

resource "aws_security_group_rule" "machine-learning-sagemaker-pipeline-sg-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.machine-learning-sagemaker-pipeline-sg.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "machine-learning-sagemaker-pipeline-sg-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.machine-learning-sagemaker-pipeline-sg.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "machine-learning-sagemaker-pipeline-sg-out-to-content-prod-postgresql-read-replica" {
  type              = "egress"
  security_group_id = aws_security_group.machine-learning-sagemaker-pipeline-sg.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.content-prod-db-replica.id
}

resource "aws_security_group_rule" "machine-learning-sagemaker-pipeline-sg-out-to-msk" {
  type                     = "egress"
  security_group_id        = aws_security_group.machine-learning-sagemaker-pipeline-sg.id
  from_port                = 9092
  to_port                  = 9098
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.msk-sg.id
}

resource "aws_sagemaker_domain" "template-recommendation-sagemaker-domain" {
  domain_name             = "template-recommendation-sagemaker-domain"
  auth_mode               = "IAM"
  vpc_id                  = data.aws_vpc.prod.id
  subnet_ids              = data.aws_subnets.prod-private.ids
  app_network_access_type = "VpcOnly"

  default_user_settings {
    execution_role  = data.aws_iam_role.sage-maker-template-recsys-execution-role.arn
    security_groups = [aws_security_group.machine-learning-sagemaker-pipeline-sg.id, data.aws_security_group.neptune-client-sg.id]

    sharing_settings {
      notebook_output_option = "Allowed"
      s3_kms_key_id          = aws_kms_key.machine-learning-sagemaker-kms.arn
      s3_output_path         = "s3://${aws_s3_bucket.machine-learning-sagemaker.bucket}/sharing/template-recommendation"
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

resource "aws_sagemaker_user_profile" "template-recommendation" {
  domain_id         = aws_sagemaker_domain.template-recommendation-sagemaker-domain.id
  user_profile_name = "template-recommendation"
}

resource "aws_sagemaker_domain" "follow-recommendation-sagemaker-domain" {
  domain_name             = "follow-recommendation-sagemaker-domain"
  auth_mode               = "IAM"
  vpc_id                  = data.aws_vpc.prod.id
  subnet_ids              = data.aws_subnets.prod-private.ids
  app_network_access_type = "VpcOnly"

  default_user_settings {
    execution_role  = data.aws_iam_role.sage-maker-who-to-follow-execution-role.arn
    security_groups = [aws_security_group.machine-learning-sagemaker-pipeline-sg.id, data.aws_security_group.neptune-client-sg.id]

    sharing_settings {
      notebook_output_option = "Allowed"
      s3_kms_key_id          = aws_kms_key.machine-learning-sagemaker-kms.arn
      s3_output_path         = "s3://${aws_s3_bucket.machine-learning-sagemaker.bucket}/sharing/follow-recommendation"
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

resource "aws_sagemaker_user_profile" "follow-recommendation" {
  domain_id         = aws_sagemaker_domain.follow-recommendation-sagemaker-domain.id
  user_profile_name = "follow-recommendation"
}

