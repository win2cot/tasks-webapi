package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
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
class DeleteTaskUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long OTHER_USER_ID = 30L;
  private static final Long VERSION = 0L;

  private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T01:00:00Z");

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @Mock AuditLogPort auditLogPort;
  @Mock Clock clock;
  @InjectMocks DeleteTaskUseCase useCase;

  private Task buildTask() {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Test task",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.TENANT,
        OWNER_ID,
        null,
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

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, VERSION))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, VERSION))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenNotOwner() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canBeDeletedBy(task, OTHER_USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, VERSION))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_throwsPreconditionFailedException_whenVersionMismatch() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeDeletedBy(task, OWNER_ID)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, VERSION + 1))
        .isInstanceOf(PreconditionFailedException.class);
  }

  @Test
  void execute_softDeletesTask_andRecordsAuditLog() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeDeletedBy(task, OWNER_ID)).thenReturn(true);
    setupClock();

    useCase.execute(TASK_ID, OWNER_ID, VERSION);

    verify(taskRepository).softDelete(eq(task), any(LocalDateTime.class));
    verify(auditLogPort)
        .record(eq(AuditEventType.TASK_DELETED), eq(TENANT_ID), eq(OWNER_ID), any());
  }

  @Test
  void execute_doesNotSoftDelete_whenPreconditionFails() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeDeletedBy(task, OWNER_ID)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, VERSION + 1))
        .isInstanceOf(PreconditionFailedException.class);

    verify(taskRepository, never()).softDelete(any(), any());
    verify(auditLogPort, never()).record(any(), any(), any(), any());
  }
}
