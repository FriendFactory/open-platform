# resource "aws_lb" "load-balancer" {
#   name                       = var.vpc_name
#   internal                   = false
#   load_balancer_type         = "application"
#   security_groups            = [aws_security_group.lb-sg.id]
#   subnets                    = [for subnet in aws_subnet.public : subnet.id]
#   enable_deletion_protection = false
#   access_logs {
#     bucket  = aws_s3_bucket.lb-access-logs.id
#     prefix  = "${var.vpc_name}-lb"
#     enabled = true
#   }
#   tags = {
#     "ingress.k8s.aws/stack"    = "frever-${var.vpc_name}-z"
#     "ingress.k8s.aws/resource" = "LoadBalancer"
#     "elbv2.k8s.aws/cluster"    = "${var.vpc_name}-eks-cluster"
#   }
# }
#
# resource "aws_security_group" "lb-sg" {
#   name        = "${var.vpc_name}-load-balancer-sg"
#   description = "${var.vpc_name} load balancer sg"
#   vpc_id      = aws_vpc.vpc.id
# }
#
# resource "aws_security_group_rule" "https-to-alb" {
#   security_group_id = aws_security_group.lb-sg.id
#   type              = "ingress"
#   from_port         = 443
#   to_port           = 443
#   protocol          = "tcp"
#   cidr_blocks       = ["0.0.0.0/0"]
# }
#
# resource "aws_security_group_rule" "alb-out-to-app" {
#   security_group_id        = aws_security_group.lb-sg.id
#   type                     = "egress"
#   from_port                = 80
#   to_port                  = 80
#   protocol                 = "tcp"
#   source_security_group_id = aws_security_group.app-sg.id
# }
#
# resource "aws_security_group" "app-sg" {
#   name        = "${var.vpc_name}-app-sg"
#   description = "${var.vpc_name} app sg"
#   vpc_id      = aws_vpc.vpc.id
# }
#
# resource "aws_security_group_rule" "app-sg-in-from-alb" {
#   type              = "ingress"
#   security_group_id = aws_security_group.app-sg.id
#   from_port         = 80
#   to_port           = 80
#   protocol          = "tcp"
#
#   source_security_group_id = aws_security_group.lb-sg.id
# }

resource "aws_s3_bucket" "lb-access-logs" {
  bucket = "${var.vpc_name}-lb-access-logs-${data.aws_region.current.name}"
}

resource "aws_s3_bucket_ownership_controls" "lb-access-logs-ownership-controls" {
  bucket = aws_s3_bucket.lb-access-logs.id
  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

resource "aws_s3_bucket_acl" "lb-access-logs-s3-acl" {
  depends_on = [aws_s3_bucket_ownership_controls.lb-access-logs-ownership-controls]
  bucket     = aws_s3_bucket.lb-access-logs.id
  acl        = "private"
}

resource "aws_s3_bucket_lifecycle_configuration" "lb-access-logs-s3-lifecycle" {
  bucket = aws_s3_bucket.lb-access-logs.id

  rule {
    id = "${var.vpc_name}-lb-access-logs-lifecycle"

    abort_incomplete_multipart_upload {
      days_after_initiation = 3
    }

    expiration {
      days = 20
    }
    filter {}
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "block" {
  bucket = aws_s3_bucket.lb-access-logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

data "aws_region" "current" {}

# https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
resource "aws_s3_bucket_policy" "lb-access-logs-bucket-policy" {
  bucket = aws_s3_bucket.lb-access-logs.id

  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnableLbToPutAccessLogsIntoBucket",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::054676820928:root"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
    },
    {
      "Sid": "AWSLogDeliveryWrite",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:PutObject",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}/*/AWSLogs/${data.aws_caller_identity.current.account_id}/*",
      "Condition": {
        "StringEquals": {
          "s3:x-amz-acl": "bucket-owner-full-control"
        }
      }
    },
    {
      "Sid": "AWSLogDeliveryAclCheck",
      "Effect": "Allow",
      "Principal": {
        "Service": "delivery.logs.amazonaws.com"
      },
      "Action": "s3:GetBucketAcl",
      "Resource": "${aws_s3_bucket.lb-access-logs.arn}"
    }
  ]
}
POLICY
}

# output "app-sg" {
#   description = "The security group for the app instances."
#   value       = aws_security_group.app-sg
# }
#
# output "lb-sg" {
#   description = "The security group for the application load balancer."
#   value       = aws_security_group.lb-sg
# }

