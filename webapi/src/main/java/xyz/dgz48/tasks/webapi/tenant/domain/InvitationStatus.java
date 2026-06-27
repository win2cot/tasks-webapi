package xyz.dgz48.tasks.webapi.tenant.domain;

/** 招待トークンの状態(ADR-0017 §3.1)。 */
public enum InvitationStatus {
  /** 発行済み・未消費。 */
  PENDING,
  /** 受諾成功により消費済み。 */
  USED,
  /** 再送または取消により失効。 */
  REVOKED
}
