module "dev-backend" {
  source               = "../../../tf-modules/frever-backend/"
  vpc_name             = "dev"
  cidr_block           = "10.10.0.0/16"
  bastion_instance_ami = "ami-0f5506a411a8dab18"
  rds_kms_key_id       = data.aws_kms_alias.rds.target_key_arn
  # use_nat_instance     = true
  # nat_count            = 1
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

resource "aws_security_group" "dev-rds-client-sg-for-machine-learning" {
  provider    = aws.machine-learning
  vpc_id      = data.aws_vpc.frever-machine-learning-prod.id
  name        = "dev-rds-client-sg-for-machine-learning"
  description = "The RDS client SG to frever-dev VPC PostgreSQL RDS."
  tags = {
    Name = "dev-rds-client-sg-for-machine-learning"
  }
}

resource "aws_security_group_rule" "rds-postgresql-in-from-client" {
  security_group_id        = module.dev-backend.rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.dev-rds-client-sg-for-machine-learning.id
}

resource "aws_security_group_rule" "rds-postgresql-client-out" {
  provider                 = aws.machine-learning
  security_group_id        = aws_security_group.dev-rds-client-sg-for-machine-learning.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = module.dev-backend.rds-sg.id
}

# data "aws_instance" "dev-nat" {
#   filter {
#     name   = "tag:Name"
#     values = ["dev-nat"]
#   }
# }
#
# data "aws_availability_zones" "available" {
#   state = "available"
# }
#
# resource "aws_route" "private-nat-ec2" {
#   count                  = length(data.aws_availability_zones.available.names)
#   route_table_id         = module.dev-backend.private-route-tables[count.index].id
#   network_interface_id   = data.aws_instance.dev-nat.network_interface_id
#   destination_cidr_block = "0.0.0.0/0"
# }
#
