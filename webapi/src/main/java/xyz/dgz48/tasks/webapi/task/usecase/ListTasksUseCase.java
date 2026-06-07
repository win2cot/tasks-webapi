package xyz.dgz48.tasks.webapi.task.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** タスク一覧取得ユースケース。認可フィルタ済みの一覧を返す(ADR-0005 / ADR-0010)。 */
@Service
@RequiredArgsConstructor
public class ListTasksUseCase {

  private final TaskRepository taskRepository;
  private final Clock clock;

  /**
   * 認可フィルタ済みタスク一覧を取得する。
   *
   * @param userId 現在のユーザー ID(visibility 認可評価に使用)
   * @param statuses 絞込ステータス(null = 全ステータス)
   * @param ownerId 絞込所有者 ID(null = 全所有者)
   * @param assigneeId 絞込担当者 ID(null = 全担当者)
   * @param visibility 絞込公開範囲(null = 全公開範囲)
   * @param pageable ページング / ソート指定
   * @return ページ結果と期限切れ未完了タスク件数
   */
  @Transactional(readOnly = true)
  public Result execute(
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility,
      Pageable pageable) {

    Page<Task> taskPage =
        taskRepository.findVisibleTasks(
            userId, statuses, ownerId, assigneeId, visibility, pageable);

    long overdueCount = taskRepository.countOverdueTasks(userId, LocalDate.now(clock));

    return new Result(taskPage, (int) overdueCount);
  }

  public record Result(Page<Task> taskPage, int overdueCount) {}
}
