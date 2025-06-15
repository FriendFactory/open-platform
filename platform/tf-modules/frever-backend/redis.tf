resource "aws_elasticache_cluster" "valkey" {
  count                = var.redis_replicas > 0 ? 0 : 1
  cluster_id           = "${var.vpc_name}-redis"
  engine               = "valkey"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = var.redis_parameter_group_name
  engine_version       = "8.0"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis-sg.id]
  apply_immediately    = true

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis.name
    destination_type = "cloudwatch-logs"
    log_format       = "text"
    log_type         = "slow-log"
  }
}

resource "aws_elasticache_replication_group" "valkey-cluster" {
  count                = var.redis_replicas > 0 ? 1 : 0
  engine               = "valkey"
  replication_group_id = "${var.vpc_name}-valkey-cluster-on"
  description          = "${var.vpc_name}-valkey with cluster mode on"
  node_type            = var.redis_node_type
  port                 = 6379
  # parameter_group_name     = "default.redis7.cluster.on"
  parameter_group_name       = var.redis_parameter_group_name
  automatic_failover_enabled = true
  engine_version             = "8.0"
  multi_az_enabled           = true
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis-sg.id]
  num_node_groups            = 1
  replicas_per_node_group    = var.redis_replicas
  apply_immediately          = true

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }
}

resource "aws_cloudwatch_log_group" "redis" {
  name              = "${var.vpc_name}-redis-logs"
  retention_in_days = var.redis_log_retention_in_days
}

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.vpc_name}-redis-subnet-group"
  subnet_ids = aws_subnet.isolated[*].id
}

resource "aws_security_group" "redis-sg" {
  vpc_id      = aws_vpc.vpc.id
  name        = "${var.vpc_name}-redis-sg"
  description = "The SG for the Elasticache Redis."
  tags = {
    Name = "${var.vpc_name}-redis-sg"
  }
}

resource "aws_security_group" "redis-client-sg" {
  vpc_id      = aws_vpc.vpc.id
  name        = "${var.vpc_name}-redis-client-sg"
  description = "The client SG for the Elasticache Redis."
  tags = {
    Name = "${var.vpc_name}-redis-client-sg"
  }
}

resource "aws_security_group_rule" "redis-out" {
  security_group_id        = aws_security_group.redis-sg.id
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.redis-client-sg.id
}

resource "aws_security_group_rule" "redis-client-in" {
  security_group_id        = aws_security_group.redis-client-sg.id
  type                     = "egress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.redis-sg.id
}

resource "aws_security_group_rule" "redis-in-from-eks-node-group" {
  depends_on = [
    aws_eks_node_group.eks-node-group,
  ]
  security_group_id        = aws_security_group.redis-sg.id
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.eks-node-group-sg.id
}

