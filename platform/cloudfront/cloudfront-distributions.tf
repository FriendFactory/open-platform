locals {
  s3_bucket_names             = ["frever-dev", "frever-dev-2", "frever-content-test", "frever-content-stage", "frever-content"]
  alt_domain_name_prefixes    = { "frever-dev" = "dev-1", "frever-dev-2" = "dev-2", "frever-content-test" = "content-test", "frever-content-stage" = "content-stage", "frever-content" = "content-prod" }
  s3_origin_id                = "s3"
  cors_s3_origin_policy_id    = "88a5eaf4-2fd4-4709-b370-b4c650ea3fcf"
  caching_optimized_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  s3_origin_access_identity   = { "frever-dev" = "E2I41O55S44AHR", "frever-dev-2" = "E1W9FMXEHRZHGE", "frever-content-test" = "E1VEB01V6PHI5K", "frever-content-stage" = "E2G40LWL5NWQ5O", "frever-content" = "E3QNUTDXJLDHG1" }
}

data "aws_s3_bucket" "bucket" {
  for_each = toset(local.s3_bucket_names)
  bucket   = each.key
}

data "aws_s3_bucket" "cloud-front-logs" {
  bucket = "frever-cloudfront-logs"
}

data "aws_acm_certificate" "frever-content" {
  provider = aws.frever-nv
  domain   = "frever-content.com"
  types    = ["AMAZON_ISSUED"]
  statuses = ["ISSUED"]
}

resource "aws_cloudfront_distribution" "s3_distribution" {
  for_each = toset(local.s3_bucket_names)
  origin {
    domain_name = data.aws_s3_bucket.bucket[each.key].bucket_regional_domain_name
    origin_id   = local.s3_origin_id

    s3_origin_config {
      origin_access_identity = "origin-access-identity/cloudfront/${local.s3_origin_access_identity[each.key]}"
    }

    dynamic "origin_shield" {
      for_each = each.key == "frever-content" ? ["apply"] : []
      content {
        enabled              = true
        origin_shield_region = "eu-central-1"
      }
    }
  }

  enabled         = true
  is_ipv6_enabled = each.key == "frever-content" ? true : false

  logging_config {
    bucket          = data.aws_s3_bucket.cloud-front-logs.bucket_domain_name
    prefix          = "${local.alt_domain_name_prefixes[each.key]}/logs/l"
    include_cookies = each.key == "frever-content" ? true : false
  }

  aliases = ["${local.alt_domain_name_prefixes[each.key]}.frever-content.com"]

  default_cache_behavior {
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.caching_optimized_policy_id
    target_origin_id           = local.s3_origin_id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.no-ai-headers.id
    viewer_protocol_policy     = "allow-all"
    compress                   = true
    smooth_streaming           = true
  }

  ordered_cache_behavior {
    path_pattern               = "**/Thumbnail/**"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.caching_optimized_policy_id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.no-ai-headers.id
    target_origin_id           = local.s3_origin_id
    viewer_protocol_policy     = "allow-all"
  }

  ordered_cache_behavior {
    path_pattern               = "**/Thumbnail*.*"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.caching_optimized_policy_id
    origin_request_policy_id   = local.cors_s3_origin_policy_id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.no-ai-headers.id
    target_origin_id           = local.s3_origin_id
    viewer_protocol_policy     = "allow-all"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
      locations        = []
    }
  }

  viewer_certificate {
    acm_certificate_arn            = data.aws_acm_certificate.frever-content.arn
    cloudfront_default_certificate = false
    ssl_support_method             = "sni-only"
    minimum_protocol_version       = "TLSv1.2_2021"
  }
}

