# ---------------------------------------------------------------------------
# SES 受信 — dev E2E メール検証(ADR-0041 / #843)
#
# e2e.<base_domain> 宛の *あらゆる* メール(サインアップ確認 / 招待 / パスワードリセット /
# 通知)を SES email receiving で受信し S3 に格納する。E2E テストが S3 の MIME を読んで
# リンク/トークンを抽出する。SES sandbox のまま運用するため、受信サブドメインを SES domain
# identity として検証(DKIM CNAME)して「sandbox の宛先は検証済みのみ」制約を満たす。
# SES email receiving は ap-northeast-1 対応済(2026-07-04 確認)。
# ---------------------------------------------------------------------------

data "aws_route53_zone" "base" {
  name         = "${var.base_domain}."
  private_zone = false
}

data "aws_caller_identity" "current" {}

locals {
  receiving_domain = "e2e.${var.base_domain}"
  bucket_name      = "platform-${var.env}-e2e-mail"
}

# --- SES domain identity(sandbox recipient 制限を満たすため e2e サブドメインを検証)---
resource "aws_sesv2_email_identity" "e2e" {
  email_identity = local.receiving_domain

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }

  tags = {
    Name = "platform-${var.env}-ses-identity-e2e"
  }
}

# DKIM CNAME ×3 — 伝播後に SES がドメインを自動検証する
resource "aws_route53_record" "dkim_cname" {
  count = 3

  zone_id = data.aws_route53_zone.base.zone_id
  name    = "${aws_sesv2_email_identity.e2e.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${local.receiving_domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.e2e.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# --- 受信 MX(e2e.<base_domain> → SES inbound、当該リージョン)---
resource "aws_route53_record" "inbound_mx" {
  zone_id = data.aws_route53_zone.base.zone_id
  name    = local.receiving_domain
  type    = "MX"
  ttl     = 300
  records = ["10 inbound-smtp.${var.region}.amazonaws.com"]
}

# --- 受信メール保存 S3 バケット ---
resource "aws_s3_bucket" "received" {
  bucket = local.bucket_name

  tags = {
    Name = "platform-${var.env}-e2e-mail"
  }
}

resource "aws_s3_bucket_public_access_block" "received" {
  bucket = aws_s3_bucket.received.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# SSE-S3(デフォルト暗号化を明示)。CMK は不採用(コスト増・効果限定、frontend と同方針)。
resource "aws_s3_bucket_server_side_encryption_configuration" "received" {
  bucket = aws_s3_bucket.received.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "received" {
  bucket = aws_s3_bucket.received.id

  versioning_configuration {
    status = "Enabled"
  }
}

# 受信テストメールは短命 — 7 日で失効(現行/旧バージョンとも)
resource "aws_s3_bucket_lifecycle_configuration" "received" {
  bucket = aws_s3_bucket.received.id

  rule {
    id     = "expire-received-mail"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.received]
}

# SES が受信メールを PutObject するのを許可(SourceAccount で自アカウントに限定)
resource "aws_s3_bucket_policy" "received" {
  bucket = aws_s3_bucket.received.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowSESReceiptPuts"
      Effect    = "Allow"
      Principal = { Service = "ses.amazonaws.com" }
      Action    = "s3:PutObject"
      Resource  = "${aws_s3_bucket.received.arn}/*"
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
      }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.received]
}

# --- SES receipt rule set / rule(e2e ドメイン宛 → S3)---
resource "aws_ses_receipt_rule_set" "e2e" {
  rule_set_name = "platform-${var.env}-e2e-mail"
}

resource "aws_ses_active_receipt_rule_set" "e2e" {
  rule_set_name = aws_ses_receipt_rule_set.e2e.rule_set_name
}

resource "aws_ses_receipt_rule" "to_s3" {
  name          = "platform-${var.env}-e2e-to-s3"
  rule_set_name = aws_ses_receipt_rule_set.e2e.rule_set_name
  recipients    = [local.receiving_domain]
  enabled       = true
  scan_enabled  = true
  tls_policy    = "Optional"

  s3_action {
    bucket_name       = aws_s3_bucket.received.id
    object_key_prefix = "inbound/"
    position          = 1
  }

  # SES は rule 作成時に S3 書込可否を検証するため、bucket policy 先行が必須
  depends_on = [aws_s3_bucket_policy.received]
}
