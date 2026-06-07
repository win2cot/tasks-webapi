package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.time.LocalDateTime;

/** native query の結果を受け取る Spring Data Projection。 */
interface StakeholderProjection {
  Long getUserId();

  String getFullName();

  String getEmail();

  Long getAddedBy();

  String getAddedByFullName();

  LocalDateTime getAddedAt();
}
