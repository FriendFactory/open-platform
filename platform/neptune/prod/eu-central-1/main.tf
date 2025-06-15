terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.67"
    }
  }

  backend "s3" {
    profile        = "frever"
    bucket         = "frever-tf"
    encrypt        = true
    kms_key_id     = "alias/tf-bucket-key"
    dynamodb_table = "tf-state"
  }
}

provider "aws" {
  profile = "frever-machine-learning"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/neptune"
    }
  }
}

