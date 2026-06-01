package xyz.dgz48.tasks.webapi.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 10, 0, 0);

  private Task buildTask(TaskStatus status) {
    return new Task(
        1L,
        10L,
        "title",
        null,
        status,
        Priority.MEDIUM,
        Visibility.TENANT,
        100L,
        null,
        LocalDate.of(2026, 12, 31),
        null,
        null,
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 1, 1, 0, 0));
  }

  @Test
  void changeVisibility_updatesVisibility() {
    Task task = buildTask(TaskStatus.NOT_STARTED);

    task.changeVisibility(Visibility.PRIVATE);

    assertThat(task.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }

  @Test
  void changeVisibility_canBeCalledMultipleTimes() {
    Task task = buildTask(TaskStatus.NOT_STARTED);

    task.changeVisibility(Visibility.STAKEHOLDERS);
    task.changeVisibility(Visibility.PRIVATE);

    assertThat(task.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }

  @Test
  void changeStatus_toDone_setsCompletedAt() {
    Task task = buildTask(TaskStatus.IN_PROGRESS);

    task.changeStatus(TaskStatus.DONE, NOW);

    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(NOW);
  }

  @Test
  void changeStatus_fromDoneToNotDone_clearsCompletedAt() {
    Task task =
        new Task(
            1L,
            10L,
            "title",
            null,
            TaskStatus.DONE,
            Priority.MEDIUM,
            Visibility.TENANT,
            100L,
            null,
            LocalDate.of(2026, 12, 31),
            NOW,
            null,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0));

    task.changeStatus(TaskStatus.IN_PROGRESS, NOW.plusHours(1));

    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(task.getCompletedAt()).isNull();
  }

  @Test
  void changeStatus_doneToDone_preservesCompletedAt() {
    Task task =
        new Task(
            1L,
            10L,
            "title",
            null,
            TaskStatus.DONE,
            Priority.MEDIUM,
            Visibility.TENANT,
            100L,
            null,
            LocalDate.of(2026, 12, 31),
            NOW,
            null,
            LocalDateTime.of(2026, 1, 1, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0));

    task.changeStatus(TaskStatus.DONE, NOW.plusHours(2));

    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(NOW);
  }

  @Test
  void changeStatus_notStartedToNotStarted_completedAtRemainsNull() {
    Task task = buildTask(TaskStatus.NOT_STARTED);

    task.changeStatus(TaskStatus.ON_HOLD, NOW);

    assertThat(task.getStatus()).isEqualTo(TaskStatus.ON_HOLD);
    assertThat(task.getCompletedAt()).isNull();
  }
}
