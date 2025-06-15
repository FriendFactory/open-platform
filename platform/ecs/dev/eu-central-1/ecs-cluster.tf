module "dev-ecs-cluster" {
  source                 = "../../../tf-modules/ecs-cluster/"
  vpc_name               = "dev"
  lb_name                = "dev-ecs"
  ecs_cluster_name       = "dev"
  ecs_subnet_name_prefix = "dev-private"
  lb_subnet_name_prefix  = "dev-public"
}

data "aws_caller_identity" "current" {}

data "aws_vpc" "dev" {
  tags = {
    Name = "dev"
  }
}

data "aws_security_group" "dev-rds-client-sg" {
  name   = "dev-rds-client-sg"
  vpc_id = data.aws_vpc.dev.id
}

