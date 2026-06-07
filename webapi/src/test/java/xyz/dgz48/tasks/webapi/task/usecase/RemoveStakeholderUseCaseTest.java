package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.StakeholderNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

@ExtendWith(MockitoExtension.class)
class RemoveStakeholderUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long ASSIGNEE_ID = 20L;
  private static final Long OTHER_USER_ID = 30L;
  private static final Long STAKEHOLDER_ID = 40L;

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @Mock AuditLogPort auditLogPort;
  @InjectMocks RemoveStakeholderUseCase useCase;

  private Task buildTask() {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Test task",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.STAKEHOLDERS,
        OWNER_ID,
        ASSIGNEE_ID,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 0, 0),
        LocalDateTime.of(2026, 5, 31, 0, 0),
        0L);
  }

  @Test
  void execute_removesStakeholderSuccessfully() {
    Task task = buildTask();
    List<Long> stakeholderIds = List.of(STAKEHOLDER_ID);
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(stakeholderIds);
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, stakeholderIds))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OWNER_ID)).thenReturn(true);
    when(stakeholderRepository.existsByTaskIdAndUserId(TASK_ID, STAKEHOLDER_ID)).thenReturn(true);

    assertThatCode(() -> useCase.execute(TASK_ID, OWNER_ID, STAKEHOLDER_ID))
        .doesNotThrowAnyException();

    verify(stakeholderRepository).removeByTaskIdAndUserId(TASK_ID, STAKEHOLDER_ID);
    verify(auditLogPort)
        .record(eq(AuditEventType.STAKEHOLDER_REMOVED), eq(TENANT_ID), eq(OWNER_ID), any());
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, STAKEHOLDER_ID))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenCannotManageStakeholders() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_USER_ID, List.of()))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OTHER_USER_ID))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_USER_ID, STAKEHOLDER_ID))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_throwsStakeholderNotFoundException_whenNotRegistered() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OWNER_ID)).thenReturn(true);
    when(stakeholderRepository.existsByTaskIdAndUserId(TASK_ID, STAKEHOLDER_ID)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, STAKEHOLDER_ID))
        .isInstanceOf(StakeholderNotFoundException.class);
  }
}
