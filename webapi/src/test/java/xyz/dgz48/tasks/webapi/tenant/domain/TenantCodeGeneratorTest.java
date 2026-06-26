package xyz.dgz48.tasks.webapi.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link TenantCodeGenerator} の slug 生成・サフィックス付与の単体テスト。 */
class TenantCodeGeneratorTest {

  private final TenantCodeGenerator generator = new TenantCodeGenerator();

  @Nested
  class ToBaseCode {

    @Test
    void asciiNameBecomesLowerKebab() {
      assertThat(generator.toBaseCode("Sales Team")).isEqualTo("sales-team");
    }

    @Test
    void symbolsAndSpacesCollapseToSingleHyphen() {
      assertThat(generator.toBaseCode("  Hello,  World!!  ")).isEqualTo("hello-world");
    }

    @Test
    void leadingAndTrailingHyphensAreTrimmed() {
      assertThat(generator.toBaseCode("---ACME---")).isEqualTo("acme");
    }

    @Test
    void japaneseOnlyNameFallsBackToTenant() {
      assertThat(generator.toBaseCode("営業部テナント")).isEqualTo(TenantCodeGenerator.FALLBACK);
    }

    @Test
    void symbolOnlyNameFallsBackToTenant() {
      assertThat(generator.toBaseCode("!!!")).isEqualTo(TenantCodeGenerator.FALLBACK);
    }

    @Test
    void resultIsTruncatedToMaxLength() {
      String longName = "a".repeat(80);
      assertThat(generator.toBaseCode(longName)).hasSize(TenantCodeGenerator.MAX_LENGTH);
    }

    @Test
    void resultAlwaysMatchesCodePattern() {
      assertThat(generator.toBaseCode("Café Münchën 2024")).matches("^[a-z0-9-]+$");
    }
  }

  @Nested
  class WithSuffix {

    @Test
    void appendsHyphenSuffix() {
      assertThat(generator.withSuffix("sales-team", 2)).isEqualTo("sales-team-2");
    }

    @Test
    void truncatesHeadToFitMaxLength() {
      String base = "a".repeat(TenantCodeGenerator.MAX_LENGTH);
      String result = generator.withSuffix(base, 10);
      assertThat(result).hasSizeLessThanOrEqualTo(TenantCodeGenerator.MAX_LENGTH).endsWith("-10");
      assertThat(result).matches("^[a-z0-9-]+$");
    }

    @Test
    void doesNotProduceDoubleHyphenWhenHeadEndsWithHyphenAfterTruncation() {
      // 切り詰め長(MAX_LENGTH-2=48)の境界がハイフンに当たるケースでも "--" を作らない。
      String base = "a".repeat(TenantCodeGenerator.MAX_LENGTH - 3) + "-extra";
      String result = generator.withSuffix(base, 3);
      assertThat(result).doesNotContain("--").endsWith("-3").matches("^[a-z0-9-]+$");
    }
  }
}
