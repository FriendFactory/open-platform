resource "aws_cloudfront_response_headers_policy" "no-ai-headers" {
  name = "no-ai-headers-policy"

  custom_headers_config {
    items {
      header   = "X-Robots-Tag"
      override = true
      value    = "noimageai, noai"
    }

    items {
      header   = "tdm-reservation"
      override = true
      value    = "1"
    }
  }
}
