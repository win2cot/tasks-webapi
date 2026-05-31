package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
class UserTenantId implements Serializable {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;
}
