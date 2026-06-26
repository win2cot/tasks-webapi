package xyz.dgz48.tasks.webapi.notification.usecase;

/**
 * 通知設定更新(A-24)のコマンド。
 *
 * @param userId 更新対象ユーザー(ログイン中ユーザー自身)
 * @param tenantId 現在のテナント(レコード新規作成時の tenant_id)
 */
public record UpdateNotificationSettingsCommand(
    Long userId,
    Long tenantId,
    boolean emailDueToday,
    boolean emailOverdue,
    boolean emailStakeholder) {}
