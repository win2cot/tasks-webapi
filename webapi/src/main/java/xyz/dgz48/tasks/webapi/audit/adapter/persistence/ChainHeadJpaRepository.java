package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ChainHeadJpaRepository extends JpaRepository<ChainHeadJpaEntity, Long> {

  /**
   * 当該 {@code chain_key} の行が無ければジェネシス相当(末尾 seq=0)で生成する。存在する場合は no-op (上書きしない)。直後の {@link
   * #lockByChainKey} による {@code FOR UPDATE} が必ず行を掴めるよう、行の存在を保証する(ADR-0038 §3.4)。
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO chain_heads (chain_key, last_hash, last_seq, updated_at)"
              + " VALUES (:chainKey, :genesisHash, 0, :now)"
              + " ON DUPLICATE KEY UPDATE chain_key = chain_key",
      nativeQuery = true)
  void ensureExists(
      @Param("chainKey") long chainKey,
      @Param("genesisHash") String genesisHash,
      @Param("now") LocalDateTime now);

  /** 当該 {@code chain_key} の末尾行を悲観ロック({@code SELECT ... FOR UPDATE})で取得する。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from ChainHeadJpaEntity h where h.chainKey = :chainKey")
  Optional<ChainHeadJpaEntity> lockByChainKey(@Param("chainKey") long chainKey);
}
