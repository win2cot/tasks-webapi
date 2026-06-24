package xyz.dgz48.tasks.webapi.dashboard.adapter.persistence;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** {@link DashboardStakeholderView} の複合主キークラス(task_id, user_id)。 */
@NoArgsConstructor
@EqualsAndHashCode
@SuppressWarnings("NullAway.Init") // JPA composite ID class requires no-args constructor
class DashboardStakeholderViewId implements Serializable {
  @Nullable private Long taskId;
  @Nullable private Long userId;

  DashboardStakeholderViewId(Long taskId, Long userId) {
    this.taskId = taskId;
    this.userId = userId;
  }
}
