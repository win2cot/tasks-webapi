package xyz.dgz48.tasks.webapi.task.usecase;

import io.micrometer.observation.annotation.Observed;
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
   * 認可フィルタ済みタスク一覧を取得する(表示対象日フィルタ済み、#665 / #666 共通)。
   *
   * @param userId 現在のユーザー ID(visibility 認可評価に使用)
   * @param statuses 絞込ステータス(null = 全ステータス)
   * @param ownerId 絞込所有者 ID(null = 全所有者)
   * @param assigneeId 絞込担当者 ID(null = 全担当者)
   * @param visibility 絞込公開範囲(null = 全公開範囲)
   * @param keyword タイトル・説明部分一致検索キーワード(null / 空白のみ = 検索しない、#669)
   * @param targetDate 表示対象日(選択日)。null のときは当日(JST、ADR-0009)に解決する
   * @param includeOverdue 期限切れ未完了タスクを含めるか(期限切れは選択日に関わらず当日基準で常時含める、#667)
   * @param pageable ページング / ソート指定
   * @return ページ結果と当日時点の期限切れ未完了タスク件数
   */
  @Observed(name = "task.list")
  @Transactional(readOnly = true)
  public Result execute(
      Long userId,
      @Nullable List<TaskStatus> statuses,
      @Nullable Long ownerId,
      @Nullable Long assigneeId,
      @Nullable Visibility visibility,
      @Nullable String keyword,
      @Nullable LocalDate targetDate,
      boolean includeOverdue,
      Pageable pageable) {

    LocalDate today = LocalDate.now(clock);
    LocalDate effectiveDate = targetDate != null ? targetDate : today;

    Page<Task> taskPage =
        taskRepository.findVisibleTasks(
            userId,
            statuses,
            ownerId,
            assigneeId,
            visibility,
            keyword,
            effectiveDate,
            today,
            includeOverdue,
            pageable);

    long overdueCount = taskRepository.countOverdueTasks(userId, today);

    return new Result(taskPage, Math.toIntExact(overdueCount));
  }

  public record Result(Page<Task> taskPage, int overdueCount) {}
}
