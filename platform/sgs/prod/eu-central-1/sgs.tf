data "aws_vpc" "content-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_vpc" "frever-machine-learning-prod" {
  provider = aws.machine-learning
  tags = {
    Name = "prod"
  }
}

resource "aws_security_group" "content-prod-bastion" {
  name        = "content-prod-ssh-bastion"
  description = "Content-prod ssh bastion"
  vpc_id      = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "content-prod-ssh-bastion"
  }
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-allow-ssh" {
  type              = "ingress"
  security_group_id = aws_security_group.content-prod-bastion.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-prod-bastion.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-allow-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-prod-bastion.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-prod-ssh-bastion-allow-any-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.content-prod-bastion.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.content-prod.cidr_block, data.aws_vpc.frever-machine-learning-prod.cidr_block]
}

data "aws_security_group" "machine-learning-sagemaker-pipeline-sg" {
  provider = aws.machine-learning
  vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  name     = "machine-learning-sagemaker-pipeline-sg"
}

resource "aws_security_group" "content-prod-db-replica" {
  name        = "content-prod-db-replica"
  description = "Content-prod DB replica sg"
  vpc_id      = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "content-prod-db-replica"
  }
}

resource "aws_security_group_rule" "content-prod-read-replica-allow-bastion-limited-access" {
  type              = "ingress"
  security_group_id = aws_security_group.content-prod-db-replica.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = aws_security_group.bastion-limited-access.id
}

resource "aws_security_group_rule" "content-prod-read-replica-allow-machine-learning-sagemaker-pipleline-access" {
  type              = "ingress"
  security_group_id = aws_security_group.content-prod-db-replica.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = data.aws_security_group.machine-learning-sagemaker-pipeline-sg.id
}

resource "aws_security_group_rule" "content-prod-read-replica-allow-client-access" {
  type              = "ingress"
  security_group_id = aws_security_group.content-prod-db-replica.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = aws_security_group.content-prod-db-replica-client.id
}

resource "aws_security_group" "content-prod-db-replica-client" {
  provider    = aws.machine-learning
  name        = "content-prod-db-replica-client"
  description = "Content-prod DB replica client sg"
  vpc_id      = data.aws_vpc.frever-machine-learning-prod.id
  tags = {
    "Name" = "content-prod-db-replica-client"
  }
}

resource "aws_security_group_rule" "content-prod-read-replica-client-allow-access" {
  provider          = aws.machine-learning
  type              = "egress"
  security_group_id = aws_security_group.content-prod-db-replica-client.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = aws_security_group.content-prod-db-replica.id
}

resource "aws_security_group" "bastion-limited-access" {
  name        = "content-prod-bastion-limited-access"
  description = "SG for limited access bastion instance in VPC content-prod"
  vpc_id      = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "content-prod-bastion-limited-access"
  }
}

resource "aws_security_group_rule" "bastion-limited-access-allow-ssh-in" {
  type              = "ingress"
  security_group_id = aws_security_group.bastion-limited-access.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "bastion-limited-access-to-content-prod-read-replica" {
  type              = "egress"
  security_group_id = aws_security_group.bastion-limited-access.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  source_security_group_id = aws_security_group.content-prod-db-replica.id
}

resource "aws_security_group_rule" "bastion-limited-access-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.bastion-limited-access.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "bastion-limited-access-allow-http-out-within-vpcs" {
  type              = "egress"
  security_group_id = aws_security_group.bastion-limited-access.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.content-prod.cidr_block, data.aws_vpc.frever-machine-learning-prod.cidr_block]
}

data "aws_security_group" "content-prod-observability" {
  vpc_id = data.aws_vpc.content-prod.id
  name   = "content-prod-observability"
}

resource "aws_security_group_rule" "bastion-limited-access-out-to-observability" {
  type                     = "egress"
  security_group_id        = aws_security_group.bastion-limited-access.id
  from_port                = 3000
  to_port                  = 3000
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.content-prod-observability.id
}

resource "aws_security_group" "content-prod-db" {
  name        = "content-prod-db"
  description = "Created by RDS management console"
  vpc_id      = data.aws_vpc.content-prod.id
  tags = {
    "Name" = "content-prod-db"
  }
}

resource "aws_security_group_rule" "content-prod-db-allow-postgresql-in" {
  type              = "ingress"
  security_group_id = aws_security_group.content-prod-db.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.content-prod.cidr_block]
}

resource "aws_security_group_rule" "content-prod-db-allow-debezium-msk-connector-in" {
  type                     = "ingress"
  security_group_id        = aws_security_group.content-prod-db.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.msk-debezium-connector-sg.id
}

data "aws_security_group" "msk-debezium-connector-sg" {
  provider = aws.machine-learning
  vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  name     = "msk-debezium-connector-sg"
}

resource "aws_security_group_rule" "content-prod-db-allow-machine-learning-pg-sg-in" {
  type                     = "ingress"
  security_group_id        = aws_security_group.content-prod-db.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = data.aws_security_group.machine-learning-pg-sg.id
}

data "aws_security_group" "machine-learning-pg-sg" {
  provider = aws.machine-learning
  vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  name     = "machine-learning-pg-sg"
}

