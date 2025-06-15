terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.94"
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
  profile = "frever-machine-learning"
  alias   = "machine-learning"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/vpc-peering"
    }
  }
}

provider "aws" {
  profile = "frever"
  region  = "eu-central-1"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/vpc-peering"
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
      RepoName    = "https://github.com/FriendFactory/platform/vpc-peering"
    }
  }
}

provider "aws" {
  profile = "frever-machine-learning"
  alias   = "frever-ml-us"
  region  = "us-east-2"
  default_tags {
    tags = {
      Environment = "prod"
      ManagedBy   = "terraform"
      RepoName    = "https://github.com/FriendFactory/platform/vpc-peering"
    }
  }
}
