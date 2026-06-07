package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDateTime;
import lombok.Getter;

/** 関係者ドメインモデル(JPA 非依存 POJO)。 */
@Getter
public class TaskStakeholder {

  private final Long userId;
  private final String fullName;
  private final String email;
  private final Long addedById;
  private final String addedByFullName;
  private final LocalDateTime addedAt;

  public TaskStakeholder(
      Long userId,
      String fullName,
      String email,
      Long addedById,
      String addedByFullName,
      LocalDateTime addedAt) {
    this.userId = userId;
    this.fullName = fullName;
    this.email = email;
    this.addedById = addedById;
    this.addedByFullName = addedByFullName;
    this.addedAt = addedAt;
  }
}
