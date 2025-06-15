resource "aws_vpc" "content-prod" {
  cidr_block = "10.0.0.0/16"
  tags = {
    "kubernetes.io/cluster/content-prod" = "shared"
    Name                                 = "content-prod"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "content-prod-private" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.content-prod.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index + 1)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name                                 = "content-prod-private-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
    "kubernetes.io/role/internal-elb"    = "1"
  }
}

resource "aws_subnet" "content-prod-public" {
  count                   = length(sort(data.aws_availability_zones.available.names))
  vpc_id                  = aws_vpc.content-prod.id
  cidr_block              = cidrsubnet("10.0.0.0/16", 8, count.index + 4)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags = {
    Name                                 = "content-prod-public-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
    "kubernetes.io/role/elb"             = "1"
  }
}

resource "aws_subnet" "content-prod-db" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.content-prod.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index + 8)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name                                 = "content-prod-db-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

resource "aws_subnet" "content-prod-elasticache" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.content-prod.id
  cidr_block        = cidrsubnet("10.0.0.0/16", 8, count.index + 11)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name                                 = "content-prod-elasticache-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.content-prod.id
  tags = {
    Name                                 = "${aws_vpc.content-prod.tags["Name"]}-public"
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

resource "aws_route" "public-internet" {
  route_table_id         = aws_route_table.public.id
  gateway_id             = aws_internet_gateway.igw.id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.content-prod.id
  tags = {
    Name                                 = aws_vpc.content-prod.tags["Name"]
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

resource "aws_route_table_association" "public-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.public.id
  subnet_id      = aws_subnet.content-prod-public[count.index].id
}

resource "aws_route_table" "private" {
  count  = length(sort(data.aws_availability_zones.available.names))
  vpc_id = aws_vpc.content-prod.id
  tags = {
    Name                                 = "${aws_vpc.content-prod.tags["Name"]}-private-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

data "aws_eip" "nat-gateway-eips" {
  count = length(sort(data.aws_availability_zones.available.names))
  filter {
    name   = "tag:Name"
    values = ["content-prod-${data.aws_availability_zones.available.names[count.index]}-nat-gateway"]
  }
}

resource "aws_nat_gateway" "private-nat-gateway" {
  count         = length(sort(data.aws_availability_zones.available.names))
  subnet_id     = aws_subnet.content-prod-public[count.index].id
  allocation_id = data.aws_eip.nat-gateway-eips[count.index].id
  tags = {
    Name                                 = "content-prod-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/cluster/content-prod" = "shared"
  }
}

resource "aws_route" "private-nat" {
  count                  = length(sort(data.aws_availability_zones.available.names))
  route_table_id         = aws_route_table.private[count.index].id
  nat_gateway_id         = aws_nat_gateway.private-nat-gateway[count.index].id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_route_table_association" "private-db-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.private[count.index].id
  subnet_id      = aws_subnet.content-prod-db[count.index].id
}

resource "aws_route_table_association" "private-elasticache-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.private[count.index].id
  subnet_id      = aws_subnet.content-prod-elasticache[count.index].id
}

resource "aws_route_table_association" "private-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.private[count.index].id
  subnet_id      = aws_subnet.content-prod-private[count.index].id
}

