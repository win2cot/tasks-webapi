package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.adapter.web.SecurityConfig;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAccessDeniedHandler;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationEntryPoint;
import xyz.dgz48.tasks.webapi.security.adapter.web.TasksJwtAuthenticationConverter;
import xyz.dgz48.tasks.webapi.security.adapter.web.WithMockMember;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantNotMemberException;
import xyz.dgz48.tasks.webapi.tenant.usecase.SwitchTenantUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(SelectTenantController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
  SelectTenantExceptionHandler.class
})
class SelectTenantControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean SwitchTenantUseCase switchTenantUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long USER_ID = 1L;
  private static final Long TENANT_ID = 100L;

  @Test
  @WithMockMember
  void selectTenant_returns204_whenMember() throws Exception {
    doNothing().when(switchTenantUseCase).execute(USER_ID, TENANT_ID);

    mockMvc
        .perform(post("/api/auth/tenants/{tenantId}/select", TENANT_ID))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void selectTenant_returns403_whenNotMember() throws Exception {
    doThrow(new TenantNotMemberException(TENANT_ID))
        .when(switchTenantUseCase)
        .execute(USER_ID, TENANT_ID);

    mockMvc
        .perform(post("/api/auth/tenants/{tenantId}/select", TENANT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  @WithMockMember
  void selectTenant_returns400_whenTenantIdIsNotNumeric() throws Exception {
    mockMvc.perform(post("/api/auth/tenants/abc/select")).andExpect(status().isBadRequest());
  }

  @Test
  void selectTenant_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(post("/api/auth/tenants/{tenantId}/select", TENANT_ID))
        .andExpect(status().isUnauthorized());
  }
}
