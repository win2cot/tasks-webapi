package xyz.dgz48.tasks.webapi.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

  /** 匿名サブクラス: {@link DomainException} は abstract のため、test 内で具象化して挙動を検証。 */
  private static final class TestDomainException extends DomainException {
    TestDomainException(String message) {
      super(message);
    }

    TestDomainException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Test
  void messageOnlyConstructorPropagatesMessage() {
    var ex = new TestDomainException("boom");
    assertThat(ex.getMessage()).isEqualTo("boom");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void messageAndCauseConstructorPropagatesBoth() {
    var cause = new IllegalStateException("root");
    var ex = new TestDomainException("boom", cause);
    assertThat(ex.getMessage()).isEqualTo("boom");
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void isRuntimeExceptionSubclass() {
    var ex = new TestDomainException("boom");
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }
}
