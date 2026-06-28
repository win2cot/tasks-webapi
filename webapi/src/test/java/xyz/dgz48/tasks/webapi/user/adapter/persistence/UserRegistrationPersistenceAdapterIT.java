package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.user.usecase.UserRegistrationPort;

/** 会員登録の {@code users} 行 upsert(ADR-0040 §3.3 ①)の統合テスト(Testcontainers MySQL)。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserRegistrationPersistenceAdapterIT {

  @Autowired UserRegistrationPort port;
  @Autowired UserRepository userRepository;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;

  private final List<Long> createdIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    if (createdIds.isEmpty()) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          for (Long id : createdIds) {
            em.createNativeQuery("DELETE FROM users WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
          }
          return null;
        });
  }

  private UserJpaEntity reload(Long id) {
    return txTemplate.execute(ignored -> userRepository.findById(id).orElseThrow());
  }

  @Test
  void insertsNewPendingRow() {
    Long id = port.upsertPendingMember("new@example.com", "新規太郎", "シンキタロウ", "開発部");
    createdIds.add(id);

    UserJpaEntity row = reload(id);
    assertThat(row.getOidcSub()).isEqualTo("pending:new@example.com");
    assertThat(row.getEmail()).isEqualTo("new@example.com");
    assertThat(row.getFullName()).isEqualTo("新規太郎");
    assertThat(row.getFullNameKana()).isEqualTo("シンキタロウ");
    assertThat(row.getDepartmentName()).isEqualTo("開発部");
    assertThat(row.isPendingCorrelation()).isTrue();
    assertThat(row.isInactive()).isFalse();
  }

  @Test
  void updatesProfileOfExistingPendingRow() {
    // SPI insert 相当: profile 空の pending 行を先に作る
    Long id =
        txTemplate.execute(
            ignored -> {
              var u =
                  new UserJpaEntity("pending:exist@example.com", "exist@example.com", "", "", null);
              em.persist(u);
              em.flush();
              return u.getId();
            });
    createdIds.add(id);

    Long sameId = port.upsertPendingMember("exist@example.com", "更新太郎", "コウシンタロウ", "営業部");

    assertThat(sameId).isEqualTo(id);
    UserJpaEntity row = reload(id);
    assertThat(row.getFullName()).isEqualTo("更新太郎");
    assertThat(row.getFullNameKana()).isEqualTo("コウシンタロウ");
    assertThat(row.getDepartmentName()).isEqualTo("営業部");
    assertThat(row.isPendingCorrelation()).isTrue();
  }

  @Test
  void rejectsAlreadyCorrelatedRow() {
    Long id =
        txTemplate.execute(
            ignored -> {
              var u = new UserJpaEntity("kc-sub-real", "corr@example.com", "既存花子", "キゾンハナコ", null);
              em.persist(u);
              em.flush();
              return u.getId();
            });
    createdIds.add(id);

    assertThatThrownBy(() -> port.upsertPendingMember("corr@example.com", "別名", "ベツメイ", null))
        .isInstanceOf(IllegalStateException.class);
  }
}
