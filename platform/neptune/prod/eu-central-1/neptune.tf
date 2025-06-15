data "aws_vpc" "prod" {
  tags = {
    Name = "prod"
  }
}

resource "aws_security_group" "machine-learning-neptune-client-sg" {
  vpc_id = data.aws_vpc.prod.id
  name   = "machine-learning-neptune-client-sg"
  tags = {
    "Name" = "machine-learning-neptune-client-sg"
  }
}

