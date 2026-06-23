# ---------------------------------------------------------------------------
# ログ基盤 — CloudWatch Logs (S3Infra-3 / infra ADR-0005)
# webapi アプリログ + 監査証跡ロググループ。保持期間は環境ごとに可変。
# ---------------------------------------------------------------------------

module "logging" {
  source = "../../modules/logging"

  env                = var.env
  log_retention_days = var.log_retention_days
}
