package xyz.dgz48.tasks.webapi.shared.web;

/** API エラーコード(設計規約 §2.4 / ADR-0011)。 */
public enum ErrorCode {
  E_VALIDATION,
  E_UNAUTHORIZED,
  E_FORBIDDEN,
  E_NOT_FOUND,
  E_CONFLICT,
  E_PRECONDITION_FAILED,
  E_UNPROCESSABLE,
  E_INTERNAL
}
