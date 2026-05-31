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
    // TODO(#144): step 8 — audit_logs に action='ANONYMIZE', entity_type='users',
    // entity_id=user.getId() を記録する(#144 監査列 Auditing 機構整備と連携後に実装)
  }
}
