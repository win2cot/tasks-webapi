package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.LocalDateTime;
import java.util.List;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;

/** task_stakeholders の永続化ポート。 */
public interface StakeholderRepository {

  /** 指定タスクの関係者一覧をユーザー情報付きで返す。 */
  List<TaskStakeholder> findByTaskId(Long taskId, Long tenantId);

  /** 指定タスクの関係者ユーザー ID リストを返す(visibility 判定用)。 */
  List<Long> findUserIdsByTaskId(Long taskId, Long tenantId);

  /** 関係者を追加し、ユーザー情報付きの関係者を返す。 */
  TaskStakeholder add(
      Long taskId, Long tenantId, Long userId, Long addedByUserId, LocalDateTime addedAt);

  /** 指定ユーザーを関係者から削除する。 */
  void removeByTaskIdAndUserId(Long taskId, Long userId);

  /** 指定ユーザーが関係者として登録済みか返す。 */
  boolean existsByTaskIdAndUserId(Long taskId, Long userId);
}
