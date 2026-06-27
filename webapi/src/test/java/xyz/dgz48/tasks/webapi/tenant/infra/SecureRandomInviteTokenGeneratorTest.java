package xyz.dgz48.tasks.webapi.tenant.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenGenerator;

class SecureRandomInviteTokenGeneratorTest {

  private final InviteTokenGenerator generator = new SecureRandomInviteTokenGenerator();

  @Test
  void generate_returnsUrlSafeBase64WithoutPadding() {
    String token = generator.generate();
    // URL-safe Base64(パディングなし): A-Z a-z 0-9 - _ のみ。256bit → 43 文字
    assertThat(token).matches("[A-Za-z0-9_-]+").hasSize(43);
  }

  @Test
  void generate_returnsDistinctTokens() {
    assertThat(generator.generate()).isNotEqualTo(generator.generate());
  }
}
