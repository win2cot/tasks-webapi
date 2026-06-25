package xyz.dgz48.tasks.webapi.audit.usecase;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditAnchor;
import xyz.dgz48.tasks.webapi.audit.domain.AuditCanonicalizer;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainHasher;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch.Reason;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainRow;
import xyz.dgz48.tasks.webapi.audit.domain.ChainHead;

/**
 * 単一連鎖({@code chain_key})を検証し、整合していればチェックポイントを追記する(ADR-0038 §3.7)。
 *
 * <p>保管窓内の最古チェックポイント(無ければ GENESIS)を起点に末尾まで HMAC を再計算し、各行の格納ハッシュ・順序・末尾を突合する。 連鎖ごとに独立したトランザクションで実行し、1
 * 連鎖の検証が他連鎖をブロックしないようにする(呼出側で fail-open に集約)。
 */
@Service
@RequiredArgsConstructor
class AuditChainVerifier {

  private final AuditChainQueryPort queryPort;
  private final HmacKeyProvider hmacKeyProvider;

  /** 当該連鎖を検証する。整合していれば空リストを返し新チェックポイントを追記する。不整合は最初の 1 件を返す(検出は事後・fail-open)。 */
  @Transactional
  List<AuditChainMismatch> verify(long chainKey) {
    List<AuditChainRow> rows = queryPort.findRows(chainKey);
    if (rows.isEmpty()) {
      return List.of();
    }

    long minSeq = rows.get(0).canonical().chainSeq();
    long startSeq;
    String prevHash;
    if (minSeq == 1) {
      startSeq = 0;
      prevHash = AuditChainHasher.GENESIS_HASH;
    } else {
      // 保管削除(B-03)で prefix が消えている場合、検証起点は削除境界の retained アンカー。
      Optional<AuditAnchor> anchor = queryPort.findLatestAnchorBelow(chainKey, minSeq);
      if (anchor.isEmpty()) {
        return List.of(new AuditChainMismatch(chainKey, minSeq, Reason.MISSING_ANCHOR));
      }
      startSeq = anchor.get().seqAtCheckpoint();
      prevHash = anchor.get().headHash();
    }

    long expectedSeq = startSeq + 1;
    String prev = prevHash;
    for (AuditChainRow row : rows) {
      long seq = row.canonical().chainSeq();
      if (seq != expectedSeq) {
        return List.of(new AuditChainMismatch(chainKey, expectedSeq, Reason.SEQUENCE_BROKEN));
      }
      String recomputed =
          AuditChainHasher.hmacHex(
              AuditCanonicalizer.canonicalBytes(row.canonical()),
              prev,
              hmacKeyProvider.keyFor(row.canonical().hashKeyId()));
      if (!recomputed.equals(row.storedHash())) {
        return List.of(new AuditChainMismatch(chainKey, seq, Reason.HASH_MISMATCH));
      }
      prev = row.storedHash();
      expectedSeq++;
    }

    // 末尾切り詰め検出: 生存行の末尾と chain_heads の末尾(書込のたびに更新)を突合する。
    ChainHead head =
        queryPort
            .findHead(chainKey)
            .orElseThrow(() -> new IllegalStateException("chain_heads 行が見つかりません: " + chainKey));
    AuditChainRow last = rows.get(rows.size() - 1);
    if (head.lastSeq() != last.canonical().chainSeq()
        || !head.lastHash().equals(last.storedHash())) {
      return List.of(new AuditChainMismatch(chainKey, head.lastSeq(), Reason.TAIL_MISMATCH));
    }

    queryPort.appendAnchor(
        chainKey, head.lastSeq(), head.lastHash(), hmacKeyProvider.currentKeyId());
    return List.of();
  }
}
