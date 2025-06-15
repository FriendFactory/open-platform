data "aws_vpc" "default" {
  tags = {
    Name = "Default"
  }
}

data "aws_vpc" "dev-1" {
  tags = {
    Name = "dev-1"
  }
}

data "aws_vpc" "dev-2" {
  tags = {
    Name = "dev-2"
  }
}

data "aws_vpc" "content-test" {
  tags = {
    Name = "content-test"
  }
}

data "aws_vpc" "content-stage" {
  tags = {
    Name = "content-stage"
  }
}

resource "aws_security_group" "ml-pro-server" {
  name        = "ml-pro-server"
  description = "SG for ml-pro-server instance"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name" = "ml-pro-server"
  }
}

resource "aws_security_group" "ml-routing-server" {
  name        = "ml-routing-server"
  description = "SG for ml-routing-server instance"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name" = "ml-routing-server"
  }
}

resource "aws_security_group_rule" "ml-pro-server-allow-https" {
  type              = "egress"
  security_group_id = aws_security_group.ml-pro-server.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "ml-routing-server-allow-https" {
  type              = "egress"
  security_group_id = aws_security_group.ml-routing-server.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "ml-pro-server-allow-http-in" {
  type              = "ingress"
  security_group_id = aws_security_group.ml-pro-server.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  source_security_group_id = aws_security_group.ml-routing-server.id
}

resource "aws_security_group_rule" "ml-routing-server-allow-http-out-to-ml-pro-server" {
  type              = "egress"
  security_group_id = aws_security_group.ml-routing-server.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  source_security_group_id = aws_security_group.ml-pro-server.id
}

resource "aws_security_group_rule" "ml-pro-server-allow-https-in" {
  type              = "ingress"
  security_group_id = aws_security_group.ml-pro-server.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  source_security_group_id = aws_security_group.ml-routing-server.id
}

resource "aws_security_group_rule" "ml-routing-server-allow-http-in" {
  type              = "ingress"
  security_group_id = aws_security_group.ml-routing-server.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "ml-routing-server-allow-https-in" {
  type              = "ingress"
  security_group_id = aws_security_group.ml-routing-server.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group" "jenkins" {
  name        = "jenkins-sg"
  description = "SG for Jenkins instance"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name" = "jenkins"
  }
}

resource "aws_security_group" "jenkins-http-alb" {
  name        = "jenkins-http-sg"
  description = "SG for jenkins-http ALB"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name" = "jenkins-http"
  }
}

resource "aws_security_group" "jenkins-static-content-alb" {
  name        = "jenkins-static-content-sg"
  description = "SG for jenkins-static-content ALB"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name" = "jenkins-static-content"
  }
}

resource "aws_security_group_rule" "jenkins-allow-https" {
  type              = "egress"
  security_group_id = aws_security_group.jenkins.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "jenkins-allow-http" {
  type              = "egress"
  security_group_id = aws_security_group.jenkins.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "jenkins-allow-ssh-out" {
  type              = "egress"
  security_group_id = aws_security_group.jenkins.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "jenkins-http-alb-allow-https" {
  type              = "ingress"
  security_group_id = aws_security_group.jenkins-http-alb.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "jenkins-http-alb-to-jenkins-instance" {
  type              = "egress"
  security_group_id = aws_security_group.jenkins-http-alb.id
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "jenkins-allow-jenkins-http-alb" {
  type              = "ingress"
  security_group_id = aws_security_group.jenkins.id
  from_port         = 8080
  to_port           = 8080
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jenkins-http-alb.id
}

resource "aws_security_group_rule" "jenkins-static-content-alb-allow-https" {
  type              = "ingress"
  security_group_id = aws_security_group.jenkins-static-content-alb.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "jenkins-static-content-alb-to-jenkins-instance" {
  type              = "egress"
  security_group_id = aws_security_group.jenkins-static-content-alb.id
  from_port         = 8888
  to_port           = 8888
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jenkins.id
}

resource "aws_security_group_rule" "jenkins-allow-jenkins-static-content-alb" {
  type              = "ingress"
  security_group_id = aws_security_group.jenkins.id
  from_port         = 8888
  to_port           = 8888
  protocol          = "tcp"

  source_security_group_id = aws_security_group.jenkins-static-content-alb.id
}

resource "aws_security_group" "dev-1-bastion" {
  name        = "dev-1-ssh-bastion"
  description = "SG for the ssh bastion instance in VPC dev-1"
  vpc_id      = data.aws_vpc.dev-1.id
  tags = {
    "Name" = "dev-1-ssh-bastion"
  }
}

resource "aws_security_group_rule" "dev-1-ssh-bastion-allow-ssh" {
  type              = "ingress"
  security_group_id = aws_security_group.dev-1-bastion.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "dev-1-ssh-bastion-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.dev-1-bastion.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "dev-1-ssh-bastion-allow-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.dev-1-bastion.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "dev-1-ssh-bastion-allow-any-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.dev-1-bastion.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.dev-1.cidr_block]
}

resource "aws_security_group" "content-test-bastion" {
  name        = "content-test-ssh-bastion"
  description = "SG for the ssh bastion instance in VPC content-test"
  vpc_id      = data.aws_vpc.content-test.id
  tags = {
    "Name" = "content-test-ssh-bastion"
  }
}

resource "aws_security_group_rule" "content-test-ssh-bastion-allow-ssh" {
  type              = "ingress"
  security_group_id = aws_security_group.content-test-bastion.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "content-test-ssh-bastion-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-test-bastion.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-test-ssh-bastion-allow-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-test-bastion.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-test-ssh-bastion-allow-postgres-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-test-bastion.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-test-ssh-bastion-allow-any-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.content-test-bastion.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.content-test.cidr_block]
}

resource "aws_security_group" "content-stage-bastion" {
  name        = "content-stage-ssh-bastion"
  description = "Content-Stage: Security group for SSH bastion"
  vpc_id      = data.aws_vpc.content-stage.id
  tags = {
    "Name" = "content-stage-ssh-bastion"
  }
}

resource "aws_security_group_rule" "content-stage-ssh-bastion-allow-ssh" {
  type              = "ingress"
  security_group_id = aws_security_group.content-stage-bastion.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "content-stage-ssh-bastion-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-stage-bastion.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-stage-ssh-bastion-allow-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.content-stage-bastion.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "content-stage-ssh-bastion-allow-any-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.content-stage-bastion.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.content-stage.cidr_block]
}

resource "aws_security_group" "redshift" {
  name        = "redshift-security-group"
  description = "Whitelist access to Redshift cluster from specified IP"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name"       = "redshift-sg"
    "frever-env" = "general"
  }
}

resource "aws_security_group_rule" "redshift-out-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.redshift.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.default.cidr_block]
}

resource "aws_security_group_rule" "redshift-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.redshift.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "redshift-allow-5439-vpc" {
  type              = "ingress"
  description       = "Default VPC CIDR"
  security_group_id = aws_security_group.redshift.id
  from_port         = 5439
  to_port           = 5439
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.default.cidr_block]
}

resource "aws_security_group" "content-test-rds" {
  name        = "content-test-db"
  description = "Sg for content-test RDS (main)"
  vpc_id      = data.aws_vpc.default.id
  tags = {
    "Name"       = "content-test-db"
    "frever-env" = "general"
  }
}

resource "aws_security_group_rule" "rds-in-within-vpc" {
  type              = "ingress"
  security_group_id = aws_security_group.content-test-rds.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.default.cidr_block]
}

resource "aws_security_group" "dev-1-rds" {
  name        = "dev-1-db"
  description = "Sg for dev-1 RDS"
  vpc_id      = data.aws_vpc.dev-1.id
  tags = {
    "Name"       = "dev-1-db"
    "frever-env" = "dev-1"
  }
}

resource "aws_security_group_rule" "dev-1-rds-in-from-bastion" {
  type                     = "ingress"
  security_group_id        = aws_security_group.dev-1-rds.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.dev-1-bastion.id
}

resource "aws_security_group_rule" "dev-1-rds-in-from-vpc" {
  type              = "ingress"
  security_group_id = aws_security_group.dev-1-rds.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.dev-1.cidr_block]
}

resource "aws_security_group" "dev-2-rds" {
  name        = "dev-2-db"
  description = "Sg for dev-2 RDS"
  vpc_id      = data.aws_vpc.dev-2.id
  tags = {
    "Name"       = "dev-2-db"
    "frever-env" = "dev-2"
  }
}

resource "aws_security_group_rule" "dev-2-rds-in-from-bastion" {
  type                     = "ingress"
  security_group_id        = aws_security_group.dev-2-rds.id
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.dev-2-bastion.id
}

resource "aws_security_group_rule" "dev-2-rds-in-from-vpc" {
  type              = "ingress"
  security_group_id = aws_security_group.dev-2-rds.id
  from_port         = 5432
  to_port           = 5432
  protocol          = "tcp"
  cidr_blocks       = [data.aws_vpc.dev-2.cidr_block]
}

resource "aws_security_group" "dev-2-bastion" {
  name        = "dev-2-ssh-bastion"
  description = "SG for the ssh bastion instance in VPC dev-2"
  vpc_id      = data.aws_vpc.dev-2.id
  tags = {
    "Name" = "dev-2-ssh-bastion"
  }
}

resource "aws_security_group_rule" "dev-2-ssh-bastion-allow-ssh" {
  type              = "ingress"
  security_group_id = aws_security_group.dev-2-bastion.id
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"

  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]
}

resource "aws_security_group_rule" "dev-2-ssh-bastion-allow-https-out" {
  type              = "egress"
  security_group_id = aws_security_group.dev-2-bastion.id
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "dev-2-ssh-bastion-allow-http-out" {
  type              = "egress"
  security_group_id = aws_security_group.dev-2-bastion.id
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"

  cidr_blocks = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "dev-2-ssh-bastion-allow-any-within-vpc" {
  type              = "egress"
  security_group_id = aws_security_group.dev-2-bastion.id
  from_port         = 0
  to_port           = 65535
  protocol          = "all"
  cidr_blocks       = [data.aws_vpc.dev-2.cidr_block]
}

