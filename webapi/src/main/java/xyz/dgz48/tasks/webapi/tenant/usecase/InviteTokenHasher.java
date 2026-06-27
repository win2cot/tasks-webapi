package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 招待トークンの SHA-256 ハッシュ化(16進64文字)。DB には本ハッシュのみを保存する(ADR-0017 §3.1)。 */
public final class InviteTokenHasher {

  private InviteTokenHasher() {}

  /** 平文トークンの SHA-256 を小文字 16 進(64 文字)で返す。 */
  public static String sha256Hex(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 は JDK 標準で必ず存在するため到達不能。
      throw new IllegalStateException("SHA-256 が利用できません", e);
    }
  }
}
