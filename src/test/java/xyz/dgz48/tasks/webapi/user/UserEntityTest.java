package xyz.dgz48.tasks.webapi.user;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
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
class UserEntityTest {

  @Autowired EntityManager entityManager;

  @Test
  void canPersistUser() {
    var user = new User("sub-001", "user@example.com", "山田 太郎", "ヤマダ タロウ", "開発部");
    entityManager.persist(user);
    entityManager.flush();

    assertThat(user.getId()).isNotNull();
    assertThat(user.getOidcSub()).isEqualTo("sub-001");
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getFullName()).isEqualTo("山田 太郎");
    assertThat(user.getFullNameKana()).isEqualTo("ヤマダ タロウ");
    assertThat(user.getDepartmentName()).isEqualTo("開発部");
  }

  @Test
  void canPersistUserWithoutDepartment() {
    var user = new User("sub-002", "nodept@example.com", "鈴木 花子", "スズキ ハナコ", null);
    entityManager.persist(user);
    entityManager.flush();

    assertThat(user.getId()).isNotNull();
    assertThat(user.getDepartmentName()).isNull();
  }

  @Test
  void canFindUserById() {
    var user = new User("sub-003", "find@example.com", "田中 一郎", "タナカ イチロウ", null);
    entityManager.persist(user);
    entityManager.flush();
    entityManager.clear();

    var found = entityManager.find(User.class, user.getId());
    assertThat(found).isNotNull();
    assertThat(found.getOidcSub()).isEqualTo("sub-003");
    assertThat(found.getEmail()).isEqualTo("find@example.com");
  }

  @Test
  void canPersistMultipleUsersWithDifferentOidcSubs() {
    var user1 = new User("sub-004", "first@example.com", "佐藤 次郎", "サトウ ジロウ", null);
    var user2 = new User("sub-005", "second@example.com", "佐藤 三郎", "サトウ サブロウ", "営業部");
    entityManager.persist(user1);
    entityManager.persist(user2);
    entityManager.flush();

    assertThat(user1.getId()).isNotNull();
    assertThat(user2.getId()).isNotNull();
    assertThat(user1.getId()).isNotEqualTo(user2.getId());
  }
}
