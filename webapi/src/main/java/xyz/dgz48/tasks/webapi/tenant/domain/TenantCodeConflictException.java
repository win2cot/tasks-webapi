package xyz.dgz48.tasks.webapi.tenant.domain;

/**
 * テナント表示名から生成した {@code code} の一意化が、サフィックスリトライ上限を超えても解消できなかった場合の例外(409 /
 * E_CONFLICT)。同名テナントが多数存在するレアケース。
 */
public class TenantCodeConflictException extends RuntimeException {

  public TenantCodeConflictException(String baseCode) {
    super("テナントコード '" + baseCode + "' の一意化に失敗しました(同名テナントが多すぎます)");
  }
}
