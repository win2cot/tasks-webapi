package xyz.dgz48.tasks.webapi.notification.adapter.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * OpenAPI {@code NotificationSettings}(更新リクエスト本体、A-24)。
 *
 * <p>3 フラグはいずれも必須({@code required})。{@code updatedAt} は readOnly のため入力では受け取らない。
 */
public record NotificationSettingsUpdateRequest(
    @NotNull Boolean emailDueToday,
    @NotNull Boolean emailOverdue,
    @NotNull Boolean emailStakeholder) {}
