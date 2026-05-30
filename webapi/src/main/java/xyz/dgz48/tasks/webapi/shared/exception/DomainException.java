package xyz.dgz48.tasks.webapi.shared.exception;

import org.jspecify.annotations.Nullable;

/**
 * 業務例外の基底クラス。{@code @ResponseStatus} は付与せず、各 feature の {@code @RestControllerAdvice} で HTTP
 * ステータスにマップする(設計規約 §1.1)。
 */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }

  protected DomainException(String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
