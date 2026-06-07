package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Priority;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

/** POST /api/tasks リクエストボディ。tenant_id / owner_id は認証コンテキストから自動付与する。 */
public record TaskCreateRequest(
    @NotBlank @Size(max = 100) String title,
    @Nullable @Size(max = 2000) String description,
    @NotNull Priority priority,
    @NotNull Visibility visibility,
    @Nullable Long assigneeId,
    @NotNull LocalDate dueDate,
    @Nullable List<Long> stakeholderUserIds) {}
