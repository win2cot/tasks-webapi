package xyz.dgz48.tasks.webapi.shared.exception;

/** SaaS Admin ロールが必要な操作を非 SaaS Admin が試みた場合に送出する業務例外(HTTP 403)。 */
public class SaasAdminRequiredException extends DomainException {

  public SaasAdminRequiredException(String message) {
    super(message);
  }
}
