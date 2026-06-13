output "cloudfront_domain_name" {
  description = "CloudFront distribution domain name (for Route53 alias record)"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_hosted_zone_id" {
  description = "CloudFront hosted zone ID (Z2FDTNDATAQYW2, fixed constant for all CloudFront distributions)"
  value       = aws_cloudfront_distribution.frontend.hosted_zone_id
}

output "distribution_id" {
  description = "CloudFront distribution ID (used by S2Infra-6 cache invalidation)"
  value       = aws_cloudfront_distribution.frontend.id
}

output "distribution_arn" {
  description = "CloudFront distribution ARN"
  value       = aws_cloudfront_distribution.frontend.arn
}

output "bucket_id" {
  description = "S3 bucket name"
  value       = aws_s3_bucket.frontend.id
}

output "bucket_arn" {
  description = "S3 bucket ARN"
  value       = aws_s3_bucket.frontend.arn
}
