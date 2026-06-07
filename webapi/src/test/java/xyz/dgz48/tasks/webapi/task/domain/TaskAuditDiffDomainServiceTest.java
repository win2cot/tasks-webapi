package xyz.dgz48.tasks.webapi.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class TaskAuditDiffDomainServiceTest {

  private final TaskAuditDiffDomainService service = new TaskAuditDiffDomainService();

  private static final Long TASK_ID = 1L;
  private static final Long TENANT_ID = 100L;
  private static final Long OWNER_ID = 10L;
  private static final LocalDate DUE = LocalDate.of(2026, 8, 1);

  private Task baseTask() {
    return new Task(
        TASK_ID,
        TENANT_ID,
        "Original title",
        "Original description",
        TaskStatus.NOT_STARTED,
        Priority.MEDIUM,
        Visibility.TENANT,
        OWNER_ID,
        null,
        DUE,
        null,
        null,
        LocalDateTime.of(2026, 6, 1, 0, 0),
        LocalDateTime.of(2026, 6, 1, 0, 0),
        0L);
  }

  @Test
  void diff_returnsEmpty_whenAllFieldsUndefined() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    assertThat(service.diff(baseTask(), cmd)).isEmpty();
  }

  @Test
  void diff_returnsEmpty_whenAllFieldsSameValue() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.of("Original title"),
            JsonNullable.of("Original description"),
            JsonNullable.of(Priority.MEDIUM),
            JsonNullable.of(null),
            JsonNullable.of(DUE));

    assertThat(service.diff(baseTask(), cmd)).isEmpty();
  }

  @Test
  void diff_detectsTitleChange() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.of("New title"),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("title");
    assertThat(changes.get(0).oldValue()).isEqualTo("Original title");
    assertThat(changes.get(0).newValue()).isEqualTo("New title");
  }

  @Test
  void diff_detectsDescriptionClear() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.of(null),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("description");
    assertThat(changes.get(0).oldValue()).isEqualTo("Original description");
    assertThat(changes.get(0).newValue()).isNull();
  }

  @Test
  void diff_noChange_whenDescriptionAlreadyNullAndClearedExplicitly() {
    Task taskWithNullDesc =
        new Task(
            TASK_ID,
            TENANT_ID,
            "Title",
            null,
            TaskStatus.NOT_STARTED,
            Priority.MEDIUM,
            Visibility.TENANT,
            OWNER_ID,
            null,
            DUE,
            null,
            null,
            LocalDateTime.of(2026, 6, 1, 0, 0),
            LocalDateTime.of(2026, 6, 1, 0, 0),
            0L);

    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.of(null),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    assertThat(service.diff(taskWithNullDesc, cmd)).isEmpty();
  }

  @Test
  void diff_detectsPriorityChange() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(Priority.HIGH),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("priority");
    assertThat(changes.get(0).oldValue()).isEqualTo(Priority.MEDIUM);
    assertThat(changes.get(0).newValue()).isEqualTo(Priority.HIGH);
  }

  @Test
  void diff_detectsAssigneeSet() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(42L),
            JsonNullable.undefined());

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("assigneeId");
    assertThat(changes.get(0).oldValue()).isNull();
    assertThat(changes.get(0).newValue()).isEqualTo(42L);
  }

  @Test
  void diff_detectsDueDateChange() {
    LocalDate newDate = LocalDate.of(2026, 9, 1);
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.undefined(),
            JsonNullable.of(newDate));

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).field()).isEqualTo("dueDate");
    assertThat(changes.get(0).oldValue()).isEqualTo(DUE);
    assertThat(changes.get(0).newValue()).isEqualTo(newDate);
  }

  @Test
  void diff_detectsMultipleChanges() {
    TaskPatchCommand cmd =
        new TaskPatchCommand(
            JsonNullable.of("New title"),
            JsonNullable.of(null),
            JsonNullable.of(Priority.HIGH),
            JsonNullable.undefined(),
            JsonNullable.undefined());

    List<FieldChange> changes = service.diff(baseTask(), cmd);

    assertThat(changes).hasSize(3);
    assertThat(changes.stream().map(FieldChange::field))
        .containsExactly("title", "description", "priority");
  }
}
