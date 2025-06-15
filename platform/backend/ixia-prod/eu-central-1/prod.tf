module "ixia-prod-backend" {
  source                        = "../../../tf-modules/frever-backend/"
  vpc_name                      = "ixia-prod"
  nat_count                     = 3
  cidr_block                    = "10.150.0.0/16"
  redis_replicas                = 1
  redis_node_type               = "cache.t4g.medium"
  redis_parameter_group_name    = "frever-valkey-8-cluster-on"
  rds_instance_class            = "db.m8g.large"
  rds_multi_az                  = true
  rds_engine_version            = "17.4"
  rds_needs_logical_replication = true
  rds_kms_key_id                = data.aws_kms_alias.rds.target_key_arn
  eks_node_group_instance_types = ["m5.xlarge"]
  eks_node_group_desired_size   = 6
  bastion_instance_ami          = "ami-0e597b908340825b5"
}

data "aws_vpc" "frever-machine-learning-prod" {
  provider = aws.machine-learning
  tags = {
    Name = "prod"
  }
}

data "aws_kms_alias" "rds" {
  name = "alias/aws/rds"
}

resource "aws_security_group" "ixia-prod-rds-client-sg-for-machine-learning" {
  provider    = aws.machine-learning
  vpc_id      = data.aws_vpc.frever-machine-learning-prod.id
  name        = "ixia-prod-rds-client-sg-for-machine-learning"
  description = "The RDS client SG to frever ixia-prod VPC PostgreSQL RDS."
  tags = {
    Name = "ixia-prod-rds-client-sg-for-machine-learning"
  }
}

# need vpc-peering
resource "aws_security_group_rule" "rds-postgresql-in-from-client" {
  security_group_id        = module.ixia-prod-backend.rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ixia-prod-rds-client-sg-for-machine-learning.id
}

# need vpc-peering
resource "aws_security_group_rule" "rds-postgresql-client-out" {
  provider                 = aws.machine-learning
  security_group_id        = aws_security_group.ixia-prod-rds-client-sg-for-machine-learning.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = module.ixia-prod-backend.rds-sg.id
}

