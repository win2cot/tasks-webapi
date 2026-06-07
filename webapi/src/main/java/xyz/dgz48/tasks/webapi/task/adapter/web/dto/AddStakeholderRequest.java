package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import jakarta.validation.constraints.NotNull;

public record AddStakeholderRequest(@NotNull Long userId) {}
