package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.usecase.LogoutUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.AddStakeholderUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListStakeholdersUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListTasksUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.RemoveStakeholderUseCase;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.AddMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.SwitchTenantUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class
})
class SecurityConfigTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean LogoutUseCase logoutUseCase;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean ChangeTaskStatusUseCase changeTaskStatusUseCase;
  @MockitoBean ListTasksUseCase listTasksUseCase;
  @MockitoBean ListStakeholdersUseCase listStakeholdersUseCase;
  @MockitoBean AddStakeholderUseCase addStakeholderUseCase;
  @MockitoBean RemoveStakeholderUseCase removeStakeholderUseCase;
  @MockitoBean SwitchTenantUseCase switchTenantUseCase;
  @MockitoBean AddMemberUseCase addMemberUseCase;
  @MockitoBean RemoveMemberUseCase removeMemberUseCase;
  @MockitoBean ChangeMemberRoleUseCase changeMemberRoleUseCase;

  @Autowired MockMvc mockMvc;

  @BeforeEach
  void stubTenantResolver() {
    given(userTenantsResolverService.resolveInitial(ArgumentMatchers.anyLong()))
        .willReturn(Optional.of(new TenantMembership(1L, TenantRole.MEMBER)));
    given(listTasksUseCase.execute(any(), any(), any(), any(), any(), any()))
        .willReturn(new ListTasksUseCase.Result(Page.empty(PageRequest.of(0, 50)), 0));
  }

  @Test
  void unauthenticatedRequestReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/tasks"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  void expiredTokenReturns401WithErrorResponse() throws Exception {
    given(jwtDecoder.decode(any()))
        .willThrow(
            new JwtValidationException(
                "JWT token expired", List.of(new OAuth2Error("invalid_token"))));
    mockMvc
        .perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer expired.mock.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  @WithMockJwt
  void authenticatedRequestIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
  }

  @Test
  @WithMockMember
  void memberIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
  }

  @Test
  @WithMockTenantAdmin
  void tenantAdminIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
  }

  @Test
  @WithMockSaasAdmin
  void saasAdminIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isOk());
  }

  @Test
  void actuatorHealthIsPubliclyAccessible() throws Exception {
    // Actuator endpoints are not registered in @WebMvcTest slice; 404 confirms security allows
    // through
    mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
  }

  @Test
  void actuatorInfoIsPubliclyAccessible() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
  }

  @Test
  void expiredAccessTokenReturnsUnauthorized() throws Exception {
    when(jwtDecoder.decode(anyString()))
        .thenThrow(
            new JwtValidationException(
                "JWT expired",
                List.of(new OAuth2Error("invalid_token", "The JWT has expired", null))));

    mockMvc
        .perform(get("/api/tasks").header("Authorization", "Bearer expired.token.here"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void malformedTokenReturnsUnauthorized() throws Exception {
    when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Malformed JWT"));

    mockMvc
        .perform(get("/api/tasks").header("Authorization", "Bearer malformed.token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void userNotRegisteredReturns401() throws Exception {
    given(jwtDecoder.decode(any())).willReturn(buildMockJwt("unregistered-sub"));
    given(userRepository.findByOidcSub("unregistered-sub")).willReturn(Optional.empty());

    mockMvc
        .perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer mock.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void inactiveUserReturns401() throws Exception {
    UserJpaEntity inactiveUser = Mockito.mock(UserJpaEntity.class);
    Mockito.when(inactiveUser.isAnonymized()).thenReturn(false);
    Mockito.when(inactiveUser.isInactive()).thenReturn(true);

    given(jwtDecoder.decode(any())).willReturn(buildMockJwt("inactive-sub"));
    given(userRepository.findByOidcSub("inactive-sub")).willReturn(Optional.of(inactiveUser));

    mockMvc
        .perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer mock.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  @Test
  void anonymizedUserReturns401() throws Exception {
    UserJpaEntity anonymizedUser = Mockito.mock(UserJpaEntity.class);
    Mockito.when(anonymizedUser.isAnonymized()).thenReturn(true);

    given(jwtDecoder.decode(any())).willReturn(buildMockJwt("anonymized-sub"));
    given(userRepository.findByOidcSub("anonymized-sub")).willReturn(Optional.of(anonymizedUser));

    mockMvc
        .perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer mock.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("認証が必要です"));
  }

  private static Jwt buildMockJwt(String sub) {
    return new Jwt(
        "mock-token-value",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "RS256"),
        Map.of("sub", sub));
  }
}
