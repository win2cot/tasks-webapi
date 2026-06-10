package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

/**
 * SaaS Admin のテナント操作ポート(Hibernate Filter 非適用、全テナント横断)。
 *
 * <p>呼び出し元は {@link xyz.dgz48.tasks.webapi.shared.usecase.TenantFilterBypassService} を通じて SaaS Admin
 * 権限を検証済みであること(ADR-0013 §3)。
 */
public interface AdminTenantRepository {

  /** テナントを ID で検索する(フィルタなし)。存在しない場合は空。 */
  Optional<Tenant> findById(Long id);

  /**
   * テナント一覧を返す(フィルタなし)。
   *
   * @param status 状態フィルタ(null = 全件)
   * @param keyword テナント名部分一致(null = 全件)
   * @param pageable ページング条件
   */
  Page<Tenant> findAll(@Nullable TenantStatus status, @Nullable String keyword, Pageable pageable);

  /** テナント名を更新して最新の状態を返す。 */
  Tenant updateName(Long id, String name);

  /** テナント状態を更新して最新の状態を返す。 */
  Tenant updateStatus(Long id, TenantStatus status);
}
