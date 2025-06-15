resource "aws_elasticache_parameter_group" "redis" {
  name   = "frever-redis-7"
  family = "redis7"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }
}

resource "aws_elasticache_parameter_group" "redis-debug" {
  name   = "frever-redis-7-debug"
  family = "redis7"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }
  parameter {
    name  = "slowlog-log-slower-than"
    value = "45"
  }
  parameter {
    name  = "slowlog-max-len"
    value = "5120"
  }
}

resource "aws_elasticache_parameter_group" "valkey7" {
  name   = "frever-valkey-7"
  family = "valkey7"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }
}

resource "aws_elasticache_parameter_group" "valkey7-cluster-on" {
  name   = "frever-valkey-7-cluster-on"
  family = "valkey7"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }

  parameter {
    name  = "cluster-enabled"
    value = "yes"
  }
}

resource "aws_elasticache_parameter_group" "valkey8" {
  name   = "frever-valkey-8"
  family = "valkey8"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }
}

resource "aws_elasticache_parameter_group" "valkey8-cluster-on" {
  name   = "frever-valkey-8-cluster-on"
  family = "valkey8"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }

  parameter {
    name  = "cluster-enabled"
    value = "yes"
  }
}

