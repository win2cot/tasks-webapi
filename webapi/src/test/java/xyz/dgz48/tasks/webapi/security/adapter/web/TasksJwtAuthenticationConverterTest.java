package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class TasksJwtAuthenticationConverterTest {

  @Mock UserRepository userRepository;
  @Mock AppAdminUserRepository appAdminUserRepository;
  @Mock Jwt jwt;
  @Mock UserJpaEntity user;

  @InjectMocks TasksJwtAuthenticationConverter converter;

  private void stubValidUser() {
    when(jwt.getSubject()).thenReturn("sub123");
    when(userRepository.findByOidcSub("sub123")).thenReturn(Optional.of(user));
    when(user.isAnonymized()).thenReturn(false);
    when(user.isInactive()).thenReturn(false);
    when(user.getId()).thenReturn(1L);
    when(user.getOidcSub()).thenReturn("sub123");
    when(user.getEmail()).thenReturn("user@example.com");
    when(user.getFullName()).thenReturn("山田太郎");
    when(user.getFullNameKana()).thenReturn("ヤマダタロウ");
    when(user.getDepartmentName()).thenReturn("開発部");
  }

  @Test
  void convertsJwtToAuthenticationToken() {
    stubValidUser();
    when(appAdminUserRepository.existsByOidcSub("sub123")).thenReturn(false);

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token).isInstanceOf(TasksAuthenticationToken.class);
    assertThat(token.isAuthenticated()).isTrue();
    TasksPrincipal principal = (TasksPrincipal) token.getPrincipal();
    assertThat(principal.getId()).isEqualTo(1L);
    assertThat(principal.getSub()).isEqualTo("sub123");
    assertThat(principal.getEmail()).isEqualTo("user@example.com");
    assertThat(principal.getFullName()).isEqualTo("山田太郎");
    assertThat(principal.getDepartmentName()).isEqualTo("開発部");
    assertThat(token.getAuthorities()).isEmpty();
  }

  @Test
  void grantsAppAdminWhenSubExistsInAppAdminUsers() {
    stubValidUser();
    when(appAdminUserRepository.existsByOidcSub("sub123")).thenReturn(true);

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getAuthorities()).extracting("authority").containsExactly("ROLE_APP_ADMIN");
  }

  @Test
  void noAuthoritiesWhenSubNotInAppAdminUsers() {
    stubValidUser();
    when(appAdminUserRepository.existsByOidcSub("sub123")).thenReturn(false);

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getAuthorities()).isEmpty();
  }

  @Test
  void throwsUserNotRegisteredWhenUserNotFound() {
    when(jwt.getSubject()).thenReturn("unknown-sub");
    when(userRepository.findByOidcSub("unknown-sub")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(UserNotRegisteredException.class);
  }

  @Test
  void correlatesPendingRowByEmailOnFirstLogin() {
    // 初回ログイン: 本物の sub では未ヒット → email クレームで pending placeholder 行を突合し sub を書き戻す。
    when(jwt.getSubject()).thenReturn("kc-sub-real");
    when(userRepository.findByOidcSub("kc-sub-real")).thenReturn(Optional.empty());
    when(jwt.getClaimAsString("email")).thenReturn("corr@example.com");
    when(userRepository.findByEmail("corr@example.com")).thenReturn(Optional.of(user));
    when(user.isPendingCorrelation()).thenReturn(true);
    when(userRepository.save(user)).thenReturn(user);
    when(user.isAnonymized()).thenReturn(false);
    when(user.isInactive()).thenReturn(false);
    when(user.getId()).thenReturn(7L);
    when(user.getOidcSub()).thenReturn("kc-sub-real");
    when(user.getEmail()).thenReturn("corr@example.com");
    when(user.getFullName()).thenReturn("田中一郎");
    when(user.getFullNameKana()).thenReturn("タナカイチロウ");
    when(user.getDepartmentName()).thenReturn(null);
    when(appAdminUserRepository.existsByOidcSub("kc-sub-real")).thenReturn(false);

    AbstractAuthenticationToken token = converter.convert(jwt);

    verify(user).correlateOidcSub("kc-sub-real");
    verify(userRepository).save(user);
    TasksPrincipal principal = (TasksPrincipal) token.getPrincipal();
    assertThat(principal.getId()).isEqualTo(7L);
    assertThat(principal.getSub()).isEqualTo("kc-sub-real");
  }

  @Test
  void doesNotCorrelateWhenEmailMatchesAlreadyCorrelatedRow() {
    // email は一致するが pending でない(別 Keycloak アカウントの sub に紐付く)行はなりすまし防止のため突合しない。
    when(jwt.getSubject()).thenReturn("kc-sub-attacker");
    when(userRepository.findByOidcSub("kc-sub-attacker")).thenReturn(Optional.empty());
    when(jwt.getClaimAsString("email")).thenReturn("victim@example.com");
    when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(user));
    when(user.isPendingCorrelation()).thenReturn(false);

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(UserNotRegisteredException.class);
    verify(userRepository, never()).save(user);
  }

  @Test
  void doesNotCorrelateWhenEmailClaimMissing() {
    when(jwt.getSubject()).thenReturn("kc-sub-noemail");
    when(userRepository.findByOidcSub("kc-sub-noemail")).thenReturn(Optional.empty());
    when(jwt.getClaimAsString("email")).thenReturn(null);

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(UserNotRegisteredException.class);
    verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void doesNotCorrelateWhenNoRowMatchesEmail() {
    when(jwt.getSubject()).thenReturn("kc-sub-orphan");
    when(userRepository.findByOidcSub("kc-sub-orphan")).thenReturn(Optional.empty());
    when(jwt.getClaimAsString("email")).thenReturn("orphan@example.com");
    when(userRepository.findByEmail("orphan@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(UserNotRegisteredException.class);
    verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void throwsWhenSubClaimMissing() {
    when(jwt.getSubject()).thenReturn(null);

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void throwsUserAnonymizedWhenDeletedAtIsSet() {
    when(jwt.getSubject()).thenReturn("sub-anon");
    when(userRepository.findByOidcSub("sub-anon")).thenReturn(Optional.of(user));
    when(user.isAnonymized()).thenReturn(true);

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(UserAnonymizedException.class)
        .hasMessage("ユーザーアカウントは削除済みです");
  }

  @Test
  void throwsUserInactiveWhenStatusIsInactive() {
    when(jwt.getSubject()).thenReturn("sub-inactive");
    when(userRepository.findByOidcSub("sub-inactive")).thenReturn(Optional.of(user));
    when(user.isAnonymized()).thenReturn(false);
    when(user.isInactive()).thenReturn(true);

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(UserInactiveException.class)
        .hasMessage("ユーザーアカウントが無効化されています");
  }

  @Test
  void anonymizedCheckTakesPriorityOverInactiveCheck() {
    when(jwt.getSubject()).thenReturn("sub-both");
    when(userRepository.findByOidcSub("sub-both")).thenReturn(Optional.of(user));
    when(user.isAnonymized()).thenReturn(true);

    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(UserAnonymizedException.class);
  }
}
