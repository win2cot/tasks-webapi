package xyz.dgz48.tasks.webapi.shared.adapter.persistence;

import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * tenant_id を持つ全業務エンティティの共通 MappedSuperclass。
 *
 * <p>@FilterDef を一箇所に集約し、Hibernate Filter "tenantFilter" を全サブクラスに自動適用する。 UPDATE/DELETE / native
 * query は {@code @Modifying} を持つメソッドで引き続き明示絞り込みが必要(ADR-0010 §3)。
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantFilteredEntity {}
