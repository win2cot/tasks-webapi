package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.InvitationPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/** {@link InvitationPort} の JPA 実装(ADR-0017)。 */
@Observed(name = "tenant.invitation.repository")
@Component
@RequiredArgsConstructor
class InvitationPersistenceAdapter implements InvitationPort {

  private final InvitationJpaRepository invitationRepository;
  private final UserTenantJpaRepository userTenantRepository;
  private final UserRepository userRepository;
  private final TenantJpaRepository tenantRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean isAlreadyMember(Long tenantId, String email) {
    return userRepository
        .findByEmail(email)
        .map(user -> userTenantRepository.existsByIdUserIdAndIdTenantId(user.getId(), tenantId))
        .orElse(false);
  }

  @Override
  @Transactional
  public void revokePending(Long tenantId, String email) {
    // tenantFilter により tenant_id は自動絞り込み(ADR-0010)。
    invitationRepository
        .findByEmailAndStatus(email, InvitationStatus.PENDING)
        .forEach(InvitationJpaEntity::revoke);
  }

  @Override
  @Transactional
  public void save(NewInvitation invitation) {
    invitationRepository.save(
        new InvitationJpaEntity(
            invitation.tenantId(),
            invitation.email(),
            invitation.tokenHash(),
            invitation.role(),
            invitation.expiresAt(),
            invitation.invitedBy(),
            invitation.createdAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findTenantName(Long tenantId) {
    return tenantRepository.findById(tenantId).map(TenantJpaEntity::getName);
  }
}
