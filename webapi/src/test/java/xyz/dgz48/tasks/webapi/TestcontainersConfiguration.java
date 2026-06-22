package xyz.dgz48.tasks.webapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  // Spring manages this bean's lifecycle (start/stop), so the container is never explicitly closed
  // here; suppress the false-positive resource-leak diagnostic.
  @SuppressWarnings("resource")
  @Bean
  @ServiceConnection
  MySQLContainer mysqlContainer() {
    return new MySQLContainer("mysql:8.4")
        .withCommand("--default-time-zone=Asia/Tokyo")
        .withUrlParam("connectionTimeZone", "SERVER")
        .withUrlParam("forceConnectionTimeZoneToSession", "true");
  }
}
