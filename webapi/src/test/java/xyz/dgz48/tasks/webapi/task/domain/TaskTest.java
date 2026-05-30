package xyz.dgz48.tasks.webapi.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskTest {

  private Task buildTask(Visibility visibility) {
    return new Task(
        1L,
        10L,
        "title",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        visibility,
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
    Task task = buildTask(Visibility.TENANT);

    task.changeVisibility(Visibility.PRIVATE);

    assertThat(task.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }

  @Test
  void changeVisibility_canBeCalledMultipleTimes() {
    Task task = buildTask(Visibility.TENANT);

    task.changeVisibility(Visibility.STAKEHOLDERS);
    task.changeVisibility(Visibility.PRIVATE);

    assertThat(task.getVisibility()).isEqualTo(Visibility.PRIVATE);
  }
}
