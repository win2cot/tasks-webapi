package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.task.domain.TaskStatus;

interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, Long> {

  /**
   * JPQL クエリとして実行するため、Hibernate Filter "tenantFilter" が有効な場合は 自動的にテナント絞り込みが適用される(ADR-0010)。 {@code
   * em.find()} ベースの継承実装はフィルタを適用しないため、本メソッドで上書きする。
   */
  @Override
  @Query("SELECT t FROM TaskJpaEntity t WHERE t.id = :id")
  Optional<TaskJpaEntity> findById(@Param("id") Long id);

  /**
   * ステータスのみを楽観ロックなしで更新する(last-write-wins、ADR-0012 amendment)。
   *
   * <p>{@code version} 列を変更しないため {@code @Version} チェックをバイパスし、同時更新が競合しない。 {@code clearAutomatically
   * = true} で永続化コンテキストをクリアし、後続 findById が最新 DB 値を返す。
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE TaskJpaEntity t SET t.status = :status, t.completedAt = :completedAt, t.updatedAt = :updatedAt WHERE t.id = :id")
  void updateStatusById(
      @Param("id") Long id,
      @Param("status") TaskStatus status,
      @Param("completedAt") @Nullable LocalDateTime completedAt,
      @Param("updatedAt") LocalDateTime updatedAt);
}
