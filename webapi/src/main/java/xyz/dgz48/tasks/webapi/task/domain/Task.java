package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Task ドメインモデル(JPA 非依存 POJO)。 設計規約 §1.1 のクリーンアーキ 4 層に従い domain 層に配置。 Persistence 層では {@code
 * TaskJpaEntity} と相互変換する。
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include private final Long id;
  private final Long tenantId;
  private String title;
  @Nullable private String description;
  private TaskStatus status;
  private Priority priority;
  private Visibility visibility;
  private final Long ownerId;
  @Nullable private Long assigneeId;
  private LocalDate dueDate;
  @Nullable private LocalDateTime completedAt;
  @Nullable private final LocalDateTime deletedAt;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
  private final Long version;

  public Task(
      Long id,
      Long tenantId,
      String title,
      @Nullable String description,
      TaskStatus status,
      Priority priority,
      Visibility visibility,
      Long ownerId,
      @Nullable Long assigneeId,
      LocalDate dueDate,
      @Nullable LocalDateTime completedAt,
      @Nullable LocalDateTime deletedAt,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      Long version) {
    this.id = id;
    this.tenantId = tenantId;
    this.title = title;
    this.description = description;
    this.status = status;
    this.priority = priority;
    this.visibility = visibility;
    this.ownerId = ownerId;
    this.assigneeId = assigneeId;
    this.dueDate = dueDate;
    this.completedAt = completedAt;
    this.deletedAt = deletedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public void changeVisibility(Visibility newVisibility) {
    this.visibility = newVisibility;
  }

  /**
   * ステータス遷移と completed_at の制御(UseCase 明示セット方式 — Issue #330 議論結論)。
   *
   * <ul>
   *   <li>非完了 → 完了: completed_at = now
   *   <li>完了 → 非完了(再オープン): completed_at = null
   *   <li>完了 → 完了(冪等): 既存 completed_at を維持
   * </ul>
   */
  public void changeStatus(TaskStatus newStatus, LocalDateTime now) {
    if (newStatus == TaskStatus.DONE && this.status != TaskStatus.DONE) {
      this.completedAt = now;
    } else if (newStatus != TaskStatus.DONE) {
      this.completedAt = null;
    }
    this.status = newStatus;
  }

  /** PATCH コマンドを適用する。JsonNullable.undefined() のフィールドは変更しない(ADR-0014)。 */
  public void applyPatch(TaskPatchCommand cmd) {
    if (cmd.title().isPresent()) {
      String v = cmd.title().get();
      if (v != null) this.title = v;
    }
    if (cmd.description().isPresent()) {
      this.description = cmd.description().get();
    }
    if (cmd.priority().isPresent()) {
      Priority v = cmd.priority().get();
      if (v != null) this.priority = v;
    }
    if (cmd.assigneeId().isPresent()) {
      this.assigneeId = cmd.assigneeId().get();
    }
    if (cmd.dueDate().isPresent()) {
      LocalDate v = cmd.dueDate().get();
      if (v != null) this.dueDate = v;
    }
  }
}
