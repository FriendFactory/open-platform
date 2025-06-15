data "aws_vpc" "frever-prod" {
  provider = aws.frever
  tags = {
    Name = "content-prod"
  }
}

data "aws_vpc" "frever-ixia-prod" {
  provider = aws.frever
  tags = {
    Name = "ixia-prod"
  }
}

data "aws_vpc" "frever-prod-us" {
  provider = aws.frever-us
  tags = {
    Name = "prod"
  }
}

data "aws_subnet" "frever-prod-private" {
  provider = aws.frever
  count    = length(sort(data.aws_availability_zones.available.names))
  vpc_id   = data.aws_vpc.frever-prod.id
  filter {
    name   = "tag:Name"
    values = ["${data.aws_vpc.frever-prod.tags["Name"]}-private-${data.aws_availability_zones.available.names[count.index]}"]
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "prod" {
  cidr_block           = "10.11.0.0/16"
  enable_dns_hostnames = true
  tags = {
    Name = "prod"
  }
}

resource "aws_subnet" "prod-isolated" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.prod.id
  cidr_block        = cidrsubnet(aws_vpc.prod.cidr_block, 8, count.index + 3 * 5)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name = "prod-isolated-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_subnet" "prod-private" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.prod.id
  cidr_block        = cidrsubnet(aws_vpc.prod.cidr_block, 6, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name = "prod-private-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_subnet" "prod-public" {
  count                   = length(sort(data.aws_availability_zones.available.names))
  vpc_id                  = aws_vpc.prod.id
  cidr_block              = cidrsubnet(aws_vpc.prod.cidr_block, 8, count.index + 3 * 4)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags = {
    Name = "prod-public-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.prod.id
  tags = {
    Name = "${aws_vpc.prod.tags["Name"]}-public"
  }
}

resource "aws_route" "public-internet" {
  route_table_id         = aws_route_table.public.id
  gateway_id             = aws_internet_gateway.igw.id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.prod.id
  tags = {
    Name = aws_vpc.prod.tags["Name"]
  }
}

resource "aws_route_table_association" "public-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.public.id
  subnet_id      = aws_subnet.prod-public[count.index].id
}

resource "aws_route_table" "private" {
  count  = length(sort(data.aws_availability_zones.available.names))
  vpc_id = aws_vpc.prod.id
  tags = {
    Name = "${aws_vpc.prod.tags["Name"]}-private-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_eip" "nat-gateway-eips" {
  count = length(sort(data.aws_availability_zones.available.names))
  tags = {
    Name = "prod-${data.aws_availability_zones.available.names[count.index]}-nat-gateway"
  }
}

resource "aws_nat_gateway" "private-nat-gateway" {
  count         = length(sort(data.aws_availability_zones.available.names))
  subnet_id     = aws_subnet.prod-public[count.index].id
  allocation_id = aws_eip.nat-gateway-eips[count.index].id
  tags = {
    Name = "prod-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_route" "private-nat" {
  count                  = length(sort(data.aws_availability_zones.available.names))
  route_table_id         = aws_route_table.private[count.index].id
  nat_gateway_id         = aws_nat_gateway.private-nat-gateway[count.index].id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_route_table_association" "private-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.private[count.index].id
  subnet_id      = aws_subnet.prod-private[count.index].id
}

resource "aws_vpc_endpoint" "ssm" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.ssm"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "ec2messages" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.ec2messages"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "ec2" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.ec2"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.s3"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "ssmmessages" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.ssmmessages"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "kms" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.kms"
  vpc_endpoint_type = "Interface"
}

resource "aws_vpc_endpoint" "logs" {
  vpc_id            = aws_vpc.prod.id
  service_name      = "com.amazonaws.eu-central-1.logs"
  vpc_endpoint_type = "Interface"
}

