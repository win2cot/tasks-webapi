package xyz.dgz48.tasks.webapi.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserAnonymizationDomainServiceTest {

  private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 1, 15, 19, 0, 0);

  private final UserAnonymizationDomainService service = new UserAnonymizationDomainService();

  private User buildActiveUser(long id) {
    return new User(
        id, "sub-" + id, "user" + id + "@example.com", "山田 太郎", "ヤマダ タロウ", "開発部", 0L, null);
  }

  @Test
  void anonymize_setsDeletedAt() {
    User user = buildActiveUser(1L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getDeletedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void anonymize_replacesEmailWithPlaceholder() {
    User user = buildActiveUser(42L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getEmail()).isEqualTo("__deleted__42@deleted.invalid");
  }

  @Test
  void anonymize_replacesOidcSubWithPlaceholder() {
    User user = buildActiveUser(42L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getOidcSub()).isEqualTo("__deleted__42");
  }

  @Test
  void anonymize_replacesFullName() {
    User user = buildActiveUser(1L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getFullName()).isEqualTo("__deleted__");
  }

  @Test
  void anonymize_replacesFullNameKana() {
    User user = buildActiveUser(1L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getFullNameKana()).isEqualTo("__deleted__");
  }

  @Test
  void anonymize_clearsDepartmentName() {
    User user = buildActiveUser(1L);
    service.anonymize(user, FIXED_NOW);
    assertThat(user.getDepartmentName()).isNull();
  }

  @Test
  void anonymize_placeholderIsUniquePerUserId() {
    User user1 = buildActiveUser(1L);
    User user2 = buildActiveUser(2L);
    service.anonymize(user1, FIXED_NOW);
    service.anonymize(user2, FIXED_NOW);
    assertThat(user1.getEmail()).isNotEqualTo(user2.getEmail());
    assertThat(user1.getOidcSub()).isNotEqualTo(user2.getOidcSub());
  }
}
