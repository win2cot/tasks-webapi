package xyz.dgz48.tasks.webapi.notification.usecase;

import java.time.LocalDateTime;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.notification.domain.NotificationSettings;

/**
 * 通知設定の取得・更新ポート(クリーンアーキの out port)。
 *
 * <p>実装は adapter.persistence。テナント分離は Hibernate Filter(ADR-0010)が自動付与する({@code
 * user_notification_settings} は {@code TenantFilteredEntity} 継承)。読み書きとも現在のテナント({@code X-Tenant-Id})
 * スコープに限定される。
 */
public interface NotificationSettingsPort {

  /** 現在のテナントにおける指定ユーザーの設定を取得する(未存在なら空)。 */
  Optional<NotificationSettings> find(Long userId);

  /** 現在のテナントにおける指定ユーザーの設定を upsert し、保存後の値を返す。 */
  NotificationSettings upsert(
      Long userId,
      Long tenantId,
      boolean emailDueToday,
      boolean emailOverdue,
      boolean emailStakeholder,
      LocalDateTime updatedAt);
}
