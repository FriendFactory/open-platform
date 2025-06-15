terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.97"
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
  region = "eu-central-1"
  default_tags {
    tags = {
      Environment = "ixia-prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/ecs/ixia-prod/eu-central-1"
    }
  }
}

