package xyz.dgz48.tasks.webapi.shared.exception;

/** 業務例外の基底クラス。HTTP マッピングは {@code @RestControllerAdvice} で行う。 */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }
}
