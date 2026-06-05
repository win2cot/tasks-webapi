package xyz.dgz48.tasks.webapi.shared.exception;

/** バージョン競合など、前提条件が満たされない場合の業務例外(HTTP 412)。 */
public class PreconditionFailedException extends DomainException {

  public PreconditionFailedException(String message) {
    super(message);
  }
}
