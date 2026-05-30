package xyz.dgz48.tasks.webapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * {@code @WebMvcTest} / {@code @AutoConfigureMockMvc} で {@link
 * SecurityMockMvcConfigurers#springSecurity()} を自動適用するテスト用 AutoConfiguration。
 *
 * <p>これにより {@link
 * org.springframework.security.test.context.support.WithSecurityContext}(@WithMockJwt 等)が MockMvc
 * リクエスト処理中に正しく SecurityContext を伝播する。
 *
 * <p>登録先: {@code
 * META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc.imports}
 */
@AutoConfiguration
public class SecurityMockMvcAutoConfiguration {

  @Bean
  MockMvcBuilderCustomizer securityMockMvcBuilderCustomizer() {
    return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
  }
}
