package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

@WebMvcTest
@Import({
  SecurityConfig.class,
  TenantContextFilter.class,
  TasksJwtAuthenticationConverter.class,
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
  }

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;
  @MockitoBean TenantMembershipPort tenantMembershipPort;

  @Autowired MockMvc mockMvc;

  @Test
  @WithMockMember
  void noTenantIdHeader_passesThrough_contextNotSet() throws Exception {
    mockMvc.perform(get("/probe")).andExpect(status().isNoContent());
  }

  @Test
  @WithMockMember
  void invalidTenantIdHeader_returnsBadRequest() throws Exception {
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "not-a-number"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void nonTasksAuthentication_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockMember
  void inactiveMember_returnsForbidden() throws Exception {
    given(tenantMembershipPort.isActiveMember(1L, 1L)).willReturn(false);
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockMember
  void activeMember_setsTenantContextAndPassesThrough() throws Exception {
    given(tenantMembershipPort.isActiveMember(1L, 1L)).willReturn(true);
    mockMvc
        .perform(get("/probe").header(TenantContextFilter.HEADER_X_TENANT_ID, "1"))
        .andExpect(status().isOk())
        .andExpect(content().string("1"));
  }
}
