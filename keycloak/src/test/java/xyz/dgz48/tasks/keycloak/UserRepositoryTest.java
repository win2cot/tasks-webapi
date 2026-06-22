package xyz.dgz48.tasks.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link UserRepository#escapeLike} の LIKE メタ文字エスケープを検証する。 */
class UserRepositoryTest {

  @Test
  void escapeLike_escapesWildcardsAndEscapeChar() {
    // % と _ は前置エスケープされ、リテラルとして扱われる(ESCAPE '|' と対応)。
    assertEquals("|%foo|_bar", UserRepository.escapeLike("%foo_bar"));
  }

  @Test
  void escapeLike_doublesEscapeCharFirst() {
    // エスケープ文字自身は二重化する(後続の % / _ 置換で壊れないよう先に処理する)。
    assertEquals("a||b", UserRepository.escapeLike("a|b"));
    assertEquals("|%|_||", UserRepository.escapeLike("%_|"));
  }

  @Test
  void escapeLike_leavesPlainTextUnchanged() {
    assertEquals("tanaka@example.com", UserRepository.escapeLike("tanaka@example.com"));
  }
}
