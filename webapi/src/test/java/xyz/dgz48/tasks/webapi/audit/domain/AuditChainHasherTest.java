package xyz.dgz48.tasks.webapi.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** {@link AuditChainHasher} のユニットテスト。 */
class AuditChainHasherTest {

  private static SecretKeySpec key(byte[] bytes) {
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Test
  void genesisHash_is64Zeros() {
    assertThat(AuditChainHasher.GENESIS_HASH).isEqualTo("0".repeat(64)).hasSize(64);
  }

  @Test
  void hmacHex_matchesRfc4231TestCase1() {
    // RFC 4231 §4.2 Test Case 1: key = 0x0b×20, data = "Hi There"。
    // hmacHex は canonicalBytes ‖ prevHashHex を HMAC するため、prevHashHex="" として
    // 実効メッセージを "Hi There" に一致させる。
    byte[] keyBytes = new byte[20];
    Arrays.fill(keyBytes, (byte) 0x0b);

    String hash =
        AuditChainHasher.hmacHex("Hi There".getBytes(StandardCharsets.UTF_8), "", key(keyBytes));

    assertThat(hash).isEqualTo("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
  }

  @Test
  void hmacHex_isDeterministic_and64LowercaseHex() {
    var k = key("secret-key".getBytes(StandardCharsets.UTF_8));
    byte[] canonical = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    String first = AuditChainHasher.hmacHex(canonical, AuditChainHasher.GENESIS_HASH, k);
    String second = AuditChainHasher.hmacHex(canonical, AuditChainHasher.GENESIS_HASH, k);

    assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
  }

  @Test
  void hmacHex_changesWhenPreviousHashChanges() {
    var k = key("secret-key".getBytes(StandardCharsets.UTF_8));
    byte[] canonical = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    String fromGenesis = AuditChainHasher.hmacHex(canonical, AuditChainHasher.GENESIS_HASH, k);
    String fromOther = AuditChainHasher.hmacHex(canonical, "a".repeat(64), k);

    assertThat(fromGenesis).isNotEqualTo(fromOther);
  }
}
