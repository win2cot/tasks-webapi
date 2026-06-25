package xyz.dgz48.tasks.webapi.audit.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * chain_heads テーブルの JPA エンティティ(ADR-0038 §3.4)。
 *
 * <p>{@code chain_key} 単位の連鎖末尾(末尾ハッシュ・最大 chain_seq)を保持する。監査 INSERT 時に該当行を悲観ロックで取得して 並行 INSERT
 * を直列化する。{@code chain_key} はテナント行は {@code tenant_id}、横断行は予約値 {@code 0}。
 */
@Entity
@Table(name = "chain_heads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init")
class ChainHeadJpaEntity {

  @Id
  @Column(name = "chain_key")
  private Long chainKey;

  @Column(name = "last_hash", nullable = false, length = 64)
  private String lastHash;

  @Column(name = "last_seq", nullable = false)
  private Long lastSeq;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  void update(String lastHash, long lastSeq, LocalDateTime updatedAt) {
    this.lastHash = lastHash;
    this.lastSeq = lastSeq;
    this.updatedAt = updatedAt;
  }
}
