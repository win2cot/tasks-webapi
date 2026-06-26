package xyz.dgz48.tasks.webapi.tenant.domain;

import java.util.Locale;

/**
 * テナント表示名から URL slug 用の {@code code} を生成するドメインサービス(Spring 非依存)。
 *
 * <p>生成ルール(基本設計書 §5.1 / OpenAPI {@code Tenant.code}): 小文字化・{@code [a-z0-9]} 以外をハイフンに置換・連続ハイフンを 1
 * つに圧縮・先頭末尾のハイフンを除去・最大 {@value #MAX_LENGTH} 文字。日本語など {@code [a-z0-9]} を一切含まない名前では slug が空になるため、
 * フォールスルー値 {@value #FALLBACK} を用いる(一意化のサフィックスは呼び出し側が付与する)。生成結果は必ず正規表現 {@code ^[a-z0-9-]+$} を満たす。
 */
public final class TenantCodeGenerator {

  /** {@code tenants.code} の最大長(OpenAPI / DDL と一致)。 */
  public static final int MAX_LENGTH = 50;

  /** slug 化で有効文字が残らなかった場合の基底コード。 */
  public static final String FALLBACK = "tenant";

  /**
   * 表示名から基底 slug を生成する(サフィックス未付与)。
   *
   * @param name テナント表示名(空白のみは呼び出し前に検証済みである前提)
   * @return {@code ^[a-z0-9-]+$} を満たす 1〜{@value #MAX_LENGTH} 文字の slug
   */
  public String toBaseCode(String name) {
    String slug =
        name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-") // 非英数字(空白・記号・非ASCII)をハイフンに
            .replaceAll("-{2,}", "-") // 連続ハイフンを 1 つに圧縮
            .replaceAll("^-+|-+$", ""); // 先頭末尾のハイフンを除去
    if (slug.isEmpty()) {
      slug = FALLBACK;
    }
    return truncate(slug, MAX_LENGTH);
  }

  /**
   * 基底 slug に一意化サフィックス {@code -n} を付与する({@value #MAX_LENGTH} 文字に収まるよう基底側を切り詰める)。
   *
   * @param baseCode {@link #toBaseCode(String)} の戻り値
   * @param suffix 2 以上の連番
   * @return {@code <base>-<suffix>} 形式で {@value #MAX_LENGTH} 文字以内のコード
   */
  public String withSuffix(String baseCode, int suffix) {
    String tail = "-" + suffix;
    String head = truncate(baseCode, MAX_LENGTH - tail.length());
    head = head.replaceAll("-+$", "");
    if (head.isEmpty()) {
      head = FALLBACK;
    }
    return head + tail;
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
