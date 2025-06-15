resource "aws_s3_bucket" "prod-lb-access-logs" {
  bucket = "prod-lb-access-logs-${data.aws_region.current.name}"
}

resource "aws_s3_bucket_acl" "prod-lb-access-logs-s3-acl" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id
  acl    = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "prod-lb-access-logs-s3-lifecycle" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id

  rule {
    id = "prod-lb-access-logs-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 90
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 60
      storage_class = "GLACIER"
    }

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
resource "aws_s3_bucket_policy" "prod-lb-access-logs-bucket-policy" {
  bucket = aws_s3_bucket.prod-lb-access-logs.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnableLbToPutAccessLogsIntoBucket",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::054676820928:root"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
    },
    {
      "Sid": "AWSLogDeliveryWrite",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
      "Condition": {
        "StringEquals": {
          "s3:x-amz-acl": "bucket-owner-full-control"
        }
      }
    },
    {
      "Sid": "AWSLogDeliveryAclCheck",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:GetBucketAcl",
      "Resource": "${aws_s3_bucket.prod-lb-access-logs.arn}"
    }
  ]
}
POLICY
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

resource "aws_s3_bucket" "frever-content" {
  bucket = "frever-content"
}

data "aws_canonical_user_id" "current" {}

resource "aws_s3_bucket_acl" "frever-content-acl" {
  bucket = aws_s3_bucket.frever-content.id
  access_control_policy {
    grant {
      permission = "FULL_CONTROL"

      grantee {
        id   = data.aws_canonical_user_id.current.id
        type = "CanonicalUser"
      }
    }
    owner {
      id = data.aws_canonical_user_id.current.id
    }
  }
}

locals {
  frever_content_bucket_prefix        = toset(["Assets", "Video", "Test"])
  frever_content_noncurrent_kept_days = 21
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-content-lifecycle" {
  bucket = aws_s3_bucket.frever-content.id

  rule {
    id = "Delete old preloads"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    filter {
      prefix = "Preloaded"
    }

    expiration {
      days                         = 3
      expired_object_delete_marker = false
    }

    noncurrent_version_expiration {
      noncurrent_days = local.frever_content_noncurrent_kept_days
    }

    status = "Enabled"
  }

  dynamic "rule" {
    for_each = local.frever_content_bucket_prefix
    content {
      id = "frever-content-lifecycle-${rule.key}"

      abort_incomplete_multipart_upload {
        days_after_initiation = 3
      }

      noncurrent_version_expiration {
        noncurrent_days = 1
      }

      expiration {
        expired_object_delete_marker = true
      }

      filter {
        prefix = "${rule.key}/"
      }

      status = "Enabled"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "frever-content-public-access" {
  bucket = aws_s3_bucket.frever-content.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "frever-content-cors" {
  bucket = aws_s3_bucket.frever-content.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT"]
    allowed_origins = ["*"]
    expose_headers  = []
  }

  cors_rule {
    allowed_headers = []
    allowed_methods = ["GET"]
    allowed_origins = ["*"]
    expose_headers  = []
  }
}

resource "aws_s3_bucket_policy" "frever-content-bucket-policy" {
  bucket = aws_s3_bucket.frever-content.id
  policy = data.aws_iam_policy_document.frever-content-bucket-policy.json
}

data "aws_iam_policy_document" "frever-content-bucket-policy" {
  statement {
    principals {
      type = "AWS"
      # ID of machine-learning AWS account
      identifiers = ["arn:aws:iam::304552489232:root"]
    }

    actions = [
      "s3:ListBucket",
      "s3:GetBucket*",
      "s3:Get*Object*",
      "s3:PutObject",
      "s3:PutObjectAcl",
    ]

    resources = [
      aws_s3_bucket.frever-content.arn,
      "${aws_s3_bucket.frever-content.arn}/*",
    ]
  }

  statement {
    sid = "AllowCloudFrontRead"

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity E3QNUTDXJLDHG1"]
    }

    actions = [
      "s3:GetObject"
    ]

    resources = [
      "${aws_s3_bucket.frever-content.arn}/*",
    ]
  }
}


resource "aws_s3_bucket_logging" "frever-content-logs" {
  bucket = aws_s3_bucket.frever-content.id

  target_bucket = data.aws_s3_bucket.frever-s3-logs.id
  target_prefix = "log/${aws_s3_bucket.frever-content.id}/"
}

data "aws_sqs_queue" "platform-operation-queue" {
  name = "platform-operation-input-queue-${data.aws_region.current.name}"
}

data "aws_lambda_function" "media-converter-content" {
  function_name = "MediaConverter-Content"
}

resource "aws_s3_bucket_notification" "frever-content-notification" {
  bucket = aws_s3_bucket.frever-content.id

  lambda_function {
    id                  = "Convert on upload media"
    lambda_function_arn = data.aws_lambda_function.media-converter-content.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "Preloaded/"
    filter_suffix       = ".convert"
  }

  dynamic "queue" {
    for_each = local.frever_content_bucket_prefix

    content {
      id            = "objects-deletion-event-${queue.key}"
      queue_arn     = data.aws_sqs_queue.platform-operation-queue.arn
      events        = ["s3:ObjectRemoved:*", "s3:LifecycleExpiration:DeleteMarkerCreated"]
      filter_prefix = "${queue.key}/"
    }
  }
}

data "aws_s3_bucket" "frever-s3-logs" {
  bucket = "frever-s3-logs"
}

resource "aws_s3_bucket" "frever-comfyui-output-ixia-prod" {
  bucket = "frever-comfyui-output-ixia-prod"
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-comfyui-output-ixia-prod-lifecycle" {
  bucket = aws_s3_bucket.frever-comfyui-output-ixia-prod.id

  rule {
    id = "delete-old-objects"

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }

    expiration {
      days = 5
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-comfyui-output-ixia-prod-access" {
  bucket = aws_s3_bucket.frever-comfyui-output-ixia-prod.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "frever-comfyui-output-ixia-prod-policy" {
  bucket = aws_s3_bucket.frever-comfyui-output-ixia-prod.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
      {
          "Effect": "Allow",
          "Principal": {
              "AWS": "arn:aws:iam::304552489232:root"
          },
          "Action": [
              "s3:*"
          ],
          "Resource": [
              "arn:aws:s3:::frever-comfyui-output-ixia-prod/*",
              "arn:aws:s3:::frever-comfyui-output-ixia-prod"
          ]
      }
  ]
}
POLICY
}

