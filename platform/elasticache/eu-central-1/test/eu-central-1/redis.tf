resource "aws_elasticache_replication_group" "test-redis" {
  replication_group_id       = "content-test-cache-cluster-on"
  description                = "content-test-cache with cluster mode on"
  node_type                  = "cache.t4g.micro"
  port                       = 6379
  parameter_group_name       = "default.redis7.cluster.on"
  automatic_failover_enabled = true
  engine_version             = "7.0"
  multi_az_enabled           = false
  subnet_group_name          = aws_elasticache_subnet_group.content-test.name
  security_group_ids         = [aws_security_group.content-test-redis-sg.id]
  num_node_groups            = 1
  replicas_per_node_group    = 0
}

data "aws_vpc" "content-test" {
  tags = {
    Name = "content-test"
  }
}

data "aws_subnets" "content-test-elasticache" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.content-test.id]
  }
  filter {
    name   = "tag:Name"
    values = ["content-test-elasticache-eu-central-1*"]
  }
}

resource "aws_elasticache_subnet_group" "content-test" {
  name       = "content-test"
  subnet_ids = data.aws_subnets.content-test-elasticache.ids
}

resource "aws_security_group" "content-test-redis-sg" {
  vpc_id      = data.aws_vpc.content-test.id
  name        = "content-test-redis-sg"
  description = "The SG for the Elasticache Redis in content-test vpc."
  tags = {
    Name = "content-test-redis-sg"
  }
}

resource "aws_security_group_rule" "redis-in" {
  security_group_id = aws_security_group.content-test-redis-sg.id
  type              = "ingress"
  from_port         = 6379
  to_port           = 6379
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.content-test.cidr_block]
}

