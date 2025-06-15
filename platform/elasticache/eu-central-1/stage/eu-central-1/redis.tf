resource "aws_elasticache_replication_group" "stage-redis" {
  replication_group_id       = "content-stage-cache-cluster-on"
  description                = "content-stage-cache with cluster mode on"
  node_type                  = "cache.t4g.small"
  port                       = 6379
  parameter_group_name       = "default.redis7.cluster.on"
  automatic_failover_enabled = true
  engine_version             = "7.0"
  multi_az_enabled           = true
  subnet_group_name          = aws_elasticache_subnet_group.content-stage.name
  security_group_ids         = [aws_security_group.content-stage-redis-sg.id]
  num_node_groups            = 1
  replicas_per_node_group    = 1
  apply_immediately          = true
}

data "aws_vpc" "content-stage" {
  tags = {
    Name = "content-stage"
  }
}

data "aws_subnets" "content-stage-elasticache" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-stage.id]
  }
  filter {
    name   = "tag:Name"
    values = ["content-stage-elasticache-eu-central-1*"]
  }
}

resource "aws_elasticache_subnet_group" "content-stage" {
  name       = "content-stage"
  subnet_ids = data.aws_subnets.content-stage-elasticache.ids
}

resource "aws_security_group" "content-stage-redis-sg" {
  vpc_id      = data.aws_vpc.content-stage.id
  name        = "content-stage-redis-sg"
  description = "The SG for the Elasticache Redis in content-stage vpc."
  tags = {
    Name = "content-stage-redis-sg"
  }
}

resource "aws_security_group_rule" "redis-in" {
  security_group_id = aws_security_group.content-stage-redis-sg.id
  type              = "ingress"
  from_port         = 6379
  to_port           = 6379
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.content-stage.cidr_block]
}

