package xyz.dgz48.tasks.webapi.task.usecase;

import java.util.Optional;
import xyz.dgz48.tasks.webapi.task.domain.Task;

/** タスク永続化のポートインタフェース。adapter.persistence 層が実装する。 */
public interface TaskRepository {

  Optional<Task> findByTenantIdAndId(Long tenantId, Long id);
}
