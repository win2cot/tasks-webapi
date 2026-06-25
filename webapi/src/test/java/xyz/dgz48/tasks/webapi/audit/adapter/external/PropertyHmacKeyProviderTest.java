package xyz.dgz48.tasks.webapi.audit.adapter.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link PropertyHmacKeyProvider} のユニットテスト。 */
class PropertyHmacKeyProviderTest {

  private final PropertyHmacKeyProvider provider = new PropertyHmacKeyProvider("v1", "test-secret");

  @Test
  void currentKeyId_returnsConfiguredId() {
    assertThat(provider.currentKeyId()).isEqualTo("v1");
  }

  @Test
  void keyFor_currentId_returnsHmacSha256Key() {
    var key = provider.keyFor("v1");
    assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(key.getEncoded())
        .isEqualTo("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void keyFor_unknownId_throws() {
    assertThatThrownBy(() -> provider.keyFor("v2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("v2");
  }
}
