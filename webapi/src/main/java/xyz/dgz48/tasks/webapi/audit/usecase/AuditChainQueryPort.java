package xyz.dgz48.tasks.webapi.audit.usecase;

import java.util.List;
import java.util.Optional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditAnchor;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainRow;
import xyz.dgz48.tasks.webapi.audit.domain.ChainHead;

/** B-05 検証バッチが連鎖を読み出し・チェックポイントを追記するための Port(ADR-0038 §3.7)。 */
public interface AuditChainQueryPort {

  /** 連鎖が存在する全 {@code chain_key}(= {@code chain_heads} の全行)を返す。 */
  List<Long> findActiveChainKeys();

  /** 当該連鎖の生存行を {@code chain_seq} 昇順で返す。 */
  List<AuditChainRow> findRows(long chainKey);

  /** 当該連鎖の末尾状態を返す。 */
  Optional<ChainHead> findHead(long chainKey);

  /**
   * 指定 {@code chain_seq} 未満で最大の {@code seq_at_checkpoint} を持つアンカーを返す(保管削除後の検証起点)。
   *
   * @param chainKey 連鎖キー
   * @param seqExclusive これ未満の seq を持つ最新アンカーを探す
   */
  Optional<AuditAnchor> findLatestAnchorBelow(long chainKey, long seqExclusive);

  /** 検証成功後に新しいチェックポイントを追記する。 */
  void appendAnchor(long chainKey, long seqAtCheckpoint, String headHash, String hashKeyId);
}
