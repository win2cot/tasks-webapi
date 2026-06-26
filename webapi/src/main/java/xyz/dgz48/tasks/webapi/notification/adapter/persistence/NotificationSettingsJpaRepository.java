package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 通知設定の JPA リポジトリ。
 *
 * <p>テナント分離は Hibernate Filter "tenantFilter"(ADR-0010)が自動付与するため、{@link #findByUserId(Long)} は
 * 現在のテナントにおける当該ユーザーの 1 行のみを返す。
 */
interface NotificationSettingsJpaRepository
    extends JpaRepository<NotificationSettingsJpaEntity, NotificationSettingsId> {

  Optional<NotificationSettingsJpaEntity> findByUserId(Long userId);
}
