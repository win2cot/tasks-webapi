package xyz.dgz48.tasks.webapi.audit.domain;

/**
 * 連鎖検証で検出した不整合(ADR-0038 §3.7)。改ざん検知は fail-open であり、検出はアラートと構造化ログに用いる。
 *
 * @param chainKey 不整合が見つかった連鎖キー
 * @param atSeq 不整合が見つかった chain_seq
 * @param reason 不整合の種別
 */
public record AuditChainMismatch(long chainKey, long atSeq, Reason reason) {

  /** 不整合の種別。 */
  public enum Reason {
    /** 連鎖順序の欠落・並べ替え(期待 chain_seq と不一致)。 */
    SEQUENCE_BROKEN,
    /** 行内容または格納ハッシュの改変(再計算ハッシュと不一致)。 */
    HASH_MISMATCH,
    /** 末尾の切り詰め(chain_heads の末尾と生存行の末尾が不一致)。 */
    TAIL_MISMATCH,
    /** 保管削除済み prefix の検証起点アンカーが見つからない。 */
    MISSING_ANCHOR
  }
}
