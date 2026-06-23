# ---------------------------------------------------------------------------
# ログ基盤 — Amazon CloudWatch Logs (S3Infra-3 / infra ADR-0005)
#
# MVP の一次ログ基盤として CloudWatch Logs を正式採用(ADR-0005 §3)。
# ロググループ命名は `/tasks/<env>/<service>` で ADR-0004 の tasks 専用
# `/tasks/*` 名前空間に揃える(ADR-0005 §3.2 / §6)。
# 保持期間はログ種別 × 環境で可変(var.log_retention_days)。
#
# 収集対象(tasks スコープ, ADR-0005 §3「収集対象ログ」):
#   - webapi アプリ / アクセスログ(JSON): logback → ECS awslogs ドライバで送出
#   - audit  セキュリティ監査証跡(クロステナント違反検知ログ等)
#
# 本モジュールが扱わないもの:
#   - Keycloak サーバログ: 共有 platform 所有(`/ecs/platform-<env>/keycloak`)。
#     Keycloak runtime は platform stack(ADR-0004)に属し、ログもそちらが所有する。
#   - ADOT Collector / EMF メトリクスログ: APM の関心事(ADR-0007)。
#     ECS task 定義と密結合のため ecs_cluster モジュールが所有する。
#   - metric filter / Alarm / SNS によるアラート連携(ADR-0005 §3.5):
#     アプリ側の構造化 JSON ログ出力(logback JSON, `level`/`event` フィールド)が
#     前提となるため、本 PR では未配線。app 側ログ整備とあわせて別 Issue で起票する。
# ---------------------------------------------------------------------------

# webapi アプリ / アクセスログ(構造化 JSON)。ECS awslogs ドライバの送出先。
resource "aws_cloudwatch_log_group" "webapi" {
  name              = "/tasks/${var.env}/webapi"
  retention_in_days = var.log_retention_days.app

  tags = {
    Name = "/tasks/${var.env}/webapi"
  }
}

# セキュリティ監査証跡(クロステナント違反検知ログ等, ADR-0005 §3)。
# 業務監査の SoT は audit_logs テーブル(ADR-0013)であり、本グループは
# CloudWatch 上で検索・保持・将来のアラート化を担う監査ログ集約先。
resource "aws_cloudwatch_log_group" "audit" {
  name              = "/tasks/${var.env}/audit"
  retention_in_days = var.log_retention_days.audit

  tags = {
    Name = "/tasks/${var.env}/audit"
  }
}
