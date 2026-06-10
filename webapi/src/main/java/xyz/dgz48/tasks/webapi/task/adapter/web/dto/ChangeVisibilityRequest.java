package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;
import xyz.dgz48.tasks.webapi.task.domain.Visibility;

public record ChangeVisibilityRequest(
    @NotNull Visibility visibility, @Nullable List<Long> stakeholderUserIds) {}
