package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import xyz.dgz48.tasks.webapi.shared.adapter.persistence.TenantFilteredEntity;

/**
 * {@code task_stakeholders} テーブルに対する dashboard feature 所有の読み取り専用ビュー。
 *
 * <p>summary 集計の visibility=STAKEHOLDERS 分岐(「関係者として参照可能」)を EXISTS サブクエリで判定するために用いる。 {@link
 * TenantFilteredEntity} 継承により Hibernate Filter がサブクエリにもテナント条件を自動付与する。
 */
@Entity
@Immutable
@Table(name = "task_stakeholders")
@IdClass(DashboardStakeholderViewId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class DashboardStakeholderView extends TenantFilteredEntity {

  @Id
  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;
}
