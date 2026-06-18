package xyz.dgz48.tasks.webapi.security.adapter.web.dto;

import org.jspecify.annotations.Nullable;

public record UserProfileDto(
    Long id, String email, String fullName, String fullNameKana, @Nullable String departmentName) {}
