package xyz.dgz48.tasks.webapi.security.infra;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal;

/**
 * native-image 用リフレクションヒント(security feature)。
 *
 * <p>コントローラの {@code @AuthenticationPrincipal(expression = "id")} は SpEL で {@link
 * TasksPrincipal#getId()} をリフレクション解決する。native-image では対象型を明示登録しないと {@code SpelEvaluationException:
 * EL1008E ... 'id' cannot be found} となり当該エンドポイントが 500 になる。
 *
 * <p>JVM ではリフレクションが常に可能なため CI(JVM テスト / e2e)では顕在化せず、native-image を実行する 環境(ADR-0008、dev/stg/prd の
 * ECS)でのみ発生する。Spring AOT は principal の実型を静的に決定できず 自動登録しないため、ここで明示登録する。
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(SecurityRuntimeHints.Registrar.class)
class SecurityRuntimeHints {

  static class Registrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      hints
          .reflection()
          .registerType(
              TasksPrincipal.class,
              MemberCategory.INVOKE_PUBLIC_METHODS,
              MemberCategory.DECLARED_FIELDS);
    }
  }
}
