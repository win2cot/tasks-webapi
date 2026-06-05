package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.task.usecase.ChangeTaskStatusUseCase;
import xyz.dgz48.tasks.webapi.task.usecase.GetTaskUseCase;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
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
  @MockitoBean TenantMembershipPort tenantMembershipPort;
  @MockitoBean GetTaskUseCase getTaskUseCase;
  @MockitoBean ChangeTaskStatusUseCase changeTaskStatusUseCase;

  @Autowired MockMvc mockMvc;

  @Test
  void unauthenticatedRequestReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/tasks"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
        .perform(
            get("/api/tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired.mock.token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("E_UNAUTHORIZED"))
        .andExpect(jsonPath("$.error").value("Unauthorized"));
  }

  @Test
  @WithMockJwt
  void authenticatedRequestIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockMember
  void memberIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockTenantAdmin
  void tenantAdminIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockSaasAdmin
  void saasAdminIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isNotFound());
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
}
