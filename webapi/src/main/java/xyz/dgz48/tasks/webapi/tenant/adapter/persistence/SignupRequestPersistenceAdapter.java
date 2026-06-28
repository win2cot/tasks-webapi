package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import io.micrometer.observation.annotation.Observed;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.SignupRequestPort;

/** {@link SignupRequestPort} の JPA 実装(ADR-0040 §3.3)。 */
@Observed(name = "tenant.signup.repository")
@Component
@RequiredArgsConstructor
class SignupRequestPersistenceAdapter implements SignupRequestPort {

  private final SignupRequestJpaRepository signupRequestRepository;

  @Override
  @Transactional
  public void replacePending(
      String email, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
    signupRequestRepository
        .findByEmailAndStatus(email, SignupRequestStatus.PENDING)
        .forEach(SignupRequestJpaEntity::revoke);
    signupRequestRepository.save(
        new SignupRequestJpaEntity(email, tokenHash, expiresAt, createdAt));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SignupRequestView> findByTokenHash(String tokenHash) {
    return signupRequestRepository
        .findByTokenHash(tokenHash)
        .map(e -> new SignupRequestView(e.getId(), e.getEmail(), e.getStatus(), e.getExpiresAt()));
  }

  @Override
  @Transactional
  public void markUsed(Long signupRequestId, LocalDateTime consumedAt) {
    signupRequestRepository.findById(signupRequestId).ifPresent(e -> e.markUsed(consumedAt));
  }
}
