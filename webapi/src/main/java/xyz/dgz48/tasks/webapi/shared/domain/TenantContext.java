package xyz.dgz48.tasks.webapi.shared.domain;

import org.jspecify.annotations.Nullable;

/** リクエストスコープの現在テナント ID を ThreadLocal で保持する。 */
public final class TenantContext {

  private static final ThreadLocal<@Nullable Long> HOLDER = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(Long tenantId) {
    HOLDER.set(tenantId);
  }

  public static @Nullable Long get() {
    return HOLDER.get();
  }

  public static void clear() {
    HOLDER.remove();
  }
}
