package xyz.dgz48.tasks.webapi.dashboard.adapter.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTask;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;

/**
 * OpenAPI {@code Task} スキーマに対応するダッシュボードのセクション項目レスポンス。
 *
 * <p>{@code editable} / {@code deletable} は ADR-0005 の 3 役割評価で <b>所有者のみ true</b>(一般編集・論理削除は所有者権限)。
 * {@code status} / {@code priority} / {@code visibility} はリードモデルが保持する文字列をそのまま用いる(JSON 表現は enum
 * と同一)。
 */
public record DashboardTaskItemResponse(
    Long id,
    Long version,
    String title,
    @Nullable String description,
    String status,
    String priority,
    String visibility,
    DashboardUserSummaryResponse owner,
    @Nullable DashboardUserSummaryResponse assignee,
    LocalDate dueDate,
    @Nullable OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean editable,
    boolean deletable) {

  public static DashboardTaskItemResponse from(
      DashboardTask task, Long currentUserId, Map<Long, UserJpaEntity> userMap) {
    DashboardUserSummaryResponse owner = toUserSummary(task.ownerId(), userMap);

    @Nullable DashboardUserSummaryResponse assignee = null;
    if (task.assigneeId() != null) {
      assignee = toUserSummary(task.assigneeId(), userMap);
    }

    boolean isOwner = task.ownerId().equals(currentUserId);

    return new DashboardTaskItemResponse(
        task.id(),
        task.version(),
        task.title(),
        task.description(),
        task.status(),
        task.priority(),
        task.visibility(),
        owner,
        assignee,
        task.dueDate(),
        task.completedAt() != null
            ? task.completedAt().atZone(AppZones.JST).toOffsetDateTime()
            : null,
        task.createdAt().atZone(AppZones.JST).toOffsetDateTime(),
        task.updatedAt().atZone(AppZones.JST).toOffsetDateTime(),
        isOwner,
        isOwner);
  }

  private static DashboardUserSummaryResponse toUserSummary(
      Long userId, Map<Long, UserJpaEntity> userMap) {
    UserJpaEntity entity = userMap.get(userId);
    return entity != null
        ? new DashboardUserSummaryResponse(entity.getId(), entity.getFullName())
        : new DashboardUserSummaryResponse(userId, "");
  }
}
