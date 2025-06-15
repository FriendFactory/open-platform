module "dev-1-modulai" {
  source                     = "../../../tf-modules/modulai/"
  vpc_name                   = "dev-1"
  # modulai_instance_eips      = ["3.66.38.55"]
  create_sagemaker_domain    = true
  s3_bucket_cross_env_access = ["content-prod"]
  ami_type                   = "ubuntu22"
  modulai_instance_type      = "c6g.8xlarge"

}

module "content-stage-modulai" {
  source                     = "../../../tf-modules/modulai/"
  vpc_name                   = "content-stage"
  # modulai_instance_eips      = ["3.124.70.227"]
  s3_bucket_cross_env_access = ["dev-1", "content-prod"]
}

