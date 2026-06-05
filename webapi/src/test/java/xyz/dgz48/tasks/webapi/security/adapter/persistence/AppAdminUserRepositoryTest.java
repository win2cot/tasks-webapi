package xyz.dgz48.tasks.webapi.security.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;

@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
@Transactional
class AppAdminUserRepositoryTest {

  @Autowired AppAdminUserRepository repository;

  @Test
  void existsByOidcSubReturnsTrueAfterInsert() {
    var entity = new AppAdminUserJpaEntity("admin-sub-001", LocalDateTime.of(2026, 1, 1, 0, 0));
    repository.save(entity);
    repository.flush();

    assertThat(repository.existsByOidcSub("admin-sub-001")).isTrue();
  }

  @Test
  void existsByOidcSubReturnsFalseForUnknownSub() {
    assertThat(repository.existsByOidcSub("unknown-sub")).isFalse();
  }

  @Test
  void existsByOidcSubReturnsFalseAfterDelete() {
    var entity = new AppAdminUserJpaEntity("admin-sub-002", LocalDateTime.of(2026, 1, 1, 0, 0));
    repository.save(entity);
    repository.flush();

    repository.delete(entity);
    repository.flush();

    assertThat(repository.existsByOidcSub("admin-sub-002")).isFalse();
  }

  @Test
  void canPersistAppAdminUser() {
    var createdAt = LocalDateTime.of(2026, 6, 1, 12, 0);
    var entity = new AppAdminUserJpaEntity("admin-sub-003", createdAt);
    repository.save(entity);
    repository.flush();

    assertThat(entity.getId()).isNotNull();
    assertThat(entity.getOidcSub()).isEqualTo("admin-sub-003");
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }
}
