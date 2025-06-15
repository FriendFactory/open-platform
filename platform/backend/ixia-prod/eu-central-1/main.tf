terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.99"
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
  profile = "frever"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "ixia-prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/backend/ixia-prod/eu-central-1"
    }
  }
}

provider "aws" {
  profile = "frever-machine-learning"
  alias   = "machine-learning"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "ixia-prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/backend/ixia-prod/eu-central-1"
    }
  }
}
