package xyz.dgz48.tasks.webapi.task.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, Long> {

  /**
   * JPQL クエリとして実行するため、Hibernate Filter "tenantFilter" が有効な場合は 自動的にテナント絞り込みが適用される(ADR-0010)。 {@code
   * em.find()} ベースの継承実装はフィルタを適用しないため、本メソッドで上書きする。
   */
  @Override
  @Query("SELECT t FROM TaskJpaEntity t WHERE t.id = :id")
  Optional<TaskJpaEntity> findById(@Param("id") Long id);
}
