variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

# 保持期間はログ種別 × 環境で可変(infra ADR-0005 §3.3 / §6)。
# app   : アプリ / アクセスログ(MVP dev = 14 日。AWS 許容値に丸め)
# audit : セキュリティ監査証跡(MVP dev = 30 日)
# CloudWatch Logs の retention_in_days は離散の許容値のみ
# (0/1/3/5/7/14/30/60/90/... )を取る。「約 10 日」の意図を最近接の許容値 14 に丸める。
variable "log_retention_days" {
  type = object({
    app   = number
    audit = number
  })
  default     = { app = 14, audit = 30 }
  description = "CloudWatch Logs retention per log class (days). Overridable per environment (infra ADR-0005)."
}
