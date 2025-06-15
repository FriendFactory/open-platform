module "content-prod-modulai" {
  source                  = "../../../tf-modules/modulai/"
  vpc_name                = "content-prod"
  # modulai_instance_eips   = ["18.159.177.173"]
  s3_bucket_cross_env_access = ["dev-1"]
}

