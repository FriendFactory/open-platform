# initial infra setup for terraform, only need to run once.
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.32"
    }
  }
  backend "s3" {
    bucket         = "frever-tf"
    key            = "backend/terraform.tfstate"
    region         = "eu-central-1"
    encrypt        = true
    kms_key_id     = "alias/tf-bucket-key"
    dynamodb_table = "tf-state"
  }
}

provider "aws" {
  region = "eu-central-1"
  default_tags {
    tags = {
      ManagedBy = "terraform"
      RepoName  = "https://github.com/FriendFactory/platform"
    }
  }
}

resource "aws_kms_key" "tf-bucket-key" {
  description         = "This key is used to encrypt bucket objects"
  enable_key_rotation = true
}

resource "aws_kms_alias" "key-alias" {
  name          = "alias/tf-bucket-key"
  target_key_id = aws_kms_key.tf-bucket-key.key_id
}

resource "aws_s3_bucket" "tf-state" {
  bucket = "frever-tf"
}

resource "aws_s3_bucket_acl" "tf-state-s3-acl" {
  bucket = aws_s3_bucket.tf-state.id
  acl    = "private"
}

resource "aws_s3_bucket_versioning" "tf-state-s3-versioning" {
  bucket = aws_s3_bucket.tf-state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tf-state-s3-sse" {
  bucket = aws_s3_bucket.tf-state.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.tf-bucket-key.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "tf-state-s3-lifecycle" {
  depends_on = [aws_s3_bucket_versioning.tf-state-s3-versioning]
  bucket     = aws_s3_bucket.tf-state.id

  rule {
    id = "tf-state-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    noncurrent_version_transition {
      noncurrent_days = 30
      storage_class   = "STANDARD_IA"
    }

    noncurrent_version_transition {
      noncurrent_days = 60
      storage_class   = "GLACIER"
    }

    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.tf-state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "tf-state" {
  name           = "tf-state"
  read_capacity  = 20
  write_capacity = 20
  hash_key       = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
