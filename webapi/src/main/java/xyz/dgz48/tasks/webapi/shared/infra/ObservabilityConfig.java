package xyz.dgz48.tasks.webapi.shared.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Micrometer Observation / トレース伝播設定(ADR-0029 §6.1)。
 *
 * <p>{@link ContextPropagatingTaskDecorator} を Bean 登録することで、Spring Boot の {@code
 * ThreadPoolTaskExecutorBuilder} が自動的にこのデコレータを適用し、{@code @Async} スレッドプール全体で Micrometer
 * トレースコンテキストを伝播させる。
 */
@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {

  @Bean
  ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
    return new ContextPropagatingTaskDecorator();
  }
}
