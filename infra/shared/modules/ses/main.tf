# ---------------------------------------------------------------------------
# Route53 zone lookup — shared public hosted zone (dgz48.xyz)
# ---------------------------------------------------------------------------

data "aws_route53_zone" "base" {
  name         = "${var.base_domain}."
  private_zone = false
}

# ---------------------------------------------------------------------------
# SES Email Identity — mail.<base_domain> (shared sender subdomain)
# Easy DKIM: RSA 2048-bit, SES-managed key rotation (BYODKIM not adopted)
# ---------------------------------------------------------------------------

resource "aws_sesv2_email_identity" "mail" {
  email_identity = "mail.${var.base_domain}"

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }

  tags = {
    Name = "platform-${var.env}-ses-identity-mail"
  }
}

# ---------------------------------------------------------------------------
# DKIM CNAME records ×3 — SES auto-verifies on propagation
# Name:  <token>._domainkey.mail.<base_domain>
# Value: <token>.dkim.amazonses.com
# ---------------------------------------------------------------------------

resource "aws_route53_record" "dkim_cname" {
  count = 3

  zone_id = data.aws_route53_zone.base.zone_id
  name    = "${aws_sesv2_email_identity.mail.dkim_signing_attributes[0].tokens[count.index]}._domainkey.mail.${var.base_domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.mail.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# ---------------------------------------------------------------------------
# Custom MAIL FROM — bounce.mail.<base_domain>
# Enables SPF alignment; USE_DEFAULT_VALUE falls back to SES default on MX failure
# ---------------------------------------------------------------------------

resource "aws_sesv2_email_identity_mail_from_attributes" "mail" {
  email_identity         = aws_sesv2_email_identity.mail.email_identity
  mail_from_domain       = "bounce.mail.${var.base_domain}"
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

resource "aws_route53_record" "mail_from_mx" {
  zone_id = data.aws_route53_zone.base.zone_id
  name    = "bounce.mail.${var.base_domain}"
  type    = "MX"
  ttl     = 300
  records = ["10 feedback-smtp.${var.region}.amazonses.com"]
}

resource "aws_route53_record" "mail_from_spf" {
  zone_id = data.aws_route53_zone.base.zone_id
  name    = "bounce.mail.${var.base_domain}"
  type    = "TXT"
  ttl     = 300
  records = ["v=spf1 include:amazonses.com -all"]
}

# ---------------------------------------------------------------------------
# DMARC — monitoring mode (p=none); tighten to quarantine/reject after review
# ---------------------------------------------------------------------------

resource "aws_route53_record" "dmarc" {
  zone_id = data.aws_route53_zone.base.zone_id
  name    = "_dmarc.mail.${var.base_domain}"
  type    = "TXT"
  ttl     = 300
  records = ["v=DMARC1; p=none"]
}

# ---------------------------------------------------------------------------
# Config Set — referenced by all projects via SSM /platform/<env>/ses-config-set
# ---------------------------------------------------------------------------

resource "aws_sesv2_configuration_set" "main" {
  configuration_set_name = "platform-${var.env}-ses"

  tags = {
    Name = "platform-${var.env}-ses-config-set"
  }
}
