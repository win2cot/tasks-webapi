package xyz.dgz48.tasks.webapi.task.domain;

import java.util.List;

/**
 * タスクごとの認可判定 SSOT — 基本設計書 §6.2.1 参照。
 *
 * <p>Spring 非依存の純粋関数。UseCase 層で {@code new TaskAuthorizationDomainService()} により インスタンス化するか、feature
 * の infra 設定クラスで {@code @Bean} 登録する。各メソッドは boolean を返し、 UseCase 層が false 時に
 * TaskNotViewableException(404) または TaskOwnershipException(403) を throw する。
 *
 * <p>{@link Task} は JPA 非依存の Domain POJO。永続化は {@code task.adapter.persistence.TaskJpaEntity}
 * が担う。
 */
public class TaskAuthorizationDomainService {

  /**
   * 参照可否を返す。ADR-0005 により Tenant Admin / SaaS Admin の業務タスク特権は撤廃済み。
   *
   * <ul>
   *   <li>TENANT: テナント全員参照可
   *   <li>STAKEHOLDERS: 所有者 / 担当者 / task_stakeholders 登録ユーザーのみ
   *   <li>PRIVATE: 所有者 / 担当者のみ
   * </ul>
   */
  public boolean canBeViewedBy(
      Task task, Long userId, TenantRole role, List<Long> stakeholderUserIds) {
    Visibility visibility = task.getVisibility();
    return switch (visibility) {
      case TENANT -> true;
      case STAKEHOLDERS ->
          task.getOwnerId().equals(userId)
              || userId.equals(task.getAssigneeId())
              || stakeholderUserIds.contains(userId);
      case PRIVATE ->
          task.getOwnerId().equals(userId) || userId.equals(task.getAssigneeId());
    };
  }

  /** 編集可否を返す。所有者または Tenant Admin のみ可(SaaS Admin は不可)。監査ログ記録は UseCase 側の責務。 */
  public boolean canBeEditedBy(Task task, Long userId, TenantRole role) {
    return task.getOwnerId().equals(userId) || role == TenantRole.TENANT_ADMIN;
  }

  /**
   * 削除可否を返す。所有者または Tenant Admin のみ可(SaaS Admin は不可)。
   *
   * <p>現仕様では {@link #canBeEditedBy} と同一だが、削除ポリシーが独立して変更される可能性があるため 意図的に分離している。
   */
  public boolean canBeDeletedBy(Task task, Long userId, TenantRole role) {
    return task.getOwnerId().equals(userId) || role == TenantRole.TENANT_ADMIN;
  }

  /** ステータス変更可否を返す。所有者・担当者・Admin。 */
  public boolean canChangeStatusBy(Task task, Long userId, TenantRole role) {
    return task.getOwnerId().equals(userId)
        || userId.equals(task.getAssigneeId())
        || role.isAdmin();
  }

  /** 公開範囲変更・関係者編集可否を返す。所有者のみ(Admin も不可)。 */
  public boolean canChangeVisibilityBy(Task task, Long userId) {
    return task.getOwnerId().equals(userId);
  }
}
