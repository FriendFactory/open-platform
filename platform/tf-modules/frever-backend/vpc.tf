resource "aws_vpc" "vpc" {
  cidr_block           = var.cidr_block
  enable_dns_hostnames = true
  tags = {
    Name = var.vpc_name
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "isolated" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.vpc.id
  cidr_block        = cidrsubnet(aws_vpc.vpc.cidr_block, 8, count.index + 3 * 5)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name = "${var.vpc_name}-isolated-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_subnet" "private" {
  count             = length(sort(data.aws_availability_zones.available.names))
  vpc_id            = aws_vpc.vpc.id
  cidr_block        = cidrsubnet(aws_vpc.vpc.cidr_block, 6, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = {
    Name                                                = "${var.vpc_name}-private-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/role/internal-elb"                   = 1
    "kubernetes.io/cluster/${var.vpc_name}-eks-cluster" = "shared"
  }
}

resource "aws_subnet" "public" {
  count                   = length(sort(data.aws_availability_zones.available.names))
  vpc_id                  = aws_vpc.vpc.id
  cidr_block              = cidrsubnet(aws_vpc.vpc.cidr_block, 8, count.index + 3 * 4)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags = {
    Name                                                = "${var.vpc_name}-public-${data.aws_availability_zones.available.names[count.index]}"
    "kubernetes.io/role/elb"                            = 1
    "kubernetes.io/cluster/${var.vpc_name}-eks-cluster" = "shared"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.vpc.id
  tags = {
    Name = "${aws_vpc.vpc.tags["Name"]}-public"
  }
}

resource "aws_route" "public-internet" {
  route_table_id         = aws_route_table.public.id
  gateway_id             = aws_internet_gateway.igw.id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.vpc.id
  tags = {
    Name = aws_vpc.vpc.tags["Name"]
  }
}

resource "aws_route_table_association" "public-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.public.id
  subnet_id      = aws_subnet.public[count.index].id
}

resource "aws_route_table" "private" {
  count  = length(sort(data.aws_availability_zones.available.names))
  vpc_id = aws_vpc.vpc.id
  tags = {
    Name = "${aws_vpc.vpc.tags["Name"]}-private-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_eip" "nat-eips" {
  count = var.nat_count
  tags = {
    Name = "${var.vpc_name}-${data.aws_availability_zones.available.names[count.index]}-nat"
  }
}

resource "aws_eip_association" "eip_assoc" {
  count         = var.use_nat_instance ? var.nat_count : 0
  instance_id   = aws_instance.nat[count.index].id
  allocation_id = aws_eip.nat-eips[count.index].id
}

resource "aws_nat_gateway" "private-nat-gateway" {
  count         = var.use_nat_instance ? 0 : var.nat_count
  subnet_id     = aws_subnet.public[count.index].id
  allocation_id = aws_eip.nat-eips[count.index].id
  tags = {
    Name = "${var.vpc_name}-${data.aws_availability_zones.available.names[count.index]}"
  }
}

resource "aws_route" "private-nat" {
  count                  = var.use_nat_instance ? 0 : length(data.aws_availability_zones.available.names)
  route_table_id         = aws_route_table.private[count.index].id
  nat_gateway_id         = var.nat_count > 1 ? aws_nat_gateway.private-nat-gateway[count.index].id : aws_nat_gateway.private-nat-gateway[0].id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_route_table_association" "private-subnet-route" {
  count          = length(sort(data.aws_availability_zones.available.names))
  route_table_id = aws_route_table.private[count.index].id
  subnet_id      = aws_subnet.private[count.index].id
}

resource "aws_route" "private-nat-ec2" {
  count                  = var.use_nat_instance ? length(data.aws_availability_zones.available.names) : 0
  route_table_id         = aws_route_table.private[count.index].id
  network_interface_id   = aws_instance.nat[var.nat_count == 1 ? 0 : count.index].primary_network_interface_id
  destination_cidr_block = "0.0.0.0/0"
}

output "vpc" {
  value = aws_vpc.vpc
}

