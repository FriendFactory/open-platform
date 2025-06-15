module "prod-us-east-2-observability" {
  source                      = "../../../tf-modules/observability/"
  vpc_name                    = "prod"
  observability_instance_type = "m6g.large"
  use_amazon_linux_2023       = true
  long-loki-s3-bucket-name    = true
}

module "prod-jaeger" {
  source                = "../../../tf-modules/jaeger/"
  vpc_name              = "prod"
  jaeger_instance_count = 3
  jaeger_instance_type  = "m6g.large"
  use_amazon_linux_2023 = true
}

