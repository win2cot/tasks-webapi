package xyz.dgz48.tasks.webapi.security.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class OidcSubCorrelationServiceTest {

  @Mock UserRepository userRepository;
  @Mock UserJpaEntity user;

  @InjectMocks OidcSubCorrelationService service;

  @Test
  void returnsUserDirectlyWhenSubAlreadyCorrelated() {
    when(userRepository.findByOidcSub("sub-real")).thenReturn(Optional.of(user));

    Optional<UserJpaEntity> result = service.resolve("sub-real", "user@example.com");

    assertThat(result).containsSame(user);
    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void correlatesPendingRowByEmailOnFirstLogin() {
    when(userRepository.findByOidcSub("sub-real")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("corr@example.com")).thenReturn(Optional.of(user));
    when(user.isPendingCorrelation()).thenReturn(true);
    when(userRepository.saveAndFlush(user)).thenReturn(user);

    Optional<UserJpaEntity> result = service.resolve("sub-real", "corr@example.com");

    assertThat(result).containsSame(user);
    verify(user).correlateOidcSub("sub-real");
    verify(userRepository).saveAndFlush(user);
  }

  @Test
  void returnsEmptyWhenEmailClaimMissing() {
    when(userRepository.findByOidcSub("sub-x")).thenReturn(Optional.empty());

    assertThat(service.resolve("sub-x", null)).isEmpty();
    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void returnsEmptyWhenEmailClaimBlank() {
    when(userRepository.findByOidcSub("sub-x")).thenReturn(Optional.empty());

    assertThat(service.resolve("sub-x", "   ")).isEmpty();
    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void returnsEmptyWhenNoRowMatchesEmail() {
    when(userRepository.findByOidcSub("sub-x")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("orphan@example.com")).thenReturn(Optional.empty());

    assertThat(service.resolve("sub-x", "orphan@example.com")).isEmpty();
    verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void doesNotCorrelateWhenEmailMatchesAlreadyCorrelatedRow() {
    // email は一致するが pending でない(別 Keycloak アカウントの sub に紐付く)行は突合しない(なりすまし防止)。
    when(userRepository.findByOidcSub("sub-attacker")).thenReturn(Optional.empty());
    when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(user));
    when(user.isPendingCorrelation()).thenReturn(false);

    assertThat(service.resolve("sub-attacker", "victim@example.com")).isEmpty();
    verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void resolvesBySubOnOptimisticLockConflict() {
    // 並行初回ログイン: 競合相手が先に同じ sub へ correlation 済み → saveAndFlush が失敗 → sub 再 lookup で解決。
    UserJpaEntity correlatedByPeer = org.mockito.Mockito.mock(UserJpaEntity.class);
    when(userRepository.findByOidcSub("sub-real"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(correlatedByPeer));
    when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.of(user));
    when(user.isPendingCorrelation()).thenReturn(true);
    when(userRepository.saveAndFlush(user))
        .thenThrow(new OptimisticLockingFailureException("conflict"));

    Optional<UserJpaEntity> result = service.resolve("sub-real", "race@example.com");

    assertThat(result).containsSame(correlatedByPeer);
  }
}
