package xyz.dgz48.tasks.webapi.shared.adapter.persistence;

import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * {@code tenant_id BIGINT NOT NULL} 列を持つ全業務 JPA エンティティの共通 MappedSuperclass。
 *
 * <p>{@code @FilterDef} を一箇所に集約し、Hibernate Filter "tenantFilter" を全サブクラスに自動適用する。 {@code UPDATE} /
 * {@code DELETE} / native query は {@code @Modifying} を持つメソッドで引き続き明示絞り込みが必要(ADR-0010 §3)。
 *
 * <p><strong>継承基準</strong>: {@code tenant_id BIGINT NOT NULL}
 * 列を持ち、テナント境界で分離すべき業務エンティティのみが本クラスを継承する。 以下のテーブルは除外対象のため継承しない:
 *
 * <ul>
 *   <li>{@code tenants} — マスタテーブル、{@code tenant_id} 列なし
 *   <li>{@code users} — プラットフォーム横断ユーザー、{@code tenant_id} 列なし
 *   <li>{@code user_tenants} — {@code TenantContext} 確立前にクロステナント参照が必要
 *   <li>{@code audit_logs} — {@code tenant_id} が nullable、テナント範囲を超えた参照が必要
 *   <li>{@code shedlock} — {@code tenant_id} 列なし、フレームワーク管理
 *   <li>{@code app_admin_users} — SaaS Admin 管理、テナント境界外
 * </ul>
 *
 * <p>除外理由の詳細は設計規約 §3.3.1 / ADR-0010 §6.1 を参照。{@code HibernateFilterEntityAuditTest} が CI
 * でこの基準の遵守を静的検証する。
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantFilteredEntity {}
