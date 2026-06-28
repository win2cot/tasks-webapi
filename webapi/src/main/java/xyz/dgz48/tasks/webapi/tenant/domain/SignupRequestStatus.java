package xyz.dgz48.tasks.webapi.tenant.domain;

/** サインアップ要求(signup_requests)の状態(ADR-0040 §3.3)。 */
public enum SignupRequestStatus {
  /** 確認メール送信済み・未消費。 */
  PENDING,
  /** complete で消費済み。 */
  USED,
  /** 再要求で失効した旧トークン。 */
  REVOKED
}
