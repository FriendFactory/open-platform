terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.93"
    }
  }

  backend "s3" {
    bucket         = "frever-tf"
    encrypt        = true
    kms_key_id     = "alias/tf-bucket-key"
    dynamodb_table = "tf-state"
  }
}

provider "aws" {
  default_tags {
    tags = {
      ManagedBy = "terraform"
      RepoName  = "https://github.com/FriendFactory/elasticache/eu-central-1"
    }
  }
}

