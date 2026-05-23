package xyz.dgz48.tasks.webapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class TasksWebapiApplicationTests {

  @Test
  void contextLoads() {}
}
