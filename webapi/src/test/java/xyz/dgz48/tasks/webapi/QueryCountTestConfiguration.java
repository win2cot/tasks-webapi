package xyz.dgz48.tasks.webapi;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * N+1 回帰固定用に、オートコンフィグされた {@link DataSource} を datasource-proxy でラップしてクエリ本数を計数する テスト設定(ADR-0039 §3
 * 選択肢 B)。{@code countQuery()} が {@code QueryCountHolder}(ThreadLocal)へ実行クエリを
 * 記録するため、{@code @SpringBootTest}(MOCK, 同一スレッド同期実行)の MockMvc 呼び出し後にテストスレッドから本数を 参照できる。
 *
 * <p>本設定は {@code @Import} した IT のみに適用する(全 IT には効かせない)。既存の datasource-micrometer による DataSource
 * ラップ(ADR-0029)とは BeanPostProcessor チェーン上で共存し、どちらが外側でも全クエリが本 proxy を通過 するため計数に影響しない。
 */
@TestConfiguration(proxyBeanMethods = false)
public class QueryCountTestConfiguration {

  /**
   * オートコンフィグ済み {@link DataSource} を datasource-proxy でラップする。{@code static} にして BPP を早期生成し、
   * 設定クラス本体の初期化に依存させない。
   */
  @Bean
  static BeanPostProcessor queryCountingDataSourcePostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
          return ProxyDataSourceBuilder.create(dataSource)
              .name("query-count-test")
              .countQuery()
              .build();
        }
        return bean;
      }
    };
  }
}
