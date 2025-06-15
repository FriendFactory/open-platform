module "content-prod-observability" {
  source                               = "../../../tf-modules/observability/"
  vpc_name                             = "content-prod"
  observability_instance_type          = "m6g.large"
  enable_cloudwatch_metrics_in_grafana = true
  use_amazon_linux_2023                = true
  observability_instance_ami           = "ami-0b1a1b08bb4526a83"
}

data "aws_vpc" "content-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_security_group" "content-prod-bastion-limited-access" {
  vpc_id = data.aws_vpc.content-prod.id
  name   = "content-prod-bastion-limited-access"
}

resource "aws_security_group_rule" "content-prod-observability-allow-limited-access-bastion" {
  type                     = "ingress"
  security_group_id        = module.content-prod-observability.observability-sg.id
  from_port                = 3000
  to_port                  = 3000
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.content-prod-bastion-limited-access.id
}

module "ixia-prod-observability" {
  source                               = "../../../tf-modules/observability/"
  vpc_name                             = "ixia-prod"
  eks_cluster_name                     = "ixia-prod-eks-cluster"
  observability_instance_type          = "m8g.large"
  enable_cloudwatch_metrics_in_grafana = true
  use_amazon_linux_2023                = true
  # observability_instance_ami           = "ami-0b1a1b08bb4526a83"
}

