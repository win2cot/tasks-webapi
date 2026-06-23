package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.usecase.GetMeUseCase;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantSummaryInfo;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(MeController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
})
class MeControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean AuditLogPort auditLogPort;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean GetMeUseCase getMeUseCase;

  @Autowired MockMvc mockMvc;

  @Test
  void unauthenticatedRequest_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwt
  void authenticatedRequest_returnsOkWithUserAndTenants() throws Exception {
    when(getMeUseCase.findTenants(1L))
        .thenReturn(List.of(new TenantSummaryInfo(100L, "acme", "Acme Corp", TenantRole.MEMBER)));

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(1))
        .andExpect(jsonPath("$.user.email").value("test@example.com"))
        .andExpect(jsonPath("$.user.fullName").value("テスト太郎"))
        .andExpect(jsonPath("$.user.fullNameKana").value("テストタロウ"))
        .andExpect(jsonPath("$.user.departmentName").isEmpty())
        .andExpect(jsonPath("$.tenants").isArray())
        .andExpect(jsonPath("$.tenants[0].id").value(100))
        .andExpect(jsonPath("$.tenants[0].code").value("acme"))
        .andExpect(jsonPath("$.tenants[0].name").value("Acme Corp"))
        .andExpect(jsonPath("$.tenants[0].role").value("MEMBER"))
        .andExpect(jsonPath("$.activeTenantId").doesNotExist());
  }

  @Test
  @WithMockJwt
  void multipleTenants_returnedInUseCaseOrder() throws Exception {
    when(getMeUseCase.findTenants(1L))
        .thenReturn(
            List.of(
                new TenantSummaryInfo(10L, "alpha", "Alpha", TenantRole.MEMBER),
                new TenantSummaryInfo(20L, "beta", "Beta", TenantRole.TENANT_ADMIN)));

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenants.length()").value(2))
        .andExpect(jsonPath("$.tenants[0].id").value(10))
        .andExpect(jsonPath("$.tenants[1].id").value(20));
  }

  @Test
  @WithMockJwt
  void withoutTenants_returnsEmptyArray() throws Exception {
    when(getMeUseCase.findTenants(1L)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenants").isEmpty())
        .andExpect(jsonPath("$.activeTenantId").doesNotExist());
  }
}
