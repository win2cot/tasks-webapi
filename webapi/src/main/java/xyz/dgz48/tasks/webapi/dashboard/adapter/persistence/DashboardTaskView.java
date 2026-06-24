package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.shared.adapter.persistence.TenantFilteredEntity;

/**
 * {@code tasks} テーブルに対する dashboard feature 所有の読み取り専用ビュー(CQRS リードモデル)。
 *
 * <p>{@link TenantFilteredEntity} を継承するため Hibernate Filter "tenantFilter"(ADR-0010)が JPQL に自動適用され、
 * テナント分離が保証される。{@link Immutable} で書き込み不可。task feature の {@code TaskJpaEntity} とは別エンティティだが
 * 同一テーブルを参照する(モジュール境界を越えた集計参照、設計規約 §3.3 native 許容カテゴリ(1) と同趣旨だが、リードモデル + JPQL
 * によりテナントフィルタを効かせる方式を採用)。
 *
 * <p>{@code status} / {@code priority} / {@code visibility} は ENUM 列を文字列として保持し、task feature の enum
 * 型へ依存しない。
 */
@Entity
@Immutable
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class DashboardTaskView extends TenantFilteredEntity {

  @Id private Long id;

  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false, length = 100)
  private String title;

  @Nullable
  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String priority;

  @Column(nullable = false)
  private String visibility;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Nullable
  @Column(name = "assignee_id")
  private Long assigneeId;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Nullable
  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Nullable
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
