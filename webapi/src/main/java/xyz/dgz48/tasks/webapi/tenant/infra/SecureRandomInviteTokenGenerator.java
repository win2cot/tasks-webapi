package xyz.dgz48.tasks.webapi.tenant.infra;

import java.security.SecureRandom;
import java.util.Base64;
import xyz.dgz48.tasks.webapi.tenant.usecase.InviteTokenGenerator;

/** {@link InviteTokenGenerator} の実装。SecureRandom 256bit を URL-safe Base64(パディングなし・約 43 文字)で返す。 */
class SecureRandomInviteTokenGenerator implements InviteTokenGenerator {

  private static final int TOKEN_BYTES = 32; // 256 bit
  private final SecureRandom secureRandom = new SecureRandom();
  private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

  @Override
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return encoder.encodeToString(bytes);
  }
}
