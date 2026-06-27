package xyz.dgz48.tasks.webapi.security.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;

/**
 * {@code @AuthenticationPrincipal(expression = "id")} の native-image 対応を回帰固定する。
 *
 * <p>このヒントが失われると native-image でのみ SpEL EL1008E により当該エンドポイントが 500 になる (JVM
 * テストでは検出できない退行)。本テストはヒント登録そのものを検証して退行を防ぐ。
 */
class SecurityRuntimeHintsTest {

  @Test
  void registersTasksPrincipalReflectionForAuthenticationPrincipalSpel() {
    RuntimeHints hints = new RuntimeHints();

    new SecurityRuntimeHints.Registrar().registerHints(hints, getClass().getClassLoader());

    assertThat(
            RuntimeHintsPredicates.reflection()
                .onType(TasksPrincipal.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS))
        .accepts(hints);
  }
}
