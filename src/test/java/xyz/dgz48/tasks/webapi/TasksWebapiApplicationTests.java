package xyz.dgz48.tasks.webapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TasksWebapiApplicationTests {

  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void contextLoads() {}
}
