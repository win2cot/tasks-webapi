package xyz.dgz48.tasks.webapi.shared.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.exception.SaasAdminRequiredException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantFilterBypassServiceTest {

  @Mock EntityManager entityManager;
  @Mock Session session;
  @Mock Filter hibernateFilter;

  TenantFilterBypassService service;

  @BeforeEach
  void setUp() {
    service = new TenantFilterBypassService(entityManager);
    when(entityManager.unwrap(Session.class)).thenReturn(session);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void runAsSaaSAdmin_whenSaasAdmin_executesAction() {
    setUpSaasAdmin();

    String result = service.runAsSaaSAdmin(() -> "result");

    assertThat(result).isEqualTo("result");
    verify(session).disableFilter("tenantFilter");
  }

  @Test
  void runAsSaaSAdmin_whenNotAuthenticated_throwsSaasAdminRequiredException() {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(() -> service.runAsSaaSAdmin(() -> "result"))
        .isInstanceOf(SaasAdminRequiredException.class)
        .hasMessageContaining("SaaS Admin");

    verifyNoInteractions(session);
  }

  @Test
  void runAsSaaSAdmin_whenMember_throwsSaasAdminRequiredException() {
    setUpMember();

    assertThatThrownBy(() -> service.runAsSaaSAdmin(() -> "result"))
        .isInstanceOf(SaasAdminRequiredException.class);

    verifyNoInteractions(session);
  }

  @Test
  void runAsSaaSAdmin_whenTenantContextSet_restoresFilterAfterExecution() {
    setUpSaasAdmin();
    TenantContext.set(42L);
    when(session.enableFilter(anyString())).thenReturn(hibernateFilter);

    service.runAsSaaSAdmin(() -> "ok");

    verify(session).disableFilter("tenantFilter");
    verify(session).enableFilter("tenantFilter");
    verify(hibernateFilter).setParameter("tenantId", 42L);
  }

  @Test
  void runAsSaaSAdmin_whenTenantContextNotSet_doesNotRestoreFilter() {
    setUpSaasAdmin();

    service.runAsSaaSAdmin(() -> "ok");

    verify(session).disableFilter("tenantFilter");
    verify(session, never()).enableFilter(anyString());
  }

  @Test
  void runAsSaaSAdmin_restoresFilterEvenWhenActionThrows() {
    setUpSaasAdmin();
    TenantContext.set(42L);
    when(session.enableFilter(anyString())).thenReturn(hibernateFilter);

    assertThatThrownBy(
            () ->
                service.runAsSaaSAdmin(
                    () -> {
                      throw new RuntimeException("action failed");
                    }))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("action failed");

    verify(session).disableFilter("tenantFilter");
    verify(session).enableFilter("tenantFilter");
    verify(hibernateFilter).setParameter("tenantId", 42L);
  }

  @Test
  void runAsSaaSAdmin_whenTenantContextNotSetAndActionThrows_doesNotRestoreFilter() {
    setUpSaasAdmin();

    assertThatThrownBy(
            () ->
                service.runAsSaaSAdmin(
                    () -> {
                      throw new RuntimeException("action failed");
                    }))
        .isInstanceOf(RuntimeException.class);

    verify(session).disableFilter("tenantFilter");
    verify(session, never()).enableFilter(anyString());
  }

  private void setUpSaasAdmin() {
    var principal = new TasksPrincipal(1L, "admin-sub", "admin@example.com", "管理者", "カンリシャ", null);
    var auth =
        new TasksAuthenticationToken(
            principal, List.of(new SimpleGrantedAuthority("ROLE_APP_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private void setUpMember() {
    var principal =
        new TasksPrincipal(2L, "member-sub", "member@example.com", "メンバー", "メンバー", null);
    var auth =
        new TasksAuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
