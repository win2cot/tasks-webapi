package xyz.dgz48.tasks.webapi.tenant.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserAlreadyMemberException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantNotFoundException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantSelfOperationException;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteUserUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/**
 * メンバー管理 API(DELETE /api/tenant/users/{userId}・PUT /api/tenant/users/{userId}/role)のスライステスト。
 *
 * <p>テナント暗黙(X-Tenant-Id 駆動)・Tenant Admin 専用・SaaS Admin = 403(#792)を検証する。SaaS Admin は当該パスで {@code
 * TenantContextFilter} がテナント解決に失敗(所属なし)するため 403 となる。
 */
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
  @MockitoBean InviteUserUseCase inviteUserUseCase;
  @MockitoBean RemoveMemberUseCase removeMemberUseCase;
  @MockitoBean ChangeMemberRoleUseCase changeMemberRoleUseCase;

  @Autowired MockMvc mockMvc;

  private static final String TENANT_HEADER = "10";
  private static final Long TENANT_ID = 10L;
  private static final Long USER_ID = 99L;

  private static final TenantUserInfo UPDATED_USER =
      new TenantUserInfo(
          USER_ID,
          "bob@example.com",
          "Bob",
          null,
          TenantRole.TENANT_ADMIN,
          UserTenantStatus.ACTIVE,
          LocalDateTime.of(2026, 1, 1, 0, 0));

  @BeforeEach
  void stubFilterDefaults() {
    // X-Tenant-Id 駆動でメンバーシップを解決させる(ロールはアノテーションの権限と合わせる)
    when(tenantMembershipPort.findActiveRole(anyLong(), eq(TENANT_ID)))
        .thenReturn(Optional.of(TenantRole.TENANT_ADMIN));
    // SaaS Admin(ヘッダなし)は所属テナント 0 件で 403
    when(userTenantsResolverService.resolveInitial(anyLong())).thenReturn(Optional.empty());
  }

  // --- DELETE /api/tenant/users/{userId} ---

  @Test
  @WithMockTenantAdmin
  void removeMember_returns204_whenTenantAdmin() throws Exception {
    doNothing().when(removeMemberUseCase).execute(any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID).header("X-Tenant-Id", TENANT_HEADER))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void removeMember_returns403_whenMember() throws Exception {
    when(tenantMembershipPort.findActiveRole(anyLong(), eq(TENANT_ID)))
        .thenReturn(Optional.of(TenantRole.MEMBER));

    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID).header("X-Tenant-Id", TENANT_HEADER))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void removeMember_returns403_whenSaasAdmin() throws Exception {
    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID))
        .andExpect(status().isForbidden());
  }

  @Test
  void removeMember_returns401_whenUnauthenticated() throws Exception {
    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID).header("X-Tenant-Id", TENANT_HEADER))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockTenantAdmin
  void removeMember_returns404_whenNotFound() throws Exception {
    doThrow(new UserTenantNotFoundException(USER_ID, TENANT_ID))
        .when(removeMemberUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID).header("X-Tenant-Id", TENANT_HEADER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockTenantAdmin
  void removeMember_returns403_whenSelfRemoval() throws Exception {
    doThrow(new UserTenantSelfOperationException())
        .when(removeMemberUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID));

    mockMvc
        .perform(delete("/api/tenant/users/{userId}", USER_ID).header("X-Tenant-Id", TENANT_HEADER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- PUT /api/tenant/users/{userId}/role ---

  @Test
  @WithMockTenantAdmin
  void updateRole_returns200WithUpdatedMember_whenTenantAdmin() throws Exception {
    when(changeMemberRoleUseCase.execute(any(), eq(TENANT_ID), eq(USER_ID), any()))
        .thenReturn(UPDATED_USER);

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", USER_ID)
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(99))
        .andExpect(jsonPath("$.email").value("bob@example.com"))
        .andExpect(jsonPath("$.role").value("TENANT_ADMIN"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @WithMockMember
  void updateRole_returns403_whenMember() throws Exception {
    when(tenantMembershipPort.findActiveRole(anyLong(), eq(TENANT_ID)))
        .thenReturn(Optional.of(TenantRole.MEMBER));

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", USER_ID)
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void updateRole_returns403_whenSaasAdmin() throws Exception {
    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockTenantAdmin
  void updateRole_returns404_whenNotFound() throws Exception {
    doThrow(new UserTenantNotFoundException(USER_ID, TENANT_ID))
        .when(changeMemberRoleUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", USER_ID)
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_NOT_FOUND"));
  }

  @Test
  @WithMockTenantAdmin
  void updateRole_returns403_whenSelfChange() throws Exception {
    doThrow(new UserTenantSelfOperationException())
        .when(changeMemberRoleUseCase)
        .execute(any(), eq(TENANT_ID), eq(USER_ID), any());

    mockMvc
        .perform(
            put("/api/tenant/users/{userId}/role", USER_ID)
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"TENANT_ADMIN\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  // --- POST /api/tenant/users/invite ---

  @Test
  @WithMockTenantAdmin
  void inviteUser_returns201_whenTenantAdmin() throws Exception {
    doNothing().when(inviteUserUseCase).execute(eq(TENANT_ID), any(), eq("new@example.com"), any());

    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockMember
  void inviteUser_returns403_whenMember() throws Exception {
    when(tenantMembershipPort.findActiveRole(anyLong(), eq(TENANT_ID)))
        .thenReturn(Optional.of(TenantRole.MEMBER));

    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockSaasAdmin
  void inviteUser_returns403_whenSaasAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockTenantAdmin
  void inviteUser_returns400_whenInvalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockTenantAdmin
  void inviteUser_returns409_whenAlreadyMember() throws Exception {
    doThrow(new UserAlreadyMemberException("dup@example.com", TENANT_ID))
        .when(inviteUserUseCase)
        .execute(eq(TENANT_ID), any(), eq("dup@example.com"), any());

    mockMvc
        .perform(
            post("/api/tenant/users/invite")
                .header("X-Tenant-Id", TENANT_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dup@example.com\",\"role\":\"MEMBER\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_CONFLICT"));
  }
}
