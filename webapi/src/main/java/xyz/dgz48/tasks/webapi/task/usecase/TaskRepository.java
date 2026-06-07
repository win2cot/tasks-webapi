package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** Task の永続化ポート。adapter.persistence の {@code TaskJpaRepositoryAdapter} で実装される。 */
public interface TaskRepository {

  /** Hibernate Filter が有効な場合、現在テナントに属さないタスクは自動的に空を返す。 */
  Optional<Task> findById(Long id);

  /** タスクを保存して最新の状態を返す。 */
  Task save(Task task);

  /**
   * 認可フィルタ済みタスク一覧を返す。
   *
   * <p>Hibernate Filter による tenant_id 自動絞込 + deleted_at IS NULL + visibility 3 役割評価(ADR-0005)を
   * 適用する。priority ソートは HIGH=3 / MEDIUM=2 / LOW=1 に内部変換。desc ソートで HIGH が先頭(重要度高い順)。
   */
  Page<Task> findVisibleTasks(
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility,
      Pageable pageable);

  /** 認可フィルタを適用した期限切れ未完了タスク件数を返す。 */
  long countOverdueTasks(Long userId, LocalDate today);
}
