resource "aws_s3_bucket" "frever-content-stage" {
  bucket = "frever-content-stage"
}

data "aws_canonical_user_id" "current" {}

resource "aws_s3_bucket_acl" "frever-content-stage-acl" {
  bucket = aws_s3_bucket.frever-content-stage.id
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
  frever_content_stage_bucket_prefix = toset(["Assets", "Video", "Test"])
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-content-stage-lifecycle" {
  bucket = aws_s3_bucket.frever-content-stage.id

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
      noncurrent_days = 3
    }

    status = "Enabled"
  }

  dynamic "rule" {
    for_each = local.frever_content_stage_bucket_prefix
    content {
      id = "frever-content-stage-lifecycle-${rule.key}"

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

resource "aws_s3_bucket_public_access_block" "frever-content-stage-public-access" {
  bucket = aws_s3_bucket.frever-content-stage.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "frever-content-stage-bucket-policy" {
  bucket = aws_s3_bucket.frever-content-stage.id

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
              "s3:ListBucket",
              "s3:GetBucket*",
              "s3:Get*Object*",
              "s3:PutObject",
              "s3:PutObjectAcl"
          ],
          "Resource": [
              "arn:aws:s3:::frever-content-stage/*",
              "arn:aws:s3:::frever-content-stage"
          ]
      },
      {
          "Sid": "AllowCloudFrontRead",
          "Effect": "Allow",
          "Principal": {
              "AWS": "arn:aws:iam::cloudfront:user/CloudFront Origin Access Identity E2G40LWL5NWQ5O"
          },
          "Action": "s3:GetObject",
          "Resource": "arn:aws:s3:::frever-content-stage/*"
      }
  ]
}
POLICY
}

resource "aws_s3_bucket_cors_configuration" "frever-content-stage-cors" {
  bucket = aws_s3_bucket.frever-content-stage.id

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

resource "aws_s3_bucket_logging" "frever-content-stage-logs" {
  bucket = aws_s3_bucket.frever-content-stage.id

  target_bucket = aws_s3_bucket.frever-s3-logs.id
  target_prefix = "log/${aws_s3_bucket.frever-content-stage.id}/"
}

data "aws_region" "current" {}

data "aws_sqs_queue" "platform-operation-queue" {
  name = "platform-operation-input-queue-${data.aws_region.current.name}"
}

data "aws_lambda_function" "media-converter-content-stage" {
  function_name = "MediaConverter-Content-Stage"
}

resource "aws_s3_bucket_notification" "frever-content-stage-notification" {
  bucket = aws_s3_bucket.frever-content-stage.id

  lambda_function {
    id                  = "Convert on upload media"
    lambda_function_arn = data.aws_lambda_function.media-converter-content-stage.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "Preloaded/"
    filter_suffix       = ".convert"
  }

  dynamic "queue" {
    for_each = local.frever_content_stage_bucket_prefix

    content {
      id            = "objects-deletion-event-${queue.key}"
      queue_arn     = data.aws_sqs_queue.platform-operation-queue.arn
      events        = ["s3:ObjectRemoved:*", "s3:LifecycleExpiration:DeleteMarkerCreated"]
      filter_prefix = "${queue.key}/"
    }
  }
}

resource "aws_s3_bucket" "frever-s3-logs" {
  bucket = "frever-s3-logs"
}

resource "aws_s3_bucket_acl" "frever-s3-logs" {
  bucket = aws_s3_bucket.frever-s3-logs.id
  acl    = "log-delivery-write"
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-s3-logs-lifecycle" {
  bucket = aws_s3_bucket.frever-s3-logs.id

  rule {
    id = "delete-old-logs"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-s3-logs-public-access" {
  bucket = aws_s3_bucket.frever-s3-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "frever-cloudfront-logs" {
  bucket = "frever-cloudfront-logs"
}

resource "aws_s3_bucket_acl" "frever-cloudfront-logs-acl" {
  bucket = aws_s3_bucket.frever-cloudfront-logs.id
  access_control_policy {
    grant {
      grantee {
        id   = data.aws_canonical_user_id.current.id
        type = "CanonicalUser"
      }
      permission = "FULL_CONTROL"
    }

    grant {
      grantee {
        // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/AccessLogs.html
        id   = "c4c1ede66af53448b93c283ce9448c4ba468c9432aa01d700d3878632f77d2d0"
        type = "CanonicalUser"
      }
      permission = "FULL_CONTROL"
    }

    owner {
      id = data.aws_canonical_user_id.current.id
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-cloudfront-logs-lifecycle" {
  bucket = aws_s3_bucket.frever-cloudfront-logs.id

  rule {
    id = "delete-old-logs"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-cloudfront-logs-access" {
  bucket = aws_s3_bucket.frever-cloudfront-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_athena_database" "cloudfront-logs" {
  name   = "cloudfront_logs"
  bucket = aws_s3_bucket.frever-athena.bucket
}

resource "aws_s3_bucket" "frever-athena" {
  bucket = "frever-athena"
}

resource "aws_s3_bucket_acl" "frever-athena-acl" {
  bucket = aws_s3_bucket.frever-athena.id
  acl    = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-athena-lifecycle" {
  bucket = aws_s3_bucket.frever-athena.id

  rule {
    id = "delete-old-objects"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-athena-access" {
  bucket = aws_s3_bucket.frever-athena.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "frever-platform" {
  bucket = "frever-platform"
}

resource "aws_s3_bucket_acl" "frever-platform-acl" {
  bucket = aws_s3_bucket.frever-platform.id
  acl    = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-platform-lifecycle" {
  bucket = aws_s3_bucket.frever-platform.id

  rule {
    id = "delete-old-objects"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 30
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-platform-access" {
  bucket = aws_s3_bucket.frever-platform.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "frever-comfyui-output-dev" {
  bucket = "frever-comfyui-output-dev"
}

resource "aws_s3_bucket_lifecycle_configuration" "frever-comfyui-output-dev-lifecycle" {
  bucket = aws_s3_bucket.frever-comfyui-output-dev.id

  rule {
    id = "delete-old-objects"

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }

    expiration {
      days = 3
    }

    filter {}

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "frever-comfyui-output-dev-access" {
  bucket = aws_s3_bucket.frever-comfyui-output-dev.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_policy" "frever-comfyui-output-dev-policy" {
  bucket = aws_s3_bucket.frever-comfyui-output-dev.id

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
              "arn:aws:s3:::frever-comfyui-output-dev/*",
              "arn:aws:s3:::frever-comfyui-output-dev"
          ]
      }
  ]
}
POLICY
}

