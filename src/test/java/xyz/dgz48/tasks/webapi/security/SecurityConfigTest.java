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

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @MockitoBean JwtDecoder jwtDecoder;

  @Autowired MockMvc mockMvc;

  @Test
  void unauthenticatedRequestReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequestIsAllowed() throws Exception {
    mockMvc.perform(get("/api/tasks").with(jwt())).andExpect(status().isNotFound());
  }
}
