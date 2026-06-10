package xyz.dgz48.tasks.webapi.tenant.usecase;

import xyz.dgz48.tasks.webapi.tenant.domain.PlatformMetrics;

/** プラットフォーム全体メトリクス取得ポート。 */
public interface PlatformMetricsPort {

  /** プラットフォーム全体のメトリクスを計算して返す。 */
  PlatformMetrics getMetrics();
}
