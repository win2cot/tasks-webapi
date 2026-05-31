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
    void tenantVisibilityAllowsAnyUser() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OTHER_ID, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsOwner() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OWNER_ID, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsAssignee() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, ASSIGNEE_ID, List.of())).isTrue();
    }

    @Test
    void stakeholdersVisibilityAllowsRegisteredStakeholder() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, STAKEHOLDER_ID, List.of(STAKEHOLDER_ID))).isTrue();
    }

    @Test
    void stakeholdersVisibilityDeniesUnrelatedUser() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, OTHER_ID, List.of())).isFalse();
    }

    @Test
    void privateVisibilityAllowsOwner() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, OWNER_ID, List.of())).isTrue();
    }

    @Test
    void privateVisibilityAllowsAssignee() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, ASSIGNEE_ID, List.of())).isTrue();
    }

    @Test
    void privateVisibilityDeniesUnrelatedUser() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, OTHER_ID, List.of())).isFalse();
    }

    @Test
    void privateVisibilityDeniesRegisteredStakeholder() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, null);
      assertThat(service.canBeViewedBy(task, STAKEHOLDER_ID, List.of(STAKEHOLDER_ID))).isFalse();
    }
  }

  @Nested
  class CanBeEditedBy {

    @Test
    void ownerCanEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeEditedBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void assigneeCannotEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeEditedBy(task, ASSIGNEE_ID)).isFalse();
    }

    @Test
    void nonOwnerCannotEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeEditedBy(task, OTHER_ID)).isFalse();
    }
  }

  @Nested
  class CanBeDeletedBy {

    @Test
    void ownerCanDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeDeletedBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void assigneeCannotDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeDeletedBy(task, ASSIGNEE_ID)).isFalse();
    }

    @Test
    void nonOwnerCannotDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canBeDeletedBy(task, OTHER_ID)).isFalse();
    }
  }

  @Nested
  class CanChangeStatusBy {

    @Test
    void ownerCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void assigneeCanChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, ASSIGNEE_ID)).isTrue();
    }

    @Test
    void nonOwnerNonAssigneeCannotChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, OTHER_ID)).isFalse();
    }
  }

  @Nested
  class CanChangeVisibilityBy {

    @Test
    void ownerCanChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeVisibilityBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void assigneeCannotChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeVisibilityBy(task, ASSIGNEE_ID)).isFalse();
    }

    @Test
    void nonOwnerCannotChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, null);
      assertThat(service.canChangeVisibilityBy(task, OTHER_ID)).isFalse();
    }
  }

  @Nested
  class CanManageStakeholdersBy {

    @Test
    void ownerCanManageStakeholders() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canManageStakeholdersBy(task, OWNER_ID)).isTrue();
    }

    @Test
    void assigneeCanManageStakeholders() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canManageStakeholdersBy(task, ASSIGNEE_ID)).isTrue();
    }

    @Test
    void nonOwnerNonAssigneeCannotManageStakeholders() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canManageStakeholdersBy(task, OTHER_ID)).isFalse();
    }
  }
}
