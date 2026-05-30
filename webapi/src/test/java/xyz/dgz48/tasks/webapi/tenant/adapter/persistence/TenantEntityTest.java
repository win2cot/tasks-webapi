package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.FixedClockConfiguration;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantPlan;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantStatus;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  MockJwtDecoderConfiguration.class,
  FixedClockConfiguration.class
})
@Transactional
class TenantEntityTest {

  @Autowired EntityManager entityManager;

  @Test
  void canPersistTenant() {
    var tenant = new TenantJpaEntity("T-001", "テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    assertThat(tenant.getId()).isNotNull();
    assertThat(tenant.getCode()).isEqualTo("T-001");
    assertThat(tenant.getName()).isEqualTo("テスト株式会社");
    assertThat(tenant.getPlan()).isEqualTo(TenantPlan.STANDARD);
    assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
  }

  @Test
  void canFindTenantById() {
    var tenant = new TenantJpaEntity("T-002", "検索テスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();
    entityManager.clear();

    var found = entityManager.find(TenantJpaEntity.class, tenant.getId());
    assertThat(found).isNotNull();
    assertThat(found.getCode()).isEqualTo("T-002");
    assertThat(found.getName()).isEqualTo("検索テスト株式会社");
  }

  @Test
  void timestampsAreSetFromFixedClock() {
    LocalDateTime expectedTimestamp = FixedClockConfiguration.FIXED_NOW;

    var tenant = new TenantJpaEntity("T-003", "タイムスタンプテスト株式会社");
    entityManager.persist(tenant);
    entityManager.flush();

    assertThat(tenant.getCreatedAt()).isEqualTo(expectedTimestamp);
    assertThat(tenant.getUpdatedAt()).isEqualTo(expectedTimestamp);
  }
}
