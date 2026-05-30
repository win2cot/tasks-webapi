package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Task ドメインモデル(JPA 非依存 POJO)。 設計規約 §1.1 のクリーンアーキ 4 層に従い domain 層に配置。 Persistence 層では {@code
 * TaskJpaEntity} と相互変換する。
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include private final Long id;
  private final Long tenantId;
  private final String title;
  @Nullable private final String description;
  private final TaskStatus status;
  private final Priority priority;
  private final Visibility visibility;
  private final Long ownerId;
  @Nullable private final Long assigneeId;
  private final LocalDate dueDate;
  @Nullable private final LocalDateTime completedAt;
  @Nullable private final LocalDateTime deletedAt;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
}
