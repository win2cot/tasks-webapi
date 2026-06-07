package xyz.dgz48.tasks.webapi.task.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * PATCH /api/tasks/{id} のコマンド。JsonNullable セマンティクス(ADR-0014):
 *
 * <ul>
 *   <li>{@code JsonNullable.undefined()} — 未指定、変更なし
 *   <li>{@code JsonNullable.of(null)} — null 明示指定(フィールドをクリア)
 *   <li>{@code JsonNullable.of(value)} — 値更新
 * </ul>
 */
public record TaskPatchCommand(
    JsonNullable<String> title,
    JsonNullable<@Nullable String> description,
    JsonNullable<Priority> priority,
    JsonNullable<@Nullable Long> assigneeId,
    JsonNullable<LocalDate> dueDate) {}
