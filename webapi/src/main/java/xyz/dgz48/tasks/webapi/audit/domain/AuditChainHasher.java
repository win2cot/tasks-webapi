package xyz.dgz48.tasks.webapi.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * 監査ハッシュチェーンの 1 リンクを計算する(ADR-0038 §3.2 / §3.3)。
 *
 * <pre>hash_n = HMAC_SHA256( key , canonical(row_n) ‖ hash_{n-1}_hex )</pre>
 *
 * <p>前ハッシュは hex 文字列(64 桁小文字)の UTF-8 バイト列として正準バイト列の後ろに連結する。各連鎖の先頭行は前ハッシュとして {@link #GENESIS_HASH}
 * を用いる。HMAC 鍵を用いるため、DB 単独を侵害した攻撃者(鍵を持たない)は連鎖を偽造できない(NIST AU-9 / AU-10)。
 */
public final class AuditChainHasher {

  /** 各連鎖の先頭行が前ハッシュとして用いるジェネシスハッシュ(64 桁のゼロ)。 */
  public static final String GENESIS_HASH = "0".repeat(64);

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private AuditChainHasher() {}

  /**
   * 自レコードハッシュを計算し 64 桁小文字 hex で返す。
   *
   * @param canonicalBytes {@link AuditCanonicalizer#canonicalBytes} の出力
   * @param prevHashHex 直前行のハッシュ hex(連鎖先頭は {@link #GENESIS_HASH})
   * @param key この行の {@code hash_key_id} に対応する HMAC 鍵
   * @return 自レコードハッシュ(64 桁小文字 hex)
   */
  public static String hmacHex(byte[] canonicalBytes, String prevHashHex, SecretKey key) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(key);
      mac.update(canonicalBytes);
      byte[] digest = mac.doFinal(prevHashHex.getBytes(StandardCharsets.UTF_8));
      return toHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 の初期化に失敗しました", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
