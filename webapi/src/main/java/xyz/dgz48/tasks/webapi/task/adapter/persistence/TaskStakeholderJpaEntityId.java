package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@NoArgsConstructor
@EqualsAndHashCode
@SuppressWarnings("NullAway.Init") // JPA composite ID class requires no-args constructor
class TaskStakeholderJpaEntityId implements Serializable {
  @Nullable private Long taskId;
  @Nullable private Long userId;

  TaskStakeholderJpaEntityId(Long taskId, Long userId) {
    this.taskId = taskId;
    this.userId = userId;
  }
}
