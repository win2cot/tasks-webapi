package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class GetTaskUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long ASSIGNEE_ID = 20L;
  private static final Long OTHER_USER_ID = 30L;

  @Mock TaskRepository taskRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @InjectMocks GetTaskUseCase useCase;

  private Task buildTask(Visibility visibility) {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Test task",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        visibility,
        OWNER_ID,
        ASSIGNEE_ID,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 0, 0),
        LocalDateTime.of(2026, 5, 31, 0, 0));
  }

  @Test
  void execute_returnsTask_whenViewable() {
    Task task = buildTask(Visibility.TENANT);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);

    assertThat(useCase.execute(TASK_ID, OWNER_ID)).isEqualTo(task);
  }

  @Test
  void execute_throwsTaskNotFoundException_whenNotFound() {
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask(Visibility.PRIVATE);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_returnsTask_whenPrivateAndUserIsAssignee() {
    Task task = buildTask(Visibility.PRIVATE);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, ASSIGNEE_ID, List.of()))
        .thenReturn(true);

    assertThat(useCase.execute(TASK_ID, ASSIGNEE_ID)).isEqualTo(task);
  }
}
