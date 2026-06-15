# ---------------------------------------------------------------------------
# Frontend S3 + CloudFront + OAC (S2Infra-3 / #480)
# S3: private bucket, accessed only via CloudFront OAC (no public access).
# CloudFront: SPA routing — 404/403 responses from S3 fall back to
#   /tasks.html (200) so direct URLs like /tasks/new work in-browser.
# ---------------------------------------------------------------------------

locals {
  s3_origin_id = "s3-tasks-${var.env}-frontend"
  # ADR-0022 §3.2 — CSP in Report-Only mode until violations are confirmed zero (§3.4)
  csp_report_only = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' https://api-${var.env}.tasks.${var.base_domain} https://auth-${var.env}.${var.base_domain}; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"
}

resource "aws_s3_bucket" "frontend" {
  bucket = "tasks-${var.env}-frontend"

  tags = {
    Name = "tasks-${var.env}-frontend"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ---------------------------------------------------------------------------
# Origin Access Control — modern replacement for OAI; SigV4-signs requests
# from CloudFront to S3 so the bucket can remain fully private.
# ---------------------------------------------------------------------------

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "tasks-${var.env}-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ---------------------------------------------------------------------------
# Response Headers Policy — security headers per ADR-0022 §3.2
# CSP is issued as Report-Only via custom_headers_config until violations are
# confirmed zero; switch to security_headers_config.content_security_policy
# (enforce) after verification (ADR-0022 §3.4).
# ---------------------------------------------------------------------------

resource "aws_cloudfront_response_headers_policy" "frontend" {
  name    = "tasks-${var.env}-frontend-security-headers"
  comment = "ADR-0022 §3.2 — security headers for tasks-${var.env} frontend"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = var.hsts_max_age_sec
      include_subdomains         = true
      preload                    = false
      override                   = true
    }

    content_type_options {
      override = true
    }

    referrer_policy {
      referrer_policy = "no-referrer"
      override        = true
    }

    frame_options {
      frame_option = "DENY"
      override     = true
    }
  }

  custom_headers_config {
    items {
      header   = "Content-Security-Policy-Report-Only"
      value    = local.csp_report_only
      override = true
    }

    items {
      header   = "Permissions-Policy"
      value    = "camera=(), microphone=(), geolocation=(), payment=(), usb=(), accelerometer=(), gyroscope=(), magnetometer=()"
      override = true
    }

    items {
      header   = "Cross-Origin-Opener-Policy"
      value    = "same-origin"
      override = true
    }

    items {
      header   = "Cross-Origin-Resource-Policy"
      value    = "same-origin"
      override = true
    }
  }
}

# ---------------------------------------------------------------------------
# CloudFront distribution
# PriceClass_200 covers Asia Pacific (Japan) at lower cost than PriceClass_All.
# SPA fallback: 404/403 from S3 → /tasks.html with HTTP 200.
#   - 404: key not found (e.g. /tasks/new, /tasks/{id}/edit)
#   - 403: safety net for any edge-case access-denied from S3
# ---------------------------------------------------------------------------

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = ["tasks-${var.env}.${var.base_domain}"]
  http_version        = "http2and3"
  price_class         = "PriceClass_200"

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = local.s3_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
    origin_path              = "/web/live"
  }

  default_cache_behavior {
    target_origin_id       = local.s3_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    # AWS-managed CachingOptimized policy (ID is the same across all accounts/regions)
    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"

    response_headers_policy_id = aws_cloudfront_response_headers_policy.frontend.id
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/tasks.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/tasks.html"
    error_caching_min_ttl = 0
  }

  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  tags = {
    Name = "tasks-${var.env}-cdn"
  }
}

# ---------------------------------------------------------------------------
# S3 bucket policy — grant CloudFront OAC s3:GetObject; deny all else.
# Condition on AWS:SourceArn scopes access to this distribution only.
# depends_on ensures public access block is in place before the policy.
# ---------------------------------------------------------------------------

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontOAC"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.frontend.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.frontend]
}
