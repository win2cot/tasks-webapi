package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.audit.usecase.AuthorizationDeniedAuditService;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAccessDeniedHandler;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationEntryPoint;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockSaasAdmin;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockTenantAdmin;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.ListTenantUsersUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(TenantUserController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
})
class TenantUserControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean AuditLogPort auditLogPort;
  @MockitoBean AuthorizationDeniedAuditService authorizationDeniedAuditService;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean ListTenantUsersUseCase listTenantUsersUseCase;

  @Autowired MockMvc mockMvc;

  private static final TenantUserInfo SAMPLE_USER =
      new TenantUserInfo(
          1L,
          "alice@example.com",
          "Alice",
          null,
          TenantRole.MEMBER,
          UserTenantStatus.ACTIVE,
          LocalDateTime.of(2026, 1, 1, 0, 0));

  @BeforeEach
  void stubDefaults() {
    when(tenantMembershipPort.findActiveRole(anyLong(), anyLong()))
        .thenReturn(Optional.of(TenantRole.MEMBER));
    when(userTenantsResolverService.resolveInitial(anyLong())).thenReturn(Optional.empty());
  }

  @Test
  @WithMockMember
  void listTenantUsers_returns200_withTenantContext() throws Exception {
    when(listTenantUsersUseCase.execute(anyLong())).thenReturn(List.of(SAMPLE_USER));

    mockMvc
        .perform(get("/api/tenant/users").header("X-Tenant-Id", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(1))
        .andExpect(jsonPath("$[0].email").value("alice@example.com"))
        .andExpect(jsonPath("$[0].role").value("MEMBER"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  @WithMockTenantAdmin
  void listTenantUsers_returns200_whenTenantAdmin() throws Exception {
    when(listTenantUsersUseCase.execute(anyLong())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/tenant/users").header("X-Tenant-Id", "10"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockSaasAdmin
  void listTenantUsers_returns403_whenSaasAdmin() throws Exception {
    mockMvc.perform(get("/api/tenant/users")).andExpect(status().isForbidden());
  }

  @Test
  void listTenantUsers_returns401_whenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/tenant/users")).andExpect(status().isUnauthorized());
  }
}
