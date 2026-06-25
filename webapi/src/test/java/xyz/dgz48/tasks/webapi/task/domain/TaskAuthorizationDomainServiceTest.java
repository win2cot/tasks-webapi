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
  // テナント運営者(TENANT_ADMIN)に相当する、タスクの所有者・担当者・関係者のいずれでもないユーザー。
  // ドメイン SSOT はテナントロールを引数に取らない(= 業務タスク認可はテナントロールを参照しない)。
  private static final Long TENANT_ADMIN_ID = 5L;

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
    void stakeholdersVisibilityDeniesUnregisteredStakeholderCandidate() {
      // 関係者リストに登録されていない限り、STAKEHOLDERS でも参照不可(参照権限は登録の有無で決まる)。
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, STAKEHOLDER_ID, List.of(OTHER_ID))).isFalse();
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

  /**
   * ADR-0005 撤廃確認: Tenant Admin に業務タスク特権が無いことを SSOT(本ドメインサービス)レベルで固定する。
   *
   * <p>本サービスはテナントロールを一切受け取らず、所有者・担当者・関係者の 3 役割のみで評価する。よって「テナント運営者であること」は
   * 業務タスクの編集・削除・ステータス変更・公開範囲変更・関係者管理のいずれにも作用しない。参照については、TENANT 公開のタスクは
   * テナントメンバー全員が見られる(運営者特権ではなく通常のメンバー権)が、STAKEHOLDERS / PRIVATE では運営者でも所有者・担当者・登録関係者で ない限り参照できない。
   */
  @Nested
  class TenantAdminHasNoBusinessTaskPrivilege {

    @Test
    void canViewTenantTask_asOrdinaryMember_notAsPrivilege() {
      // TENANT 公開はメンバー全員に開かれている。運営者も「メンバーとして」参照できるだけで特権ではない。
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, TENANT_ADMIN_ID, List.of())).isTrue();
    }

    @Test
    void cannotViewStakeholdersTask_whenNotRegistered() {
      Task task = mockTask(Visibility.STAKEHOLDERS, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, TENANT_ADMIN_ID, List.of(STAKEHOLDER_ID))).isFalse();
    }

    @Test
    void cannotViewPrivateTask() {
      Task task = mockTask(Visibility.PRIVATE, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeViewedBy(task, TENANT_ADMIN_ID, List.of())).isFalse();
    }

    @Test
    void cannotEdit() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeEditedBy(task, TENANT_ADMIN_ID)).isFalse();
    }

    @Test
    void cannotDelete() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canBeDeletedBy(task, TENANT_ADMIN_ID)).isFalse();
    }

    @Test
    void cannotChangeStatus() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeStatusBy(task, TENANT_ADMIN_ID)).isFalse();
    }

    @Test
    void cannotChangeVisibility() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canChangeVisibilityBy(task, TENANT_ADMIN_ID)).isFalse();
    }

    @Test
    void cannotManageStakeholders() {
      Task task = mockTask(Visibility.TENANT, OWNER_ID, ASSIGNEE_ID);
      assertThat(service.canManageStakeholdersBy(task, TENANT_ADMIN_ID)).isFalse();
    }
  }
}
