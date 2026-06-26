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

  /**
   * テナントを新規作成する({@code plan} は本フェーズ固定の {@code FREE}、{@code status} は {@code ACTIVE})。
   *
   * <p>セルフサインアップ(A-05)経路で呼ばれる。作成直後はメンバー未登録のため、返却する {@link Tenant} の {@code userCount} / {@code
   * taskCount} は 0(COUNT クエリは発行しない — {@code X-Tenant-Id} 未指定経路で {@code tasks} を読むと越境検知に当たるため)。
   *
   * @param code 一意な slug コード(呼び出し側が一意性を担保済み)
   * @param name テナント表示名
   * @return 作成された Tenant(userCount/taskCount = 0)
   */
  Tenant createTenant(String code, String name);

  /** 指定 code のテナントが既に存在するか(slug 一意化リトライ用)。 */
  boolean existsByCode(String code);

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
