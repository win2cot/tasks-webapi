package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dgz48.tasks.webapi.tenant.domain.InvitationStatus;

interface InvitationJpaRepository extends JpaRepository<InvitationJpaEntity, Long> {

  /** 当該テナント・email・status の招待を返す(tenantFilter により tenant_id は自動絞り込み)。 */
  List<InvitationJpaEntity> findByEmailAndStatus(String email, InvitationStatus status);

  /**
   * token_hash で招待 1 件を引く(受諾フロー用)。token_hash は UNIQUE。受諾は TenantContext 未設定で到達するため tenantFilter は
   * 無効化されており全テナント横断で照会される(ADR-0040 §3.3)。
   */
  Optional<InvitationJpaEntity> findByTokenHash(String tokenHash);
}
