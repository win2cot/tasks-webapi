package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.usecase.LogoutUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(LogoutController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
  LogoutExceptionHandler.class
})
class LogoutControllerTest {

  private static final String ID_TOKEN_HINT = "eyJhbGciOiJSUzI1NiJ9.test";
  private static final String POST_LOGOUT_REDIRECT_URI = "https://app.example.com/";
  private static final String END_SESSION_URL =
      "http://keycloak:8080/realms/tasks/protocol/openid-connect/logout"
          + "?id_token_hint=eyJhbGciOiJSUzI1NiJ9.test"
          + "&post_logout_redirect_uri=https%3A%2F%2Fapp.example.com%2F";

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean AuditLogPort auditLogPort;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean LogoutUseCase logoutUseCase;

  @Autowired MockMvc mockMvc;

  @Test
  void unauthenticatedRequest_returnsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwt
  void authenticatedRequest_returnsOkWithEndSessionUrl() throws Exception {
    when(logoutUseCase.buildEndSessionUrl(ID_TOKEN_HINT, POST_LOGOUT_REDIRECT_URI))
        .thenReturn(END_SESSION_URL);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", ID_TOKEN_HINT)
                .param("postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endSessionUrl").value(END_SESSION_URL));
  }

  @Test
  @WithMockJwt
  void blankIdTokenHint_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", "")
                .param("postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwt
  void blankPostLogoutRedirectUri_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", ID_TOKEN_HINT)
                .param("postLogoutRedirectUri", ""))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockMember
  void memberCanLogout() throws Exception {
    when(logoutUseCase.buildEndSessionUrl(anyString(), anyString())).thenReturn(END_SESSION_URL);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", ID_TOKEN_HINT)
                .param("postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockTenantAdmin
  void tenantAdminCanLogout() throws Exception {
    when(logoutUseCase.buildEndSessionUrl(anyString(), anyString())).thenReturn(END_SESSION_URL);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", ID_TOKEN_HINT)
                .param("postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockSaasAdmin
  void saasAdminCanLogout() throws Exception {
    when(logoutUseCase.buildEndSessionUrl(anyString(), anyString())).thenReturn(END_SESSION_URL);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .param("idTokenHint", ID_TOKEN_HINT)
                .param("postLogoutRedirectUri", POST_LOGOUT_REDIRECT_URI))
        .andExpect(status().isOk());
  }
}
