package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;

/**
 * PATCH /api/tasks/{id} リクエストボディ。ADR-0014 に従い JsonNullable セマンティクスで 3 状態を表現する:
 *
 * <ul>
 *   <li>フィールド省略 → {@code JsonNullable.undefined()} — 変更なし
 *   <li>{@code null} 明示 → {@code JsonNullable.of(null)} — フィールドをクリア(description / assigneeId のみ)
 *   <li>値指定 → {@code JsonNullable.of(value)} — 更新
 * </ul>
 */
public record TaskPatchRequest(
    JsonNullable<String> title,
    JsonNullable<@Nullable String> description,
    JsonNullable<Priority> priority,
    JsonNullable<@Nullable Long> assigneeId,
    JsonNullable<LocalDate> dueDate) {}
