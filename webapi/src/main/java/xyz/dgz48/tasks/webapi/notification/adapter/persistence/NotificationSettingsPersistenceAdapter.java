package xyz.dgz48.tasks.webapi.notification.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.notification.domain.NotificationSettings;
import xyz.dgz48.tasks.webapi.notification.usecase.NotificationSettingsPort;

/**
 * {@link NotificationSettingsPort} の JPA 実装。
 *
 * <p>読み取りは Hibernate Filter により現在テナントへ自動絞り込み。upsert は SELECT(現在テナント)結果の有無で update / insert
 * を分岐し、insert 時のみ {@code tenant_id} を明示設定する(Filter は INSERT に作用しないため)。
 */
@Observed(name = "notification.settings.repository")
@Component
@RequiredArgsConstructor
class NotificationSettingsPersistenceAdapter implements NotificationSettingsPort {

  private final NotificationSettingsJpaRepository repository;

  @Override
  public Optional<NotificationSettings> find(Long userId) {
    return repository.findByUserId(userId).map(NotificationSettingsPersistenceAdapter::toDomain);
  }

  @Override
  public NotificationSettings upsert(
      Long userId,
      Long tenantId,
      boolean emailDueToday,
      boolean emailOverdue,
      boolean emailStakeholder,
      LocalDateTime updatedAt) {
    NotificationSettingsJpaEntity entity =
        repository
            .findByUserId(userId)
            .map(
                existing -> {
                  existing.update(emailDueToday, emailOverdue, emailStakeholder, updatedAt);
                  return existing;
                })
            .orElseGet(
                () ->
                    new NotificationSettingsJpaEntity(
                        userId,
                        tenantId,
                        emailDueToday,
                        emailOverdue,
                        emailStakeholder,
                        updatedAt));
    return toDomain(repository.save(entity));
  }

  private static NotificationSettings toDomain(NotificationSettingsJpaEntity e) {
    return new NotificationSettings(
        e.isEmailDueToday(), e.isEmailOverdue(), e.isEmailStakeholder(), e.getUpdatedAt());
  }
}
