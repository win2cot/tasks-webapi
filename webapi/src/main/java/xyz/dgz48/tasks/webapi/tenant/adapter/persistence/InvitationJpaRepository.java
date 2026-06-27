package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;

interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, Long> {

  /** 当該テナント・email・status の招待を返す(tenantFilter により tenant_id は自動絞り込み)。 */
  List<InvitationJpaEntity> findByEmailAndStatus(String email, InvitationStatus status);
}
