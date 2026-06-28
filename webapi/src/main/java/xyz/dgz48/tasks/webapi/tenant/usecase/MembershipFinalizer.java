package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/**
 * 招待受諾の最終確定(user_tenants 紐付け + 招待 USED 消費)を 1 トランザクションで原子的に行う(ADR-0040 §3.3)。
 *
 * <p>会員登録プリミティブ({@link xyz.dgz48.tasks.webapi.user.usecase.RegisterMemberUseCase})は内部に project
 * DB↔Keycloak の リモート境界を含むため DB トランザクションに入れられない(ADR-0040 §3.5)。そのため「登録(=補償対象)」と「紐付け+消費(=本クラスの原子的 DB
 * 確定)」 を分離し、本クラスを別 Bean に切り出すことで自己呼び出しによる {@code @Transactional} 無効化を避ける。
 */
@Service
@RequiredArgsConstructor
public class MembershipFinalizer {

  private final UserTenantManagementPort userTenantManagementPort;
  private final InvitationPort invitationPort;

  /** user_tenants に ACTIVE メンバーを追加し、招待を USED 消費する(原子的)。 */
  @Transactional
  public void linkAndConsume(
      Long userId, Long tenantId, TenantRole role, Long invitationId, LocalDateTime consumedAt) {
    userTenantManagementPort.addMember(userId, tenantId, role);
    invitationPort.markUsed(invitationId, consumedAt);
  }
}
