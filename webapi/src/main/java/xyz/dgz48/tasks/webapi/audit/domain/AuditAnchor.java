package xyz.dgz48.tasks.webapi.audit.domain;

/**
 * 検証チェックポイント({@code audit_anchors} の不変表現、ADR-0038 §3.6)。
 *
 * @param chainKey 連鎖キー(tenant_id、横断は 0)
 * @param seqAtCheckpoint この時点の chain_seq
 * @param headHash この時点の連鎖頭ハッシュ
 * @param hashKeyId チェックポイント作成時の現行鍵 ID
 */
public record AuditAnchor(long chainKey, long seqAtCheckpoint, String headHash, String hashKeyId) {}
