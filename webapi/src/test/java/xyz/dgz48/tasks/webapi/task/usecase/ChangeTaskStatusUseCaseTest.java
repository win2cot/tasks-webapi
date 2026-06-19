package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class ChangeTaskStatusUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long ASSIGNEE_ID = 20L;
  private static final Long OTHER_USER_ID = 30L;

  private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T01:00:00Z");
  private static final LocalDateTime FIXED_LDT =
      LocalDateTime.ofInstant(FIXED_INSTANT, AppZones.JST);

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @Mock Clock clock;
  @InjectMocks ChangeTaskStatusUseCase useCase;

  private static final Long VERSION = 0L;

  private Task buildTask(TaskStatus status) {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Test task",
        null,
        status,
        Priority.MEDIUM,
        Visibility.TENANT,
        OWNER_ID,
        ASSIGNEE_ID,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 0, 0),
        LocalDateTime.of(2026, 5, 31, 0, 0),
        VERSION);
  }

  private void setupClock() {
    when(clock.getZone()).thenReturn(AppZones.JST);
    when(clock.instant()).thenReturn(FIXED_INSTANT);
  }

  @Test
  void execute_throwsTaskNotFoundException_whenTaskNotFound() {
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, TaskStatus.DONE))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask(TaskStatus.IN_PROGRESS);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, TaskStatus.DONE))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenCannotChangeStatus() {
    Task task = buildTask(TaskStatus.IN_PROGRESS);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canChangeStatusBy(task, OTHER_USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, TaskStatus.DONE))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_setsCompletedAt_whenChangedToDone() {
    Task task = buildTask(TaskStatus.IN_PROGRESS);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canChangeStatusBy(task, OWNER_ID)).thenReturn(true);
    setupClock();
    Task expected =
        new Task(
            TASK_ID,
            TENANT_ID,
            "Test task",
            null,
            TaskStatus.DONE,
            Priority.MEDIUM,
            Visibility.TENANT,
            OWNER_ID,
            ASSIGNEE_ID,
            LocalDate.of(2026, 6, 1),
            FIXED_LDT,
            null,
            LocalDateTime.of(2026, 5, 31, 0, 0),
            FIXED_LDT,
            VERSION);
    when(taskRepository.saveStatus(eq(TASK_ID), eq(TaskStatus.DONE), eq(FIXED_LDT), any()))
        .thenReturn(expected);

    Task result = useCase.execute(TASK_ID, OWNER_ID, TaskStatus.DONE);

    assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(result.getCompletedAt()).isEqualTo(FIXED_LDT);
  }

  @Test
  void execute_clearsCompletedAt_whenReopened() {
    Task task =
        new Task(
            TASK_ID,
            TENANT_ID,
            "Test task",
            null,
            TaskStatus.DONE,
            Priority.MEDIUM,
            Visibility.TENANT,
            OWNER_ID,
            ASSIGNEE_ID,
            LocalDate.of(2026, 6, 1),
            FIXED_LDT,
            null,
            LocalDateTime.of(2026, 5, 31, 0, 0),
            LocalDateTime.of(2026, 5, 31, 0, 0),
            VERSION);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(taskAuthorizationDomainService.canBeViewedBy(task, ASSIGNEE_ID, List.of()))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canChangeStatusBy(task, ASSIGNEE_ID)).thenReturn(true);
    setupClock();
    Task expected =
        new Task(
            TASK_ID,
            TENANT_ID,
            "Test task",
            null,
            TaskStatus.IN_PROGRESS,
            Priority.MEDIUM,
            Visibility.TENANT,
            OWNER_ID,
            ASSIGNEE_ID,
            LocalDate.of(2026, 6, 1),
            null,
            null,
            LocalDateTime.of(2026, 5, 31, 0, 0),
            FIXED_LDT,
            VERSION);
    when(taskRepository.saveStatus(eq(TASK_ID), eq(TaskStatus.IN_PROGRESS), isNull(), any()))
        .thenReturn(expected);

    Task result = useCase.execute(TASK_ID, ASSIGNEE_ID, TaskStatus.IN_PROGRESS);

    assertThat(result.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(result.getCompletedAt()).isNull();
  }
}
