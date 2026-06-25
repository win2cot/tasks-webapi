package xyz.dgz48.tasks.webapi.audit.adapter.external;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import xyz.dgz48.tasks.webapi.audit.usecase.HmacKeyProvider;

/**
 * 設定値({@code audit.hash-chain.secret})を HMAC 鍵に用いる {@link HmacKeyProvider} 実装。
 *
 * <p>ローカル / テスト用のフォールバック。本番は {@code source=ssm} で Parameter Store から鍵をロードする実装に切り替える。鍵は不変のため {@link
 * SecretKeySpec} を一度だけ構築して保持する。
 */
public class PropertyHmacKeyProvider implements HmacKeyProvider {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final String keyId;
  private final SecretKey key;

  public PropertyHmacKeyProvider(String keyId, String secret) {
    this.keyId = keyId;
    this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  @Override
  public String currentKeyId() {
    return keyId;
  }

  @Override
  public SecretKey keyFor(String keyId) {
    if (!this.keyId.equals(keyId)) {
      throw new IllegalArgumentException("未知の鍵識別子です: " + keyId);
    }
    return key;
  }
}
