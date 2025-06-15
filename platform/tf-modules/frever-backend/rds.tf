data "aws_iam_role" "rds-monitoring-role" {
  name = "rds-monitoring-role"
}

resource "aws_db_subnet_group" "isolated" {
  name       = "${var.vpc_name}-isolated"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgresql-rds" {
  count                        = var.require_rds ? 1 : 0
  identifier                   = var.vpc_name
  allocated_storage            = var.rds_replicate_source_db == null ? var.rds_allocated_storage : null
  max_allocated_storage        = var.rds_replicate_source_db == null ? var.rds_max_allocated_storage : null
  db_name                      = var.rds_replicate_source_db == null ? "frever" : null
  engine                       = "postgres"
  engine_version               = var.rds_engine_version
  instance_class               = var.rds_instance_class
  multi_az                     = var.rds_multi_az
  username                     = var.rds_replicate_source_db == null ? "Master" : null
  replicate_source_db          = var.rds_replicate_source_db
  kms_key_id                   = var.rds_kms_key_id
  manage_master_user_password  = var.rds_replicate_source_db == null ? true : null
  parameter_group_name         = var.rds_replicate_source_db == null ? (var.rds_needs_logical_replication ? "frever-postgresql-17-logical-replication" : "frever-postgresql-17") : null
  skip_final_snapshot          = true
  db_subnet_group_name         = aws_db_subnet_group.isolated.name
  vpc_security_group_ids       = [aws_security_group.rds-sg.id]
  backup_retention_period      = var.rds_replicate_source_db == null ? "30" : "0"
  backup_window                = "03:00-03:30"
  maintenance_window           = "sun:04:00-sun:04:30"
  storage_type                 = "gp3"
  performance_insights_enabled = "true"
  monitoring_interval          = 60
  monitoring_role_arn          = data.aws_iam_role.rds-monitoring-role.arn
  storage_encrypted            = "true"
}

resource "aws_security_group" "rds-sg" {
  vpc_id      = aws_vpc.vpc.id
  name        = "${var.vpc_name}-rds-sg"
  description = "The SG for the PostgreSQL RDS."
  tags = {
    Name = "${var.vpc_name}-rds-sg"
  }
}

resource "aws_security_group" "rds-client-sg" {
  vpc_id      = aws_vpc.vpc.id
  name        = "${var.vpc_name}-rds-client-sg"
  description = "The RDS client SG for the PostgreSQL RDS."
  tags = {
    Name = "${var.vpc_name}-rds-client-sg"
  }
}

resource "aws_security_group_rule" "rds-postgresql-in-from-client" {
  security_group_id        = aws_security_group.rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds-client-sg.id
}

resource "aws_security_group_rule" "rds-postgresql-client-out" {
  security_group_id        = aws_security_group.rds-client-sg.id
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.rds-sg.id
}

resource "aws_security_group_rule" "rds-postgresql-in-from-eks-node-group" {
  depends_on = [
    aws_eks_node_group.eks-node-group,
  ]
  security_group_id        = aws_security_group.rds-sg.id
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.eks-node-group-sg.id
}

output "rds-sg" {
  value = aws_security_group.rds-sg
}

output "db-subnet-group" {
  value = aws_db_subnet_group.isolated
}
