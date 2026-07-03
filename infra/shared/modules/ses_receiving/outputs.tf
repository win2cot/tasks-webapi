output "receiving_domain" {
  description = "受信サブドメイン(E2E テストの宛先: <local>@<receiving_domain>)"
  value       = local.receiving_domain
}

output "mail_bucket_name" {
  description = "受信メールを格納する S3 バケット名"
  value       = aws_s3_bucket.received.id
}

output "mail_bucket_arn" {
  description = "受信メール S3 バケットの ARN"
  value       = aws_s3_bucket.received.arn
}

output "mail_object_key_prefix" {
  description = "受信メールオブジェクトの key prefix"
  value       = "inbound/"
}
