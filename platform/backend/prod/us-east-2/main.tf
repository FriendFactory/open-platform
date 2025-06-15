terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.58"
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
  alias   = "frever-eu"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/backend/prod/us-east-2"
    }
  }
}

provider "aws" {
  profile = "frever"
  alias   = "frever-us"
  region  = "us-east-2"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/backend/prod/us-east-2"
    }
  }
}

