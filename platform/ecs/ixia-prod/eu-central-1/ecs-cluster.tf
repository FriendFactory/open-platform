module "ixia-prod-ecs-cluster" {
  source                 = "../../../tf-modules/ecs-cluster/"
  vpc_name               = "ixia-prod"
  lb_name                = "ixia-prod-ecs"
  lb_idle_timeout        = 600
  ecs_cluster_name       = "ixia-prod"
  ecs_subnet_name_prefix = "ixia-prod-private"
  lb_subnet_name_prefix  = "ixia-prod-public"
}

