package xyz.dgz48.tasks.webapi.user.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link UserJpaEntity} の oidc_sub correlation ロジック(ADR-0040 §3.2 / ADR-0006 §3.2)の単体テスト。 */
class UserJpaEntityCorrelationTest {

  @Test
  void isPendingCorrelationTrueForPendingPlaceholder() {
    var user = new UserJpaEntity("pending:a@example.com", "a@example.com", "氏名", "シメイ", null);
    assertThat(user.isPendingCorrelation()).isTrue();
  }

  @Test
  void isPendingCorrelationFalseForRealSub() {
    var user = new UserJpaEntity("kc-sub-real", "a@example.com", "氏名", "シメイ", null);
    assertThat(user.isPendingCorrelation()).isFalse();
  }

  @Test
  void correlateOidcSubRewritesPendingPlaceholderToRealSub() {
    var user = new UserJpaEntity("pending:a@example.com", "a@example.com", "氏名", "シメイ", null);

    user.correlateOidcSub("kc-sub-real");

    assertThat(user.getOidcSub()).isEqualTo("kc-sub-real");
    assertThat(user.isPendingCorrelation()).isFalse();
  }

  @Test
  void correlateOidcSubRejectsAlreadyCorrelatedRow() {
    var user = new UserJpaEntity("kc-sub-existing", "a@example.com", "氏名", "シメイ", null);

    assertThatThrownBy(() -> user.correlateOidcSub("kc-sub-new"))
        .isInstanceOf(IllegalStateException.class);
  }
}
