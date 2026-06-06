package xyz.dgz48.tasks.webapi.shared.usecase;

import jakarta.persistence.EntityManager;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.domain.TenantFilterBypassContext;
import xyz.dgz48.tasks.webapi.shared.exception.SaasAdminRequiredException;

/**
 * SaaS Admin のみが使用できる Hibernate Filter "tenantFilter" 一時無効化サービス。
 *
 * <p>全テナント横断クエリが必要なプラットフォーム監視・運用画面等で使用する。フィルタ解除のスコープを渡された {@link Supplier} の実行範囲に限定し、try-finally
 * で必ずフィルタ状態を復元する。非 SaaS Admin による呼び出しは {@link SaasAdminRequiredException} を送出する。
 *
 * <p>直接 {@code session.disableFilter("tenantFilter")} を呼び出すことは禁止とし、本サービス経由でのみ bypass を許可する
 * (ADR-0010 §3 — レビュー対象として可視化)。
 */
@Service
public class TenantFilterBypassService {

  private static final String TENANT_FILTER = "tenantFilter";
  private static final String TENANT_ID_PARAM = "tenantId";

  private final EntityManager entityManager;

  TenantFilterBypassService(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * SaaS Admin 権限を検証したうえで Hibernate Filter を一時無効化し、全テナント横断クエリを実行する。
   *
   * <p>既存トランザクション内の Hibernate Session を使用するため、呼び出し元でトランザクションが開始済みであること。フィルタ状態は try-finally
   * で必ず復元される。
   *
   * @param action 全テナント横断で実行するアクション
   * @return アクションの戻り値
   * @throws SaasAdminRequiredException 呼び出し元が SaaS Admin({@code ROLE_APP_ADMIN})でない場合
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T runAsSaaSAdmin(Supplier<T> action) {
    checkSaasAdminRole();
    @Nullable Long previousTenantId = TenantContext.get();
    Session session = entityManager.unwrap(Session.class);
    TenantFilterBypassContext.activate();
    session.disableFilter(TENANT_FILTER);
    try {
      return action.get();
    } finally {
      TenantFilterBypassContext.deactivate();
      if (previousTenantId != null) {
        session.enableFilter(TENANT_FILTER).setParameter(TENANT_ID_PARAM, previousTenantId);
      }
    }
  }

  private void checkSaasAdminRole() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean isAdmin =
        auth != null
            && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_APP_ADMIN".equals(a.getAuthority()));
    if (!isAdmin) {
      throw new SaasAdminRequiredException("SaaS Admin ロールが必要です");
    }
  }
}
