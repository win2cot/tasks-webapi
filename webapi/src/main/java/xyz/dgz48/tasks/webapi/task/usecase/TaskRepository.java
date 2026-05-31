package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.Optional;
import xyz.dgz48.tasks.webapi.task.domain.Task;

/** Task の永続化ポート。adapter.persistence の {@code TaskJpaRepositoryAdapter} で実装される。 */
public interface TaskRepository {

  /** Hibernate Filter が有効な場合、現在テナントに属さないタスクは自動的に空を返す。 */
  Optional<Task> findById(Long id);
}
