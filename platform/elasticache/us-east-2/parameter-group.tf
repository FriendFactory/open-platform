resource "aws_elasticache_parameter_group" "redis" {
  provider = aws.frever-us
  name     = "frever-redis-7"
  family   = "redis7"

  parameter {
    name  = "activedefrag"
    value = "yes"
  }
}

resource "aws_elasticache_parameter_group" "redis-debug" {
  provider = aws.frever-us
  name     = "frever-redis-7-debug"
  family   = "redis7"

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

