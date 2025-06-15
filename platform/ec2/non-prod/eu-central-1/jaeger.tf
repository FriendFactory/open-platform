module "test-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "content-test"
  use_amazon_linux_2023 = true
  jaeger_instance_ami   = "ami-0b1a1b08bb4526a83"
}

module "stage-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "content-stage"
  jaeger_instance_type  = "m6g.large"
  use_amazon_linux_2023 = true
  jaeger_instance_ami   = "ami-0b1a1b08bb4526a83"
}

module "dev-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "dev"
  use_amazon_linux_2023 = true
  jaeger_instance_ami   = "ami-0f5506a411a8dab18"
}
