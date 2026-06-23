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
   * <p>表示対象日フィルタ(#665 / #666 / #667 共通):
   *
   * <ul>
   *   <li>{@code includeOverdue == true}: {@code due_date = targetDate} または 「{@code due_date <
   *       today} かつ未完了(status != DONE)」のタスク(選択日 + 期限切れ)。期限切れ判定は選択日 {@code targetDate} ではなく
   *       <strong>当日 {@code today}</strong> を基準とし、選択日に関わらず常時含める(#667、基本設計書 §3.3.1)。
   *   <li>{@code includeOverdue == false}: {@code due_date = targetDate} のタスクのみ
   * </ul>
   *
   * @param keyword タイトル・説明の部分一致検索キーワード(null / 空白のみ = 検索しない、#669)
   * @param targetDate 表示対象日(選択日)。{@code null} 不可(usecase で当日に解決済み)。
   * @param today 期限切れ判定の基準となる当日(JST、ADR-0009)。{@code null} 不可。
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
      LocalDate today,
      boolean includeOverdue,
      Pageable pageable);

  /** 認可フィルタを適用した、当日時点の期限切れ未完了タスク件数({@code due_date < today} かつ status != DONE)を返す(#667)。 */
  long countOverdueTasks(Long userId, LocalDate today);

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
