package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.InvitationPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/** {@link InvitationPort} の JPA 実装(ADR-0017 / ADR-0040)。 */
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

  @Override
  @Transactional(readOnly = true)
  public Optional<InvitationView> findByTokenHash(String tokenHash) {
    return invitationRepository
        .findByTokenHash(tokenHash)
        .map(
            e ->
                new InvitationView(
                    e.getId(),
                    e.getTenantId(),
                    e.getEmail(),
                    e.getRole(),
                    e.getStatus(),
                    e.getExpiresAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Long> findRegisteredUserId(String email) {
    // pending correlation 行(会員登録のみ・初回ログイン未了)は未登録扱い(ADR-0040 §3.2)。
    return userRepository
        .findByEmail(email)
        .filter(user -> !user.isPendingCorrelation())
        .map(user -> user.getId());
  }

  @Override
  @Transactional
  public void markUsed(Long invitationId, LocalDateTime consumedAt) {
    invitationRepository.findById(invitationId).ifPresent(e -> e.markUsed(consumedAt));
  }
}
