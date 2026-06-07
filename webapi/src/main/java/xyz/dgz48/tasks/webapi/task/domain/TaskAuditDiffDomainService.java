package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * PATCH 更新差分を field-by-field で計算する純粋関数ドメインサービス(ADR-0013)。
 *
 * <p>Spring 非依存。{@code TaskInfraConfig} で {@code @Bean} 登録する。
 */
public class TaskAuditDiffDomainService {

  /**
   * 旧タスクと PATCH コマンドを比較し、変更があったフィールドの差分リストを返す。
   *
   * <ul>
   *   <li>{@code JsonNullable.undefined()} → diff 不在
   *   <li>{@code JsonNullable.of(null)} + 旧値 non-null → {@code {old: <旧値>, new: null}}
   *   <li>{@code JsonNullable.of(null)} + 旧値 null → diff 不在(同値)
   *   <li>{@code JsonNullable.of(value)} + 旧値と同値 → diff 不在
   *   <li>{@code JsonNullable.of(value)} + 旧値と異なる → {@code {old: <旧値>, new: <新値>}}
   * </ul>
   */
  public List<FieldChange> diff(Task previous, TaskPatchCommand cmd) {
    List<FieldChange> changes = new ArrayList<>();

    if (cmd.title().isPresent()) {
      String newVal = cmd.title().get();
      if (newVal != null && !newVal.equals(previous.getTitle())) {
        changes.add(new FieldChange("title", previous.getTitle(), newVal));
      }
    }

    if (cmd.description().isPresent()) {
      @Nullable String newVal = cmd.description().get();
      @Nullable String oldVal = previous.getDescription();
      if (!nullableEquals(oldVal, newVal)) {
        changes.add(new FieldChange("description", oldVal, newVal));
      }
    }

    if (cmd.priority().isPresent()) {
      Priority newVal = cmd.priority().get();
      if (newVal != null && !newVal.equals(previous.getPriority())) {
        changes.add(new FieldChange("priority", previous.getPriority(), newVal));
      }
    }

    if (cmd.assigneeId().isPresent()) {
      @Nullable Long newVal = cmd.assigneeId().get();
      @Nullable Long oldVal = previous.getAssigneeId();
      if (!nullableEquals(oldVal, newVal)) {
        changes.add(new FieldChange("assigneeId", oldVal, newVal));
      }
    }

    if (cmd.dueDate().isPresent()) {
      LocalDate newVal = cmd.dueDate().get();
      if (newVal != null && !newVal.equals(previous.getDueDate())) {
        changes.add(new FieldChange("dueDate", previous.getDueDate(), newVal));
      }
    }

    return changes;
  }

  private static boolean nullableEquals(@Nullable Object a, @Nullable Object b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }
}
