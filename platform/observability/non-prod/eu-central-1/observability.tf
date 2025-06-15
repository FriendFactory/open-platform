module "content-stage-observability" {
  source                     = "../../../tf-modules/observability/"
  vpc_name                   = "content-stage"
  observability_instance_ami = "ami-0b1a1b08bb4526a83"
  use_amazon_linux_2023      = true
}

module "dev-observability" {
  source                      = "../../../tf-modules/observability/"
  vpc_name                    = "dev"
  eks_cluster_name            = "dev-eks-cluster"
  use_amazon_linux_2023       = true
  long-loki-s3-bucket-name    = true
  observability_instance_ami  = "ami-0f5506a411a8dab18"
  observability_instance_type = "t4g.small"
}

