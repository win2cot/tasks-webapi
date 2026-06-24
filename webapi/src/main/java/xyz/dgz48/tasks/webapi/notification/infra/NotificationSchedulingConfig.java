package xyz.dgz48.tasks.webapi.notification.infra;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * スケジュールバッチと ShedLock による多重起動排他制御を有効化する(設計規約 §7、基本設計書 §4.2.8 / §8.2)。
 *
 * <p>{@link JdbcTemplateLockProvider} は既存の {@code shedlock} テーブルを使用し、{@code usingDbTime()} で DB
 * 時刻基準の ロック判定を行う(複数 ECS Fargate タスク間のクロックスキューに依存しない)。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class NotificationSchedulingConfig {

  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName("shedlock")
            .usingDbTime()
            .build());
  }
}
