package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCrossBoundaryException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantAlreadyExistsException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;
import xyz.dgz48.tasks.webapi.tenant.usecase.AddMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest(TenantMemberController.class)
@Import({
  SecurityConfig.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
  TenantMemberExceptionHandler.class
})
class TenantMemberControllerWebMvcTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean AuditLogPort auditLogPort;
  @MockitoBean AuthorizationDeniedAuditService authorizationDeniedAuditService;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean AddMemberUseCase addMemberUseCase;
  @MockitoBean RemoveMemberUseCase removeMemberUseCase;
  @MockitoBean ChangeMemberRoleUseCase changeMemberRoleUseCase;

  @Autowired MockMvc mockMvc;

  private static final Long TENANT_ID = 10L;
  private static final Long USER_ID = 99L;

  // --- POST /api/tenants/{tenantId}/users ---

  @Test
  @WithMockSaasAdmin
  void addMember_returns201_whenSaasAdmin() throws Exception {
    doNothing().when(addMemberUseCase).execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockTenantAdmin
  void addMember_returns201_whenTenantAdmin() throws Exception {
    doNothing().when(addMemberUseCase).execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockMember
  void addMember_returns403_whenMember() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void addMember_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockSaasAdmin
  void addMember_returns409_whenAlreadyExists() throws Exception {
    doThrow(new UserTenantAlreadyExistsException(USER_ID, TENANT_ID))
        .when(addMemberUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));
  }

  @Test
  @WithMockTenantAdmin
  void addMember_returns403_whenCrossTenant() throws Exception {
    doThrow(new TenantCrossBoundaryException(TENANT_ID))
        .when(addMemberUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            post("/api/tenants/{tenantId}/users", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":99,\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- DELETE /api/tenants/{tenantId}/users/{userId} ---

  @Test
  @WithMockSaasAdmin
  void removeMember_returns204_whenSaasAdmin() throws Exception {
    doNothing().when(removeMemberUseCase).execute(any(), any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void removeMember_returns403_whenMember() throws Exception {
    mockMvc
        .perform(delete("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void removeMember_returns404_whenNotFound() throws Exception {
    doThrow(new UserTenantNotFoundException(USER_ID, TENANT_ID))
        .when(removeMemberUseCase)
        .execute(any(), any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockSaasAdmin
  void removeMember_returns403_whenSelfRemoval() throws Exception {
    doThrow(new UserTenantSelfOperationException())
        .when(removeMemberUseCase)
        .execute(any(), any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- PATCH /api/tenants/{tenantId}/users/{userId} ---

  @Test
  @WithMockSaasAdmin
  void changeMemberRole_returns204_whenSaasAdmin() throws Exception {
    doNothing()
        .when(changeMemberRoleUseCase)
        .execute(any(), any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void changeMemberRole_returns403_whenMember() throws Exception {
    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void changeMemberRole_returns404_whenNotFound() throws Exception {
    doThrow(new UserTenantNotFoundException(USER_ID, TENANT_ID))
        .when(changeMemberRoleUseCase)
        .execute(any(), any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockSaasAdmin
  void changeMemberRole_returns403_whenSelfChange() throws Exception {
    doThrow(new UserTenantSelfOperationException())
        .when(changeMemberRoleUseCase)
        .execute(any(), any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            patch("/api/tenants/{tenantId}/users/{userId}", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }
}
