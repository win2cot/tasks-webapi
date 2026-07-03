package xyz.dgz48.tasks.webapi;

import net.ttddyy.dsproxy.QueryCountHolder;

/**
 * datasource-proxy の {@code QueryCountHolder}(ThreadLocal)を薄くラップする N+1 回帰テスト用ヘルパ(ADR-0039)。
 *
 * <p>計測区間の直前に {@link #reset()} し、区間後に {@link #totalQueries()} / {@link #selectQueries()} で本数を
 * assert する。本数がフィクスチャ件数 N に依存せず定数であることを固定することで、将来の実装変更による N+1 退行を CI で検知する。
 */
public final class QueryCountProbe {

  private QueryCountProbe() {}

  /** 計測区間開始前にスレッドローカルのクエリ計数をリセットする。 */
  public static void reset() {
    QueryCountHolder.clear();
  }

  /** リセット以降に実行された総クエリ本数(SELECT / INSERT / UPDATE / DELETE / OTHER の合算)。 */
  public static long totalQueries() {
    return QueryCountHolder.getGrandTotal().getTotal();
  }

  /** リセット以降に実行された SELECT 本数。 */
  public static long selectQueries() {
    return QueryCountHolder.getGrandTotal().getSelect();
  }
}
