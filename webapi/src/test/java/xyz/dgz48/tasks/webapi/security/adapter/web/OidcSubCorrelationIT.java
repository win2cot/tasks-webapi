package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserJpaEntity;
import xyz.dgz48.tasks.webapi.user.adapter.persistence.UserRepository;

/**
 * 初回ログイン時の oidc_sub correlation(ADR-0040 §3.2 / ADR-0006 §3.2)の統合テスト。
 *
 * <p>会員登録 / SPI insert で作られた {@code pending:<email>} 行が、初回ログイン(本物の Keycloak {@code sub} を持つ JWT)で
 * email により突合され、本物の sub が書き戻されて以降ログイン可能になることを実 DB(Testcontainers)で検証する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OidcSubCorrelationIT {

  @Autowired TasksJwtAuthenticationConverter converter;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate txTemplate;
  @Autowired UserRepository userRepository;

  private Long userId;

  @AfterEach
  void tearDown() {
    if (userId == null) {
      return;
    }
    txTemplate.execute(
        ignored -> {
          em.createNativeQuery("DELETE FROM users WHERE id = ?")
              .setParameter(1, userId)
              .executeUpdate();
          return null;
        });
  }

  private Long insertPendingUser(String email) {
    return txTemplate.execute(
        ignored -> {
          var u = new UserJpaEntity("pending:" + email, email, "田中一郎", "タナカイチロウ", null);
          em.persist(u);
          em.flush();
          return u.getId();
        });
  }

  private static Jwt jwtWith(String sub, String email) {
    var builder = Jwt.withTokenValue("token").header("alg", "none").subject(sub);
    if (email != null) {
      builder = builder.claim("email", email);
    }
    return builder.build();
  }

  @Test
  void firstLoginCorrelatesPendingRowAndAllowsSubsequentLookupBySub() {
    userId = insertPendingUser("corr@example.com");

    var token = converter.convert(jwtWith("kc-sub-real", "corr@example.com"));

    // principal は突合した行を指す
    var principal = (TasksPrincipal) token.getPrincipal();
    assertThat(principal.getId()).isEqualTo(userId);
    assertThat(principal.getSub()).isEqualTo("kc-sub-real");

    // DB の oidc_sub は本物の sub に書き戻され、以降 sub で直接ヒットする
    Optional<UserJpaEntity> bySub =
        txTemplate.execute(ignored -> userRepository.findByOidcSub("kc-sub-real"));
    assertThat(bySub).isPresent();
    assertThat(bySub.orElseThrow().getId()).isEqualTo(userId);
    assertThat(bySub.orElseThrow().isPendingCorrelation()).isFalse();
  }

  @Test
  void rejectsWhenEmailMatchesAlreadyCorrelatedRow() {
    // 既に本物の sub に correlation 済みの行(別 Keycloak アカウントの sub を持つ JWT)はなりすまし防止のため弾く
    userId =
        txTemplate.execute(
            ignored -> {
              var u = new UserJpaEntity("kc-sub-owner", "owned@example.com", "本田", "ホンダ", null);
              em.persist(u);
              em.flush();
              return u.getId();
            });

    assertThatThrownBy(() -> converter.convert(jwtWith("kc-sub-attacker", "owned@example.com")))
        .isInstanceOf(UserNotRegisteredException.class);
  }

  @Test
  void rejectsWhenNoRowMatches() {
    assertThatThrownBy(() -> converter.convert(jwtWith("kc-sub-unknown", "nobody@example.com")))
        .isInstanceOf(UserNotRegisteredException.class);
  }
}
