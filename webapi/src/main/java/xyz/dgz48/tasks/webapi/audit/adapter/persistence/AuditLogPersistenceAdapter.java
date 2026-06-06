package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;

@Component
class AuditLogPersistenceAdapter implements AuditLogPort {

  private final AuditLogJpaRepository repository;
  private final Clock clock;

  AuditLogPersistenceAdapter(AuditLogJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void record(
      AuditEventType eventType, @Nullable Long tenantId, @Nullable Long userId, String detail) {
    var entity =
        new AuditLogJpaEntity(tenantId, userId, eventType.name(), detail, LocalDateTime.now(clock));
    repository.save(entity);
  }
}
