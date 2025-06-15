data "aws_vpc" "frever-machine-learning-prod" {
  provider = aws.machine-learning
  tags = {
    Name = "prod"
  }
}

data "aws_vpc" "frever-machine-learning-us-default" {
  provider = aws.frever-ml-us
  tags = {
    Name = "default"
  }
}

data "aws_vpc" "frever-prod" {
  tags = {
    Name = "content-prod"
  }
}

data "aws_vpc" "frever-dev" {
  tags = {
    Name = "dev"
  }
}

data "aws_vpc" "frever-ixia-prod" {
  tags = {
    Name = "ixia-prod"
  }
}

data "aws_vpc" "frever-prod-us-east-2" {
  provider = aws.frever-us
  tags = {
    Name = "prod"
  }
}

data "aws_caller_identity" "frever-machine-learning" {
  provider = aws.machine-learning
}

data "aws_caller_identity" "frever-us-east-2" {
  provider = aws.frever-us
}

data "aws_route_tables" "frever-content-prod-route-tables" {
  vpc_id = data.aws_vpc.frever-prod.id
  filter {
    name   = "tag:Name"
    values = ["content-prod*"]
  }
}

data "aws_route_tables" "frever-dev-route-tables" {
  vpc_id = data.aws_vpc.frever-dev.id
  filter {
    name   = "tag:Name"
    values = ["dev-*"]
  }
}

data "aws_route_tables" "frever-ixia-prod-route-tables" {
  vpc_id = data.aws_vpc.frever-ixia-prod.id
  filter {
    name   = "tag:Name"
    values = ["ixia-prod-*"]
  }
}

data "aws_route_tables" "frever-machine-learning-prod-route-tables" {
  vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  provider = aws.machine-learning
  filter {
    name   = "tag:Name"
    values = ["prod*"]
  }
}

data "aws_route_tables" "frever-machine-learning-us-default-route-tables" {
  vpc_id   = data.aws_vpc.frever-machine-learning-us-default.id
  provider = aws.frever-ml-us
  filter {
    name   = "tag:Name"
    values = ["default"]
  }
}

data "aws_route_tables" "frever-prod-us-east-2-route-tables" {
  vpc_id   = data.aws_vpc.frever-prod-us-east-2.id
  provider = aws.frever-us
  filter {
    name   = "tag:Name"
    values = ["prod*"]
  }
}

# ======================== frever-prod with frever-machine-learning-prod ========================
resource "aws_route" "frever-content-prod-route-to-machine-learning-prod-vpc" {
  for_each                  = toset(data.aws_route_tables.frever-content-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer.id
}

resource "aws_route" "frever-machine-learning-prod-route-to-content-prod-vpc" {
  provider                  = aws.machine-learning
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer.id
}

resource "aws_vpc_peering_connection" "frever-peer" {
  vpc_id        = data.aws_vpc.frever-prod.id
  peer_vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  peer_owner_id = data.aws_caller_identity.frever-machine-learning.account_id
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-content-prod-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-machine-learning-peer" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-content-prod-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_options" "requester" {
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-prod with frever-machine-learning-prod ========================

# ======================== frever-dev with frever-machine-learning-prod ========================
resource "aws_route" "frever-dev-route-to-machine-learning-prod-vpc" {
  for_each                  = toset(data.aws_route_tables.frever-dev-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-dev.id
}

resource "aws_route" "frever-machine-learning-prod-route-to-frever-dev-vpc" {
  provider                  = aws.machine-learning
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-dev.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-dev.id
}

resource "aws_vpc_peering_connection" "frever-peer-dev" {
  vpc_id        = data.aws_vpc.frever-dev.id
  peer_vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  peer_owner_id = data.aws_caller_identity.frever-machine-learning.account_id
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-dev-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-machine-learning-peer-dev" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-dev.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-dev-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_options" "requester-dev" {
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer-dev.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter-dev" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer-dev.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-dev with frever-machine-learning-prod ========================

# ======================== frever-prod with prod in us-east-2 ========================
resource "aws_route" "frever-content-prod-route-to-frever-prod-us-east-2-vpc" {
  for_each                  = toset(data.aws_route_tables.frever-content-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-prod-us-east-2.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-content-prod-peer.id
}

resource "aws_route" "frever-prod-us-east-2-route-to-content-prod-vpc" {
  provider                  = aws.frever-us
  for_each                  = toset(data.aws_route_tables.frever-prod-us-east-2-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-content-prod-peer.id
}

resource "aws_vpc_peering_connection" "frever-content-prod-peer" {
  vpc_id        = data.aws_vpc.frever-prod.id
  peer_vpc_id   = data.aws_vpc.frever-prod-us-east-2.id
  peer_owner_id = data.aws_caller_identity.frever-us-east-2.account_id
  peer_region   = "us-east-2"
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-content-prod-and-frever-prod-us-east-2"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-prod-us-east-2-peer" {
  provider                  = aws.frever-us
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-content-prod-peer.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-content-prod-and-frever-prod-us-east-2"
  }
}

resource "aws_vpc_peering_connection_options" "requester-prod-us-east-2" {
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-prod-us-east-2-peer.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter-prod-us-east-2" {
  provider                  = aws.frever-us
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-prod-us-east-2-peer.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-prod with prod in us-east-2 ========================

# ======================== frever-prod in us-east-2 with frever-machine-learning-prod ========================
resource "aws_route" "frever-prod-us-east-2-route-to-machine-learning-prod-vpc" {
  provider                  = aws.frever-us
  for_each                  = toset(data.aws_route_tables.frever-prod-us-east-2-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-prod-us-east-2-peer-machine-learning-prod.id
}

resource "aws_route" "frever-machine-learning-prod-route-to-frever-prod-us-east-2-vpc" {
  provider                  = aws.machine-learning
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-prod-us-east-2.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-prod-us-east-2-peer-machine-learning-prod.id
}

resource "aws_vpc_peering_connection" "frever-prod-us-east-2-peer-machine-learning-prod" {
  provider      = aws.frever-us
  vpc_id        = data.aws_vpc.frever-prod-us-east-2.id
  peer_vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  peer_owner_id = data.aws_caller_identity.frever-machine-learning.account_id
  peer_region   = "eu-central-1"
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-prod-us-east-2-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-machine-learning-peer-prod-us-east-2" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-prod-us-east-2-peer-machine-learning-prod.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-prod-us-east-2-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_options" "requester-frever-prod-us-east-2-to-machine-learning" {
  provider                  = aws.frever-us
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-prod-us-east-2-peer-machine-learning-prod.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter-frever-prod-us-east-2-to-machine-learning" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-prod-us-east-2-peer-machine-learning-prod.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-prod in us-east-2 with frever-machine-learning-prod ========================


# ======================== frever-machine-learning default with frever-machine-learning-prod ========================
resource "aws_route" "frever-machine-learning-us-default-route-to-machine-learning-prod-vpc" {
  provider                  = aws.frever-ml-us
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-us-default-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-machine-learning-us-default-peer-machine-learning-prod.id
}

resource "aws_route" "frever-machine-learning-prod-route-to-frever-machine-learning-us-default-vpc" {
  provider                  = aws.machine-learning
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-us-default.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-machine-learning-us-default-peer-machine-learning-prod.id
}

resource "aws_vpc_peering_connection" "frever-machine-learning-us-default-peer-machine-learning-prod" {
  provider      = aws.frever-ml-us
  vpc_id        = data.aws_vpc.frever-machine-learning-us-default.id
  peer_vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  peer_owner_id = data.aws_caller_identity.frever-machine-learning.account_id
  peer_region   = "eu-central-1"
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-machine-learning-us-default-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-machine-learning-prod-peer-machine-learning-us-default" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-machine-learning-us-default-peer-machine-learning-prod.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-machine-learning-us-default-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_options" "requester-frever-machine-learning-us-default" {
  provider                  = aws.frever-ml-us
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-prod-peer-machine-learning-us-default.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter-frever-machine-learning-us-default" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-prod-peer-machine-learning-us-default.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-dev with frever-machine-learning-prod ========================

# ======================== frever-ixia-prod with frever-machine-learning-prod ========================
resource "aws_route" "frever-ixia-prod-route-to-machine-learning-prod-vpc" {
  for_each                  = toset(data.aws_route_tables.frever-ixia-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-machine-learning-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-ixia-prod.id
}

resource "aws_route" "frever-machine-learning-prod-route-to-frever-ixia-prod-vpc" {
  provider                  = aws.machine-learning
  for_each                  = toset(data.aws_route_tables.frever-machine-learning-prod-route-tables.ids)
  route_table_id            = each.key
  destination_cidr_block    = data.aws_vpc.frever-ixia-prod.cidr_block
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-ixia-prod.id
}

resource "aws_vpc_peering_connection" "frever-peer-ixia-prod" {
  vpc_id        = data.aws_vpc.frever-ixia-prod.id
  peer_vpc_id   = data.aws_vpc.frever-machine-learning-prod.id
  peer_owner_id = data.aws_caller_identity.frever-machine-learning.account_id
  auto_accept   = false
  tags = {
    Side = "Requester"
    Name = "frever-ixia-prod-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_accepter" "frever-machine-learning-peer-ixia-prod" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection.frever-peer-ixia-prod.id
  auto_accept               = true
  tags = {
    Side = "Accepter"
    Name = "frever-ixia-prod-and-frever-machine-learning"
  }
}

resource "aws_vpc_peering_connection_options" "requester-ixia-prod" {
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer-ixia-prod.id
  requester {
    allow_remote_vpc_dns_resolution = true
  }
}

resource "aws_vpc_peering_connection_options" "accepter-ixia-prod" {
  provider                  = aws.machine-learning
  vpc_peering_connection_id = aws_vpc_peering_connection_accepter.frever-machine-learning-peer-ixia-prod.id
  accepter {
    allow_remote_vpc_dns_resolution = true
  }
}
# ======================== frever-dev with frever-machine-learning-prod ========================
