module "stage-ecs-cluster" {
  source                 = "../../../tf-modules/ecs-cluster/"
  vpc_name               = "content-stage"
  lb_name                = "content-stage-ecs"
  ecs_cluster_name       = "stage"
  ecs_subnet_name_prefix = "content-stage-private"
  lb_subnet_name_prefix  = "content-stage-public"
}

