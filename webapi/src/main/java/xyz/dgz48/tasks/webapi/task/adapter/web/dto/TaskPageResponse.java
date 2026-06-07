package xyz.dgz48.tasks.webapi.task.adapter.web.dto;

import java.util.List;

/** OpenAPI TaskPage スキーマに対応するレスポンス DTO。 */
public record TaskPageResponse(
    List<TaskListItemResponse> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    int overdueCount) {}
