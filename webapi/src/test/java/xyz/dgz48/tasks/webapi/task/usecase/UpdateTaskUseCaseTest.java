package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.shared.exception.PreconditionFailedException;
import xyz.dgz48.tasks.webapi.task.domain.FieldChange;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuditDiffDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskPatchCommand;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class UpdateTaskUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long OTHER_ID = 20L;
  private static final LocalDate DUE = LocalDate.of(2026, 8, 1);
  private static final Long VERSION = 0L;

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @Mock TaskAuditDiffDomainService taskAuditDiffDomainService;
  @Mock AuditLogPort auditLogPort;
  @InjectMocks UpdateTaskUseCase useCase;

  private Task buildTask() {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Title",
        "Desc",
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.TENANT,
        OWNER_ID,
        null,
        DUE,
        null,
        null,
        LocalDateTime.of(2026, 6, 1, 0, 0),
        LocalDateTime.of(2026, 6, 1, 0, 0),
        VERSION);
  }

  private TaskPatchCommand titleOnlyCmd() {
    return new TaskPatchCommand(
        JsonNullable.of("New title"),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined(),
        JsonNullable.undefined());
  }

  @Test
  void execute_throwsTaskNotFoundException_whenTaskNotFound() {
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, titleOnlyCmd(), VERSION))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_ID, List.of())).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_ID, titleOnlyCmd(), VERSION))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenNotOwner() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeEditedBy(task, OTHER_ID)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_ID, titleOnlyCmd(), VERSION))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_throwsPreconditionFailedException_whenVersionMismatch() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeEditedBy(task, OWNER_ID)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, titleOnlyCmd(), VERSION + 1))
        .isInstanceOf(PreconditionFailedException.class);
  }

  @Test
  void execute_returnsTaskWithoutSave_whenNoDiff() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeEditedBy(task, OWNER_ID)).thenReturn(true);
    when(taskAuditDiffDomainService.diff(any(), any())).thenReturn(List.of());

    Task result = useCase.execute(TASK_ID, OWNER_ID, titleOnlyCmd(), VERSION);

    assertThat(result).isSameAs(task);
    verify(taskRepository, never()).save(any());
    verify(auditLogPort, never()).record(any(), any(), any(), any());
  }

  @Test
  void execute_savesAndRecordsAudit_whenChangesExist() {
    Task task = buildTask();
    Task saved = buildTask();
    List<FieldChange> changes = List.of(new FieldChange("title", "Title", "New title"));

    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeEditedBy(task, OWNER_ID)).thenReturn(true);
    when(taskAuditDiffDomainService.diff(any(), any())).thenReturn(changes);
    when(taskRepository.save(any())).thenReturn(saved);

    Task result = useCase.execute(TASK_ID, OWNER_ID, titleOnlyCmd(), VERSION);

    assertThat(result).isSameAs(saved);
    verify(taskRepository).save(task);
    verify(auditLogPort)
        .record(eq(AuditEventType.TASK_UPDATED), eq(TENANT_ID), eq(OWNER_ID), any(String.class));
  }

  @Test
  void execute_audit_detail_quotesLocalDate() {
    Task task = buildTask();
    List<FieldChange> changes =
        List.of(new FieldChange("dueDate", null, LocalDate.of(2026, 8, 1)));

    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canBeEditedBy(task, OWNER_ID)).thenReturn(true);
    when(taskAuditDiffDomainService.diff(any(), any())).thenReturn(changes);
    when(taskRepository.save(any())).thenReturn(buildTask());

    useCase.execute(TASK_ID, OWNER_ID, titleOnlyCmd(), VERSION);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(auditLogPort)
        .record(
            eq(AuditEventType.TASK_UPDATED), eq(TENANT_ID), eq(OWNER_ID), captor.capture());
    // LocalDate は "2026-08-01" と引用符付きで出力されなければならない（修正前は引用符なしで 500 になった）
    assertThat(captor.getValue()).contains("\"2026-08-01\"");
  }
}
