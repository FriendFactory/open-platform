module "test-ecs-cluster" {
  source                 = "../../../tf-modules/ecs-cluster/"
  vpc_name               = "content-test"
  lb_name                = "content-test-ecs"
  ecs_cluster_name       = "test"
  ecs_subnet_name_prefix = "content-test-private"
  lb_subnet_name_prefix  = "content-test-public"
}

