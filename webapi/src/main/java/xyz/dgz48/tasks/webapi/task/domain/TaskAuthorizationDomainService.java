package xyz.dgz48.tasks.webapi.task.domain;

import java.util.List;

/**
 * タスクごとの認可判定 SSOT — 基本設計書 §6.2.1 / ADR-0005 参照。
 *
 * <p>Spring 非依存の純粋関数。UseCase 層で {@code new TaskAuthorizationDomainService()} により インスタンス化するか、feature
 * の infra 設定クラスで {@code @Bean} 登録する。各メソッドは boolean を返し、 UseCase 層が false 時に
 * TaskNotViewableException(404) または TaskOwnershipException(403) を throw する。
 *
 * <p>ADR-0005 により、タスク認可はタスクスコープの 3 役割(所有者・担当者・関係者)のみで評価する。{@code TenantRole} (Tenant Admin / SaaS
 * Admin)によるバイパスは一切ない。
 *
 * <p>B-1 (#273) で Domain {@link Task}(POJO)を新規追加し、本クラスは domain.Task を参照する形に整理した。
 */
public class TaskAuthorizationDomainService {

  /**
   * 参照可否を返す(ADR-0005 §3.1)。
   *
   * <ul>
   *   <li>TENANT: テナント全員参照可(TenantRole 不問)
   *   <li>STAKEHOLDERS: 所有者・担当者・task_stakeholders 登録ユーザーのみ
   *   <li>PRIVATE: 所有者・担当者のみ(Tenant Admin / SaaS Admin バイパスなし)
   * </ul>
   */
  public boolean canBeViewedBy(Task task, Long userId, List<Long> stakeholderUserIds) {
    Visibility visibility = task.getVisibility();
    return switch (visibility) {
      case TENANT -> true;
      case STAKEHOLDERS ->
          task.getOwnerId().equals(userId)
              || userId.equals(task.getAssigneeId())
              || stakeholderUserIds.contains(userId);
      case PRIVATE -> task.getOwnerId().equals(userId) || userId.equals(task.getAssigneeId());
    };
  }

  /** 編集可否を返す(ADR-0005 §3.1)。所有者のみ可。監査ログ記録は UseCase 側の責務。 */
  public boolean canBeEditedBy(Task task, Long userId) {
    return task.getOwnerId().equals(userId);
  }

  /**
   * 削除可否を返す(ADR-0005 §3.1)。所有者のみ可。
   *
   * <p>現仕様では {@link #canBeEditedBy} と同一だが、削除ポリシーが独立して変更される可能性があるため意図的に分離している。
   */
  public boolean canBeDeletedBy(Task task, Long userId) {
    return task.getOwnerId().equals(userId);
  }

  /** ステータス変更可否を返す(ADR-0005 §3.1)。所有者・担当者のみ可。 */
  public boolean canChangeStatusBy(Task task, Long userId) {
    return task.getOwnerId().equals(userId) || userId.equals(task.getAssigneeId());
  }

  /** 公開範囲変更・関係者編集可否を返す(ADR-0005 §3.1)。所有者のみ可。 */
  public boolean canChangeVisibilityBy(Task task, Long userId) {
    return task.getOwnerId().equals(userId);
  }
}
