package xyz.dgz48.tasks.webapi.shared.infra;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeImageHintsConfig.Registrar.class)
class NativeImageHintsConfig {

  static class Registrar implements RuntimeHintsRegistrar {

    // Hibernate Validator の組込 ConstraintValidator 実装。Spring Boot AOT は検証対象 Bean
    // 経由でしか hints を登録せず、これら実装クラスの no-arg コンストラクタが native image に
    // 残らない。結果 @RequestBody/@RequestParam を @NotNull/@Size 等で検証する全経路が
    // BeanCreationException("No default constructor found")で 500 になる(#810)。
    // 利用中の制約(@NotNull/@NotBlank/@Size/@Min/@Max/@Pattern/@Email)に対応する実装を
    // 明示登録する。型ごとに分かれる Min/Max は将来のフィールド型変更に備え数値 variant を網羅。
    private static final String[] HV_CONSTRAINT_VALIDATORS = {
      "org.hibernate.validator.internal.constraintvalidators.bv.NotNullValidator",
      "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator",
      "org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator",
      "org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator",
      "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForCharSequence",
      "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForCollection",
      "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForMap",
      "org.hibernate.validator.internal.constraintvalidators.bv.size.SizeValidatorForArray",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForBigDecimal",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForBigInteger",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForByte",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForDouble",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForFloat",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForInteger",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForLong",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForShort",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForNumber",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MinValidatorForCharSequence",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForBigDecimal",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForBigInteger",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForByte",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForDouble",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForFloat",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForInteger",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForLong",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForShort",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForNumber",
      "org.hibernate.validator.internal.constraintvalidators.bv.number.bound.MaxValidatorForCharSequence",
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      // JBoss Logging annotation-generated impls; not reached by Spring Boot AOT hints
      for (String cls :
          new String[] {
            "org.hibernate.validator.internal.util.logging.Log_$logger",
            "org.hibernate.validator.internal.util.logging.Messages_$bundle",
          }) {
        try {
          hints.reflection().registerType(Class.forName(cls), MemberCategory.values());
        } catch (ClassNotFoundException ignored) {
          // Class absent on this classpath; nothing to register, skip intentionally.
        }
      }
      // 組込 ConstraintValidator(no-arg コンストラクタ)を reflection 登録する(#810)。
      for (String cls : HV_CONSTRAINT_VALIDATORS) {
        try {
          hints.reflection().registerType(Class.forName(cls), MemberCategory.values());
        } catch (ClassNotFoundException ignored) {
          // 当該バージョンに存在しない validator は登録不要。意図的にスキップする。
        }
      }
      // Flyway classpath scanner cannot enumerate directories under the native-image resource:
      // protocol
      hints.resources().registerPattern("db/migration/*.sql");
    }
  }
}
