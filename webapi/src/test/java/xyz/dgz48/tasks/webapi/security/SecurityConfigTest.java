package xyz.dgz48.tasks.webapi.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import xyz.dgz48.tasks.webapi.user.UserRepository;

@WebMvcTest
@Import({SecurityConfig.class, TasksJwtAuthenticationConverter.class})
class SecurityConfigTest {

  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserRepository userRepository;

  @Autowired MockMvc mockMvc;

  @Test
  void unauthenticatedRequestReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequestIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks").with(jwt())).andExpect(status().isNotFound());
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
