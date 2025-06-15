module "prod-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "content-prod"
  jaeger_instance_count = 3
  jaeger_instance_type  = "m6g.large"
  use_amazon_linux_2023 = true
  jaeger_instance_ami   = "ami-0b1a1b08bb4526a83"
}

module "ixia-prod-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "ixia-prod"
  use_amazon_linux_2023 = true
  # jaeger_instance_count = 3
  jaeger_instance_type  = "m8g.large"
  # jaeger_instance_ami   = "ami-0f5506a411a8dab18"
}

