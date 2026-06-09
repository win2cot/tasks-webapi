package xyz.dgz48.tasks.webapi.shared.infra;

import java.time.ZoneId;

/** アプリケーション全体で使用するタイムゾーン定数(JST 全層統一: #265 / ADR-0009)。 */
public final class AppZones {

  /** アプリケーション標準タイムゾーン(Asia/Tokyo)。 */
  public static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  private AppZones() {}
}
