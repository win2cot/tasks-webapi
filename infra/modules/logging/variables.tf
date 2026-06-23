variable "env" {
  type        = string
  description = "Environment name (dev / stg / prd)"
}

# 保持期間はログ種別 × 環境で可変(infra ADR-0005 §3.3 / §6)。
# app   : アプリ / アクセスログ(MVP dev = 10 日)
# audit : セキュリティ監査証跡(MVP dev = 30 日)
variable "log_retention_days" {
  type = object({
    app   = number
    audit = number
  })
  default     = { app = 10, audit = 30 }
  description = "CloudWatch Logs retention per log class (days). Overridable per environment (infra ADR-0005)."
}
