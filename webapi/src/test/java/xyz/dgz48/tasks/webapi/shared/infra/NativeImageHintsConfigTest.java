package xyz.dgz48.tasks.webapi.shared.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

/**
 * Hibernate Validator 組込 ConstraintValidator の native-image reflection 登録を回帰固定する (#810)。
 *
 * <p>これらヒントが失われると native-image でのみ {@code @RequestBody}/{@code @RequestParam} を
 * {@code @NotNull}/{@code @Size}/{@code @Min} 等で検証する経路が {@code BeanCreationException("No default
 * constructor found")} により 500 になる(JVM テストでは検出できない退行)。本テストはヒント登録そのものを検証する。
 */
class NativeImageHintsConfigTest {

  private static final String BV = "org.hibernate.validator.internal.constraintvalidators.bv.";

  @Test
  void registersBuiltInConstraintValidatorsForReflection() {
    RuntimeHints hints = new RuntimeHints();

    new NativeImageHintsConfig.Registrar().registerHints(hints, getClass().getClassLoader());

    for (String validator :
        new String[] {
          BV + "NotNullValidator",
          BV + "NotBlankValidator",
          BV + "EmailValidator",
          BV + "PatternValidator",
          BV + "size.SizeValidatorForCharSequence",
          BV + "number.bound.MinValidatorForInteger",
          BV + "number.bound.MaxValidatorForInteger",
        }) {
      assertThat(
              RuntimeHintsPredicates.reflection()
                  .onType(TypeReference.of(validator))
                  .withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
          .as("native hint for %s", validator)
          .accepts(hints);
    }
  }
}
