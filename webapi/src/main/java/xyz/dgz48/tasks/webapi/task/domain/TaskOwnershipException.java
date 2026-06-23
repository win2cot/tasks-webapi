package xyz.dgz48.tasks.webapi.task.domain;

import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.shared.exception.DomainException;

/**
 * タスク操作権限違反。403 + 監査ログ(基本設計書 §6.2.3)。
 *
 * <p>{@code deniedAction} が指定された場合のみ §6.2.3 の {@code *_DENIED} として記録される。クロステナント 関係者登録の拒否など、§6.2.3
 * の認可違反シナリオに該当しない権限違反では {@code null} を渡す。
 */
public class TaskOwnershipException extends DomainException {

  private final Long taskId;
  private final @Nullable AuditEventType deniedAction;

  /** 監査記録対象外の操作権限違反(例: クロステナント関係者登録の拒否)。 */
  public TaskOwnershipException(Long taskId) {
    this(taskId, null);
  }

  /**
   * 操作権限違反。
   *
   * @param taskId 対象タスク ID
   * @param deniedAction §6.2.3 の {@code *_DENIED} アクション。記録不要なら {@code null}
   */
  public TaskOwnershipException(Long taskId, @Nullable AuditEventType deniedAction) {
    super("タスクの操作権限がありません: " + taskId);
    this.taskId = taskId;
    this.deniedAction = deniedAction;
  }

  public Long getTaskId() {
    return taskId;
  }

  public @Nullable AuditEventType getDeniedAction() {
    return deniedAction;
  }
}
