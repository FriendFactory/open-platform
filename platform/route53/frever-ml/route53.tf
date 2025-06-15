resource "aws_route53_zone" "frever-ml" {
  name    = "frever-ml.com"
  comment = "HostedZone created by Route53 Registrar"
}
