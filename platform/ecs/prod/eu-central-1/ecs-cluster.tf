module "prod-ecs-cluster" {
  source                 = "../../../tf-modules/ecs-cluster/"
  vpc_name               = "content-prod"
  lb_name                = "content-prod-ecs"
  lb_idle_timeout        = 600
  ecs_cluster_name       = "prod"
  ecs_subnet_name_prefix = "content-prod-private"
  lb_subnet_name_prefix  = "content-prod-public"
}

