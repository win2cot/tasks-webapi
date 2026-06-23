package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** Task の永続化ポート。adapter.persistence の {@code TaskJpaRepositoryAdapter} で実装される。 */
public interface TaskRepository {

  /** Hibernate Filter が有効な場合、現在テナントに属さないタスクは自動的に空を返す。 */
  Optional<Task> findById(Long id);

  /** 新規タスクを永続化して返す。status は常に NOT_STARTED で固定される。 */
  Task create(
      Long tenantId,
      Long ownerId,
      String title,
      @Nullable String description,
      Priority priority,
      Visibility visibility,
      @Nullable Long assigneeId,
      LocalDate dueDate);

  /** タスクを保存して最新の状態を返す。 */
  Task save(Task task);

  /**
   * 認可フィルタ済みタスク一覧を返す。
   *
   * <p>Hibernate Filter による tenant_id 自動絞込 + deleted_at IS NULL + visibility 3 役割評価(ADR-0005)を
   * 適用する。priority ソートは HIGH=3 / MEDIUM=2 / LOW=1 に内部変換。desc ソートで HIGH が先頭(重要度高い順)。
   *
   * <p>表示対象日フィルタ(#665 / #666 共通):
   *
   * <ul>
   *   <li>{@code includeOverdue == true}: {@code due_date = targetDate} または 「{@code due_date <
   *       targetDate} かつ未完了(status != DONE)」のタスク(当日 + 期限切れ)
   *   <li>{@code includeOverdue == false}: {@code due_date = targetDate} のタスクのみ
   * </ul>
   *
   * @param keyword タイトル・説明の部分一致検索キーワード(null / 空白のみ = 検索しない、#669)
   * @param targetDate 表示対象日(基準日)。{@code null} 不可(usecase で当日に解決済み)。
   * @param includeOverdue 期限切れ未完了タスクを含めるか
   */
  Page<Task> findVisibleTasks(
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility,
      @Nullable String keyword,
      LocalDate targetDate,
      boolean includeOverdue,
      Pageable pageable);

  /** 認可フィルタを適用した、基準日時点の期限切れ未完了タスク件数({@code due_date < targetDate})を返す。 */
  long countOverdueTasks(Long userId, LocalDate targetDate);

  /** タスクを論理削除する(deleted_at セット)。楽観ロック競合時は PreconditionFailedException を投げる。 */
  void softDelete(Task task, LocalDateTime deletedAt);

  /**
   * ステータスと completedAt のみを更新する(last-write-wins、ADR-0012 amendment)。
   *
   * <p>楽観ロック({@code @Version})をバイパスするため同時変更が競合しない。{@code now} は {@code updated_at} に反映される。
   */
  Task saveStatus(
      Long taskId, TaskStatus newStatus, @Nullable LocalDateTime completedAt, LocalDateTime now);
}
