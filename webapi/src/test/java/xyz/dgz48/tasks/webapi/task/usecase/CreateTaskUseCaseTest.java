package xyz.dgz48.tasks.webapi.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import xyz.dgz48.tasks.webapi.task.domain.Task;
import xyz.dgz48.tasks.webapi.task.domain.TaskOwnershipException;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

@ExtendWith(MockitoExtension.class)
class CreateTaskUseCaseTest {

  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final Long STAKEHOLDER_ID = 20L;

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
  private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T01:00:00Z");
  private static final LocalDateTime FIXED_LDT = LocalDateTime.ofInstant(FIXED_INSTANT, JST);

  @Mock TaskRepository taskRepository;
  @Mock StakeholderRepository stakeholderRepository;
  @Mock TenantMembershipPort tenantMembershipPort;
  @Mock AuditLogPort auditLogPort;
  @Mock Clock clock;
  @InjectMocks CreateTaskUseCase useCase;

  private Task buildCreatedTask(Visibility visibility) {
    return new Task(
        1L,
        TENANT_ID,
        "テストタスク",
        null,
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        visibility,
        OWNER_ID,
        null,
        LocalDate.of(2026, 12, 31),
        null,
        null,
        FIXED_LDT,
        FIXED_LDT,
        0L);
  }

  @Test
  void execute_createsTaskWithNotStartedStatus() {
    Task created = buildCreatedTask(Visibility.TENANT);
    when(taskRepository.create(
            eq(TENANT_ID),
            eq(OWNER_ID),
            eq("テストタスク"),
            any(),
            eq(Priority.MEDIUM),
            eq(Visibility.TENANT),
            any(),
            any()))
        .thenReturn(created);

    Task result =
        useCase.execute(
            TENANT_ID,
            OWNER_ID,
            "テストタスク",
            null,
            Priority.MEDIUM,
            Visibility.TENANT,
            null,
            LocalDate.of(2026, 12, 31),
            null);

    assertThat(result.getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
    assertThat(result.getId()).isEqualTo(1L);
    verify(auditLogPort)
        .record(eq(AuditEventType.TASK_CREATED), eq(TENANT_ID), eq(OWNER_ID), any(String.class));
  }

  @Test
  void execute_addsStakeholders_whenVisibilityIsStakeholders() {
    Task created = buildCreatedTask(Visibility.STAKEHOLDERS);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);
    when(tenantMembershipPort.findActiveRole(STAKEHOLDER_ID, TENANT_ID))
        .thenReturn(Optional.of(TenantRole.MEMBER));
    when(clock.getZone()).thenReturn(JST);
    when(clock.instant()).thenReturn(FIXED_INSTANT);

    useCase.execute(
        TENANT_ID,
        OWNER_ID,
        "STAKEHOLDERSタスク",
        null,
        Priority.MEDIUM,
        Visibility.STAKEHOLDERS,
        null,
        LocalDate.of(2026, 12, 31),
        List.of(STAKEHOLDER_ID));

    verify(stakeholderRepository)
        .add(eq(1L), eq(TENANT_ID), eq(STAKEHOLDER_ID), eq(OWNER_ID), any(LocalDateTime.class));
  }

  @Test
  void execute_throwsTaskOwnershipException_whenStakeholderNotInTenant() {
    Task created = buildCreatedTask(Visibility.STAKEHOLDERS);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);
    when(tenantMembershipPort.findActiveRole(STAKEHOLDER_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    TENANT_ID,
                    OWNER_ID,
                    "STAKEHOLDERSタスク",
                    null,
                    Priority.MEDIUM,
                    Visibility.STAKEHOLDERS,
                    null,
                    LocalDate.of(2026, 12, 31),
                    List.of(STAKEHOLDER_ID)))
        .isInstanceOf(TaskOwnershipException.class);

    verify(stakeholderRepository, never()).add(any(), any(), any(), any(), any());
  }

  @Test
  void execute_deduplicatesStakeholderIds_whenDuplicatesGiven() {
    Task created = buildCreatedTask(Visibility.STAKEHOLDERS);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);
    when(tenantMembershipPort.findActiveRole(STAKEHOLDER_ID, TENANT_ID))
        .thenReturn(Optional.of(TenantRole.MEMBER));
    when(clock.getZone()).thenReturn(JST);
    when(clock.instant()).thenReturn(FIXED_INSTANT);

    useCase.execute(
        TENANT_ID,
        OWNER_ID,
        "重複IDタスク",
        null,
        Priority.MEDIUM,
        Visibility.STAKEHOLDERS,
        null,
        LocalDate.of(2026, 12, 31),
        List.of(STAKEHOLDER_ID, STAKEHOLDER_ID));

    verify(stakeholderRepository, times(1))
        .add(eq(1L), eq(TENANT_ID), eq(STAKEHOLDER_ID), eq(OWNER_ID), any(LocalDateTime.class));
  }

  @Test
  void execute_doesNotAddStakeholders_whenVisibilityIsTenant() {
    Task created = buildCreatedTask(Visibility.TENANT);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);

    useCase.execute(
        TENANT_ID,
        OWNER_ID,
        "TENANTタスク",
        null,
        Priority.MEDIUM,
        Visibility.TENANT,
        null,
        LocalDate.of(2026, 12, 31),
        List.of(STAKEHOLDER_ID));

    verify(stakeholderRepository, never()).add(any(), any(), any(), any(), any());
  }

  @Test
  void execute_doesNotAddStakeholders_whenStakeholderUserIdsIsNull() {
    Task created = buildCreatedTask(Visibility.STAKEHOLDERS);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);

    useCase.execute(
        TENANT_ID,
        OWNER_ID,
        "STAKEHOLDERSタスク(関係者なし)",
        null,
        Priority.MEDIUM,
        Visibility.STAKEHOLDERS,
        null,
        LocalDate.of(2026, 12, 31),
        null);

    verify(stakeholderRepository, never()).add(any(), any(), any(), any(), any());
  }

  @Test
  void execute_recordsAuditLog() {
    Task created = buildCreatedTask(Visibility.TENANT);
    when(taskRepository.create(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(created);

    useCase.execute(
        TENANT_ID,
        OWNER_ID,
        "監査ログテスト",
        null,
        Priority.LOW,
        Visibility.TENANT,
        null,
        LocalDate.of(2026, 12, 31),
        null);

    verify(auditLogPort)
        .record(eq(AuditEventType.TASK_CREATED), eq(TENANT_ID), eq(OWNER_ID), eq("{\"taskId\":1}"));
  }
}
