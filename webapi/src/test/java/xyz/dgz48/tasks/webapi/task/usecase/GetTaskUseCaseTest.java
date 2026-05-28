package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaEntity;
import xyz.dgz48.tasks.webapi.task.adapter.persistence.TaskJpaRepository;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

class GetTaskUseCaseTest {

  private TaskJpaRepository taskJpaRepository;
  private GetTaskUseCase useCase;

  @BeforeEach
  void setUp() {
    taskJpaRepository = mock(TaskJpaRepository.class);
    useCase = new GetTaskUseCase(taskJpaRepository);
  }

  @Test
  void getTask_returnsTask_whenFound() {
    var now = LocalDateTime.now();
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
    TaskJpaEntity entity = mock(TaskJpaEntity.class);
    when(taskJpaRepository.findByTenantIdAndId(1L, 1L)).thenReturn(Optional.of(entity));
    when(entity.toDomain()).thenReturn(expectedTask);

    Task result = useCase.getTask(1L, 1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getTitle()).isEqualTo("テストタスク");
    assertThat(result.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
  }

  @Test
  void getTask_throwsTaskNotFoundException_whenNotFound() {
    when(taskJpaRepository.findByTenantIdAndId(1L, 99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.getTask(1L, 99L)).isInstanceOf(TaskNotFoundException.class);
  }
}
