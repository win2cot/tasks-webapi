package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;

public record ChangeTaskStatusRequest(@NotNull TaskStatus status) {}
