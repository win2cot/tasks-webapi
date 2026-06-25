package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * audit_anchors テーブルの JPA エンティティ(ADR-0038 §3.6)。
 *
 * <p>B-05 検証成功後に各 {@code chain_key} の連鎖頭を追記専用で固定する。B-03(保管削除)の対象外で prune しない。
 */
@Entity
@Table(name = "audit_anchors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
class AuditAnchorJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "chain_key", nullable = false)
  private Long chainKey;

  @Column(name = "seq_at_checkpoint", nullable = false)
  private Long seqAtCheckpoint;

  @Column(name = "head_hash", nullable = false, length = 64)
  private String headHash;

  @Column(name = "hash_key_id", nullable = false, length = 32)
  private String hashKeyId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  AuditAnchorJpaEntity(
      long chainKey,
      long seqAtCheckpoint,
      String headHash,
      String hashKeyId,
      LocalDateTime createdAt) {
    this.chainKey = chainKey;
    this.seqAtCheckpoint = seqAtCheckpoint;
    this.headHash = headHash;
    this.hashKeyId = hashKeyId;
    this.createdAt = createdAt;
  }
}
