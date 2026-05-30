package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.Optional;
import xyz.dgz48.tasks.webapi.task.domain.Task;

/** Task の永続化ポート。adapter.persistence の {@code TaskJpaRepositoryAdapter} で実装される。 */
public interface TaskRepository {

  Optional<Task> findByIdAndTenantId(Long id, Long tenantId);
}
