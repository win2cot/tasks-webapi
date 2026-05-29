package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

class GetTaskUseCaseTest {

  private TaskRepository taskRepository;
  private GetTaskUseCase useCase;

  @BeforeEach
  void setUp() {
    taskRepository = mock(TaskRepository.class);
    useCase = new GetTaskUseCase(taskRepository);
  }

  @Test
  void getTask_returnsTask_whenFound() {
    var now = OffsetDateTime.now();
    Task expectedTask =
        new Task(
            1L,
            1L,
            "テストタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            Visibility.TENANT,
            1L,
            null,
            LocalDate.of(2026, 12, 31),
            null,
            null,
            now,
            now);
    when(taskRepository.findByTenantIdAndId(1L, 1L)).thenReturn(Optional.of(expectedTask));

    Task result = useCase.getTask(1L, 1L, 1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getTitle()).isEqualTo("テストタスク");
    assertThat(result.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
  }

  @Test
  void getTask_throwsTaskNotFoundException_whenNotFound() {
    when(taskRepository.findByTenantIdAndId(1L, 99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getTask(1L, 99L, 1L)).isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void getTask_throwsTaskNotViewableException_whenPrivateAndNotOwner() {
    var now = OffsetDateTime.now();
    Task privateTask =
        new Task(
            1L,
            1L,
            "プライベートタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            Visibility.PRIVATE,
            99L, // owner is user 99
            null,
            LocalDate.of(2026, 12, 31),
            null,
            null,
            now,
            now);
    when(taskRepository.findByTenantIdAndId(1L, 1L)).thenReturn(Optional.of(privateTask));

    assertThatThrownBy(() -> useCase.getTask(1L, 1L, 1L)) // requesting user is 1, not owner
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void getTask_returnsTask_whenPrivateAndAssignee() {
    var now = OffsetDateTime.now();
    Task privateTask =
        new Task(
            2L,
            1L,
            "担当者プライベートタスク",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            Visibility.PRIVATE,
            99L, // owner is user 99
            1L, // assignee is user 1 (requesting user)
            LocalDate.of(2026, 12, 31),
            null,
            null,
            now,
            now);
    when(taskRepository.findByTenantIdAndId(1L, 2L)).thenReturn(Optional.of(privateTask));

    Task result = useCase.getTask(1L, 2L, 1L); // requesting user is 1 (assignee)

    assertThat(result.getId()).isEqualTo(2L);
  }
}
