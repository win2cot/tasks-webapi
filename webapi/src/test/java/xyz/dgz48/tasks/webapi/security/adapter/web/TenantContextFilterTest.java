package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.security.adapter.persistence.AppAdminUserRepository;
import xyz.dgz48.tasks.webapi.security.usecase.LogoutUseCase;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.task.usecase.AddStakeholderUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeVisibilityUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.CreateTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.DeleteTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListStakeholdersUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.ListTasksUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.RemoveStakeholderUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.UpdateTaskUseCase;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.AddMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.ChangeMemberRoleUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.RemoveMemberUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.SwitchTenantUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest
@Import({
  SecurityConfig.class,
  TenantContextFilter.class,
  TasksJwtAuthenticationConverter.class,
  TasksAuthenticationEntryPoint.class,
  TasksAccessDeniedHandler.class,
  TenantContextFilterTest.ProbeController.class
})
class TenantContextFilterTest {

  @RestController
  static class ProbeController {

    @GetMapping("/probe")
    ResponseEntity<Long> probe() {
      Long tenantId = TenantContext.get();
      if (tenantId == null) {
        return ResponseEntity.noContent().build();
      }
      return ResponseEntity.ok(tenantId);
    }

    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @GetMapping("/probe/tenant-admin-only")
    ResponseEntity<Void> tenantAdminOnly() {
      return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/probe/member-only")
    ResponseEntity<Void> memberOnly() {
      return ResponseEntity.ok().build();
    }
  }

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean AppAdminUserRepository appAdminUserRepository;
  @MockitoBean LogoutUseCase logoutUseCase;
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean UserTenantsResolverService userTenantsResolverService;
  @MockitoBean CreateTaskUseCase createTaskUseCase;
  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean ChangeTaskStatusUseCase changeTaskStatusUseCase;
  @MockitoBean UpdateTaskUseCase updateTaskUseCase;
  @MockitoBean DeleteTaskUseCase deleteTaskUseCase;
  @MockitoBean ChangeVisibilityUseCase changeVisibilityUseCase;
  @MockitoBean ListTasksUseCase listTasksUseCase;
  @MockitoBean ListStakeholdersUseCase listStakeholdersUseCase;
  @MockitoBean AddStakeholderUseCase addStakeholderUseCase;
  @MockitoBean RemoveStakeholderUseCase removeStakeholderUseCase;
  @MockitoBean SwitchTenantUseCase switchTenantUseCase;
  @MockitoBean AddMemberUseCase addMemberUseCase;
  @MockitoBean RemoveMemberUseCase removeMemberUseCase;
  @MockitoBean ChangeMemberRoleUseCase changeMemberRoleUseCase;

  @Autowired MockMvc mockMvc;

  @Test
  @WithMockUser
  void noTenantIdHeader_nonTasksAuth_passesThrough_contextNotSet() throws Exception {
    mockMvc.perform(get("/probe")).andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void invalidTenantIdHeader_returnsBadRequestWithErrorResponse() throws Exception {
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("E_VALIDATION"));
  }

  @Test
  @WithMockUser
  void nonTasksAuthentication_returnsUnauthorizedWithErrorResponse() throws Exception {
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"));
  }

  @Test
  @WithMockMember
  void nonMember_returnsForbiddenWithErrorResponse() throws Exception {
    given(tenantMembershipPort.findActiveRole(1L, 1L)).willReturn(Optional.empty());
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  @WithMockJwt(roles = {})
  void insufficientRole_returnsForbiddenWithErrorResponse() throws Exception {
    given(tenantMembershipPort.findActiveRole(1L, 1L)).willReturn(Optional.of(TenantRole.MEMBER));
    mockMvc
        .perform(
            get("/probe/tenant-admin-only").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  @WithMockMember
  void activeMember_setsTenantContextAndPassesThrough() throws Exception {
    given(tenantMembershipPort.findActiveRole(1L, 1L)).willReturn(Optional.of(TenantRole.MEMBER));
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isOk())
        .andExpect(content().string("1"));
  }

  @Test
  @WithMockJwt(roles = {})
  void memberRole_grantsMemberAuthority() throws Exception {
    given(tenantMembershipPort.findActiveRole(1L, 1L)).willReturn(Optional.of(TenantRole.MEMBER));
    mockMvc
        .perform(get("/probe/member-only").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockJwt(roles = {})
  void tenantAdminRole_grantsTenantAdminAuthority() throws Exception {
    given(tenantMembershipPort.findActiveRole(1L, 1L))
        .willReturn(Optional.of(TenantRole.TENANT_ADMIN));
    mockMvc
        .perform(
            get("/probe/tenant-admin-only").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockJwt(roles = {})
  void saasAdminRole_throwsIllegalArgumentException() {
    given(tenantMembershipPort.findActiveRole(1L, 1L))
        .willReturn(Optional.of(TenantRole.SAAS_ADMIN));
    assertThatThrownBy(
            () ->
                mockMvc.perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // --- fallback (X-Tenant-Id ヘッダなし) ---

  @Test
  @WithMockMember
  void noHeader_noMemberships_returnsForbidden() throws Exception {
    given(userTenantsResolverService.resolveInitial(1L)).willReturn(Optional.empty());
    mockMvc
        .perform(get("/probe"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  @WithMockMember
  void noHeader_hasMembership_setsTenantContextAndPassesThrough() throws Exception {
    given(userTenantsResolverService.resolveInitial(1L))
        .willReturn(Optional.of(new TenantMembership(5L, TenantRole.MEMBER)));
    mockMvc.perform(get("/probe")).andExpect(status().isOk()).andExpect(content().string("5"));
  }

  @Test
  @WithMockJwt(roles = {})
  void noHeader_hasMembership_grantsMemberAuthority() throws Exception {
    given(userTenantsResolverService.resolveInitial(1L))
        .willReturn(Optional.of(new TenantMembership(5L, TenantRole.MEMBER)));
    mockMvc.perform(get("/probe/member-only")).andExpect(status().isOk());
  }

  @Test
  @WithMockMember
  void noHeader_exemptAuthPath_resolverNotCalled() throws Exception {
    mockMvc.perform(get("/api/auth/tenants/1/select"));
    org.mockito.Mockito.verify(userTenantsResolverService, org.mockito.Mockito.never())
        .resolveInitial(org.mockito.ArgumentMatchers.anyLong());
  }
}
