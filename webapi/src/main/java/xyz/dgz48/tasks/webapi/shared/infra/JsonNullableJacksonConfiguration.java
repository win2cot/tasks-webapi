package xyz.dgz48.tasks.webapi.shared.infra;

import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JsonNullable Jackson 3 モジュール設定(ADR-0014)。 */
@Configuration
public class JsonNullableJacksonConfiguration {

  @Bean
  public JsonNullableJackson3Module jsonNullableModule() {
    return new JsonNullableJackson3Module();
  }
}
