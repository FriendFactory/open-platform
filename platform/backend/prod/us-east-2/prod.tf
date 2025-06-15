data "aws_db_instance" "prod-main" {
  provider               = aws.frever-eu
  db_instance_identifier = "production-main"
}

data "aws_vpc" "frever-prod" {
  provider = aws.frever-eu
  tags = {
    Name = "content-prod"
  }
}

data "aws_kms_alias" "rds" {
  provider = aws.frever-us
  name     = "alias/aws/rds"
}

module "prod-backend" {
  providers = {
    aws = aws.frever-us
  }
  source                        = "../../../tf-modules/frever-backend/"
  vpc_name                      = "prod"
  cidr_block                    = "10.100.0.0/16"
  nat_count                     = 3
  bastion_instance_ami          = "ami-07a5db12eede6ff87"
  redis_replicas                = 1
  redis_node_type               = "cache.m6g.large"
  rds_instance_class            = "db.m7g.large"
  rds_multi_az                  = true
  rds_engine_version            = "14.10"
  rds_replicate_source_db       = data.aws_db_instance.prod-main.db_instance_arn
  rds_kms_key_id                = data.aws_kms_alias.rds.target_key_arn
  eks_node_group_instance_types = ["m5.xlarge"]
}

data "aws_security_group" "eks-node-group-sg" {
  provider   = aws.frever-us
  depends_on = [module.prod-backend]
  name       = "eks-cluster-sg-prod-eks-cluster-*"
}

data "aws_security_group" "prod-main-db-in-eu-central-1-sg" {
  name = "content-prod-db"
}

data "aws_caller_identity" "current" {}

# https://docs.aws.amazon.com/vpc/latest/peering/vpc-peering-security-groups.html
# You cannot reference the security group of a peer VPC that's in a different Region. Instead, use the CIDR block of the peer VPC.
resource "aws_security_group_rule" "content-prod-allow-prod-us-east-2-eks-nodes" {
  type              = "ingress"
  security_group_id = data.aws_security_group.prod-main-db-in-eu-central-1-sg.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [module.prod-backend.vpc.cidr_block]
}

resource "aws_security_group_rule" "prod-us-east-2-eks-nodes-to-content-prod-main-db" {
  provider          = aws.frever-us
  type              = "egress"
  security_group_id = data.aws_security_group.eks-node-group-sg.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.frever-prod.cidr_block]
}

resource "aws_acm_certificate" "frever-api-star" {
  provider                  = aws.frever-us
  domain_name               = "*.frever-api.com"
  validation_method         = "DNS"
  key_algorithm             = "EC_prime256v1"
  subject_alternative_names = ["frever-api.com"]
}

data "aws_route53_zone" "frever-api" {
  name         = "frever-api.com"
  private_zone = false
}

resource "aws_route53_record" "frever-api-star-validation" {
  provider = aws.frever-us
  for_each = {
    for dvo in aws_acm_certificate.frever-api-star.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.frever-api.zone_id
}

