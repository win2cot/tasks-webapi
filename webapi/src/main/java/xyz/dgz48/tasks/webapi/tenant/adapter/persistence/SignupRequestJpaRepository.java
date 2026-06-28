package xyz.dgz48.tasks.webapi.tenant.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dgz48.tasks.webapi.tenant.domain.SignupRequestStatus;

interface SignupRequestJpaRepository extends JpaRepository<SignupRequestJpaEntity, Long> {

  /** token_hash で 1 件引く(token_hash は UNIQUE)。確認画面・complete で使用。 */
  Optional<SignupRequestJpaEntity> findByTokenHash(String tokenHash);

  /** 当該 email・status のサインアップ要求を返す(再要求時の旧 PENDING 失効に使用)。 */
  List<SignupRequestJpaEntity> findByEmailAndStatus(String email, SignupRequestStatus status);
}
