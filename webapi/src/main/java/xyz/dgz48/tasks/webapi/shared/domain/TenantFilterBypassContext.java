package xyz.dgz48.tasks.webapi.shared.domain;

/**
 * {@link xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService} による正当な SaaS Admin bypass
 * 実行中であることを ThreadLocal で示す。
 *
 * <p>{@code activate()} / {@code deactivate()} は {@link
 * xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService} からのみ呼び出す。{@code isActive()} は
 * {@code CrossTenantViolationInspector} が false positive を除外するために参照する。
 */
public final class TenantFilterBypassContext {

  private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

  private TenantFilterBypassContext() {}

  /** 現在のスレッドが正当な SaaS Admin bypass 実行中かどうかを返す。 */
  public static boolean isActive() {
    return Boolean.TRUE.equals(ACTIVE.get());
  }

  /** bypass 開始を記録する。{@link xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService} 専用。 */
  public static void activate() {
    ACTIVE.set(Boolean.TRUE);
  }

  /** bypass 終了を記録する。{@link xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService} 専用。 */
  public static void deactivate() {
    ACTIVE.remove();
  }
}
