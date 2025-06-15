data "aws_vpc" "vpc" {
  tags = {
    Name = var.vpc_name
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.vpc.id]
  }
  filter {
    name   = "tag:Name"
    values = ["*-private-*"]
  }
}

data "aws_route_tables" "rts" {
  vpc_id = var.vpc_name
}

data "aws_region" "current" {}

# -- S3 endpoint (Gateway) -----------------------------------------------------
resource "aws_vpc_endpoint" "s3_internet_disabled" {
  service_name    = "com.amazonaws.${data.aws_region.current.name}.s3"
  route_table_ids = data.aws_route_tables.rts.ids
  vpc_id          = data.aws_vpc.vpc.id

  tags = { Name = "${var.vpc_name}-${data.aws_region.current.name}-s3" }
}

# -- ECR Endpoints -------------------------------------------------------------
resource "aws_vpc_endpoint" "ecr_dkr" {
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.ecr_service.id]
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.dkr"
  subnet_ids          = data.aws_subnets.private.ids
  vpc_endpoint_type   = "Interface"
  vpc_id              = data.aws_vpc.vpc.id

  tags = { Name = "${var.vpc_name}-${data.aws_region.current.name}-ecr-dkr" }
}

resource "aws_vpc_endpoint" "ecr_api" {
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.ecr_service.id]
  service_name        = "com.amazonaws.${data.aws_region.current.name}.ecr.api"
  subnet_ids          = data.aws_subnets.private.ids
  vpc_endpoint_type   = "Interface"
  vpc_id              = data.aws_vpc.vpc.id

  tags = { Name = "${var.vpc_name}-${data.aws_region.current.name}-ecr-api" }
}

resource "aws_security_group" "ecr_service" {
  description = "Allow access to ECR endpoints"
  name        = "${var.vpc_name}-ecr-service"
  vpc_id      = data.aws_vpc.vpc.id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.vpc.cidr_block]
  }

  tags = { Name = "${var.vpc_name}-ecr-service" }
}

