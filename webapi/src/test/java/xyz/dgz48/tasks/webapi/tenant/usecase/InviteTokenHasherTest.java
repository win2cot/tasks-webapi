package xyz.dgz48.tasks.webapi.tenant.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InviteTokenHasherTest {

  @Test
  void sha256Hex_matchesKnownVector() {
    // SHA-256("abc") の既知ベクタ
    assertThat(InviteTokenHasher.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void sha256Hex_is64LowercaseHex() {
    String hash = InviteTokenHasher.sha256Hex("some-random-token");
    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void sha256Hex_isDeterministic() {
    assertThat(InviteTokenHasher.sha256Hex("t")).isEqualTo(InviteTokenHasher.sha256Hex("t"));
  }
}
