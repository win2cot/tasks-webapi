package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import xyz.dgz48.tasks.webapi.task.domain.StakeholderAlreadyExistsException;
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskAuthorizationDomainService;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotFoundException;
import xyz.dgz48.tasks.webapi.task.domain.TaskNotViewableException;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStakeholder;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

@ExtendWith(MockitoExtension.class)
class AddStakeholderUseCaseTest {

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long ASSIGNEE_ID = 20L;
  private static final Long OTHER_TENANT_USER_ID = 30L;
  private static final Long NEW_STAKEHOLDER_ID = 50L;

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
  private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T01:00:00Z");

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TaskAuthorizationDomainService taskAuthorizationDomainService;
  @Mock TenantMembershipPort tenantMembershipPort;
  @Mock AuditLogPort auditLogPort;
  @Mock Clock clock;
  @InjectMocks AddStakeholderUseCase useCase;

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
        ASSIGNEE_ID,
        LocalDate.of(2026, 6, 1),
        null,
        null,
        LocalDateTime.of(2026, 5, 31, 0, 0),
        LocalDateTime.of(2026, 5, 31, 0, 0),
        0L);
  }

  private TaskStakeholder buildStakeholder() {
    return new TaskStakeholder(
        NEW_STAKEHOLDER_ID,
        "新 関係者",
        "new-stakeholder@example.com",
        OWNER_ID,
        "所有者 太郎",
        LocalDateTime.now());
  }

  private void setupClock() {
    when(clock.getZone()).thenReturn(JST);
    when(clock.instant()).thenReturn(FIXED_INSTANT);
  }

  @Test
  void execute_addsStakeholderSuccessfully() {
    Task task = buildTask();
    TaskStakeholder expected = buildStakeholder();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OWNER_ID)).thenReturn(true);
    when(tenantMembershipPort.findActiveRole(NEW_STAKEHOLDER_ID, TENANT_ID))
        .thenReturn(Optional.of(TenantRole.MEMBER));
    when(stakeholderRepository.existsByTaskIdAndUserId(TASK_ID, NEW_STAKEHOLDER_ID))
        .thenReturn(false);
    setupClock();
    when(stakeholderRepository.add(
            eq(TASK_ID), eq(TENANT_ID), eq(NEW_STAKEHOLDER_ID), eq(OWNER_ID), any()))
        .thenReturn(expected);

    TaskStakeholder result = useCase.execute(TASK_ID, OWNER_ID, NEW_STAKEHOLDER_ID);

    assertThat(result.getUserId()).isEqualTo(NEW_STAKEHOLDER_ID);
    verify(auditLogPort)
        .record(eq(AuditEventType.STAKEHOLDER_ADDED), eq(TENANT_ID), eq(OWNER_ID), any());
  }

  @Test
  void execute_throwsTaskNotFoundException_whenTaskNotFound() {
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, NEW_STAKEHOLDER_ID))
        .isInstanceOf(TaskNotFoundException.class);
  }

  @Test
  void execute_throwsTaskNotViewableException_whenNotViewable() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_TENANT_USER_ID, List.of()))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_TENANT_USER_ID, NEW_STAKEHOLDER_ID))
        .isInstanceOf(TaskNotViewableException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenCannotManageStakeholders() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OTHER_TENANT_USER_ID, List.of()))
        .thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OTHER_TENANT_USER_ID))
        .thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OTHER_TENANT_USER_ID, NEW_STAKEHOLDER_ID))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_throwsTaskOwnershipException_whenTargetNotTenantMember() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OWNER_ID)).thenReturn(true);
    when(tenantMembershipPort.findActiveRole(anyLong(), eq(TENANT_ID)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, NEW_STAKEHOLDER_ID))
        .isInstanceOf(TaskOwnershipException.class);
  }

  @Test
  void execute_throwsStakeholderAlreadyExistsException_whenAlreadyRegistered() {
    Task task = buildTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
    when(stakeholderRepository.findUserIdsByTaskId(TASK_ID, TENANT_ID)).thenReturn(List.of());
    when(taskAuthorizationDomainService.canBeViewedBy(task, OWNER_ID, List.of())).thenReturn(true);
    when(taskAuthorizationDomainService.canManageStakeholdersBy(task, OWNER_ID)).thenReturn(true);
    when(tenantMembershipPort.findActiveRole(NEW_STAKEHOLDER_ID, TENANT_ID))
        .thenReturn(Optional.of(TenantRole.MEMBER));
    when(stakeholderRepository.existsByTaskIdAndUserId(TASK_ID, NEW_STAKEHOLDER_ID))
        .thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(TASK_ID, OWNER_ID, NEW_STAKEHOLDER_ID))
        .isInstanceOf(StakeholderAlreadyExistsException.class);
  }
}
