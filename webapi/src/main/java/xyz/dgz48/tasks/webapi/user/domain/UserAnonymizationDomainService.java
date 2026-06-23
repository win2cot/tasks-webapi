package xyz.dgz48.tasks.webapi.user.domain;

import java.time.LocalDateTime;

/**
 * ユーザー匿名化処理 SSOT — ADR-0006 §3.4 参照。
 *
 * <p>Spring 非依存の純粋関数。SPI(Sprint 1 Infra)および本システム API(将来の解約・退会 API)の 両方から {@code new
 * UserAnonymizationDomainService()} でインスタンス化して呼び出す。
 *
 * <p>ADR-0006 §3.4 の 8 ステップ匿名化処理のうち、step 1〜6 は {@link User#anonymize(LocalDateTime)} に委譲する。 step
 * 7(version の自動 increment)は JPA {@code @Version} により保存時に自動適用される。
 */
public class UserAnonymizationDomainService {

  /**
   * ユーザーを匿名化する(ADR-0006 §3.4 step 1〜7)。
   *
   * <p>呼び出し元(UseCase 層)は {@code @Transactional} 境界内でこのメソッドを呼び出し、 完了後に {@code UserJpaEntity} に反映して
   * repository.save() を実行すること。
   *
   * @param user 匿名化対象の User ドメインオブジェクト
   * @param now 論理削除日時(呼び出し元で {@code LocalDateTime.now(clock)} を渡すこと)
   */
  public void anonymize(User user, LocalDateTime now) {
    user.anonymize(now);
    // step 8(audit_logs への ANONYMIZE 記録)は本ドメインサービスでは行わない。
    // ドメイン層は Spring 非依存・AuditLogPort(usecase 層)に依存できないため、WebAPI 起点の匿名化
    // では呼び出し側 UseCase が同 transaction 内で AuditEventType.ANONYMIZE(entity_type='users',
    // entity_id=user.getId())を記録する責務を持つ。ただし WebAPI 側の匿名化呼び出し経路(解約・退会 API)は
    // 未実装のため、この記録は将来の解約・退会 API 実装時(Phase 2 / #167 圏)に委譲する(TODO 追跡を残す)。
    // 一方、現状実際に走る Keycloak SPI 起点の匿名化は keycloak サブプロジェクトの UserRepository#anonymize が
    // JDBC で同一内容(action='ANONYMIZE')を記録する(#734 で実装済み / ADR-0006 §3.4)。
  }
}
