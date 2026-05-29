package xyz.dgz48.tasks.webapi.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaskAuthorizationDomainServiceTest {

  private final TaskAuthorizationDomainService service = new TaskAuthorizationDomainService();

  private static final Long OWNER_ID = 1L;
  private static final Long ASSIGNEE_ID = 2L;
  private static final Long STAKEHOLDER_ID = 3L;
  private static final Long OTHER_ID = 4L;

  private Task mockTask(Visibility visibility, Long ownerId, @Nullable Long assigneeId) {
    Task task = mock(Task.class);
    when(task.getVisibility()).thenReturn(visibility);
    when(task.getOwnerId()).thenReturn(ownerId);
    when(task.getAssigneeId()).thenReturn(assigneeId);
    return task;
  }

  @Nested
  class CanBeViewedBy {

    @Test
    void tenantAdminCannotViewPrivateTask() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OTHER_ID, TenantRole.TENANT_ADMIN, List.of()))
          .isFalse();
    }

    @Test
    void saasAdminCannotViewPrivateTask() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OTHER_ID, TenantRole.SAAS_ADMIN, List.of()))
          .isFalse();
    }

    @Test
    void tenantVisibilityAllowsAnyMember() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OTHER_ID, TenantRole.MEMBER, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsOwner() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OWNER_ID, TenantRole.MEMBER, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsAssignee() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, ASSIGNEE_ID, TenantRole.MEMBER, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsRegisteredStakeholder() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, null);
      assertThat(
              service.canBeViewedBy(
                  task, STAKEHOLDER_ID, TenantRole.MEMBER, List.of(STAKEHOLDER_ID)))
          .isTrue();
    }

    @Test
    void stakeholdersVisibilityDeniesUnrelatedMember() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, OTHER_ID, TenantRole.MEMBER, List.of())).isFalse();
    }

    @Test
    void privateVisibilityAllowsOwner() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OWNER_ID, TenantRole.MEMBER, List.of())).isTrue();
    }

    @Test
    void privateVisibilityAllowsAssignee() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, ASSIGNEE_ID, TenantRole.MEMBER, List.of())).isTrue();
    }

    @Test
    void privateVisibilityDeniesNonOwner() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OTHER_ID, TenantRole.MEMBER, List.of())).isFalse();
    }
  }

  @Nested
  class CanBeEditedBy {

    @Test
    void ownerCanEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeEditedBy(task, OWNER_ID, TenantRole.MEMBER)).isTrue();
    }

    @Test
    void tenantAdminCanEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeEditedBy(task, OTHER_ID, TenantRole.TENANT_ADMIN)).isTrue();
    }

    @Test
    void saasAdminCannotEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeEditedBy(task, OTHER_ID, TenantRole.SAAS_ADMIN)).isFalse();
    }

    @Test
    void nonOwnerMemberCannotEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeEditedBy(task, OTHER_ID, TenantRole.MEMBER)).isFalse();
    }
  }

  @Nested
  class CanBeDeletedBy {

    @Test
    void ownerCanDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeDeletedBy(task, OWNER_ID, TenantRole.MEMBER)).isTrue();
    }

    @Test
    void tenantAdminCanDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeDeletedBy(task, OTHER_ID, TenantRole.TENANT_ADMIN)).isTrue();
    }

    @Test
    void saasAdminCannotDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeDeletedBy(task, OTHER_ID, TenantRole.SAAS_ADMIN)).isFalse();
    }

    @Test
    void nonOwnerMemberCannotDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeDeletedBy(task, OTHER_ID, TenantRole.MEMBER)).isFalse();
    }
  }

  @Nested
  class CanChangeStatusBy {

    @Test
    void ownerCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OWNER_ID, TenantRole.MEMBER)).isTrue();
    }

    @Test
    void assigneeCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, ASSIGNEE_ID, TenantRole.MEMBER)).isTrue();
    }

    @Test
    void tenantAdminCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OTHER_ID, TenantRole.TENANT_ADMIN)).isTrue();
    }

    @Test
    void saasAdminCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OTHER_ID, TenantRole.SAAS_ADMIN)).isTrue();
    }

    @Test
    void nonRelatedMemberCannotChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OTHER_ID, TenantRole.MEMBER)).isFalse();
    }
  }

  @Nested
  class CanChangeVisibilityBy {

    @Test
    void ownerCanChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canChangeVisibilityBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void nonOwnerCannotChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canChangeVisibilityBy(task, OTHER_ID)).isFalse();
    }
  }
}
