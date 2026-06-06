package xyz.dgz48.tasks.webapi.user.domain;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * User ドメインモデル(JPA 非依存 POJO)。 設計規約 §1.1 のクリーンアーキ 4 層に従い domain 層に配置。 Persistence 層では {@code
 * UserJpaEntity} と相互変換する。
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

  @EqualsAndHashCode.Include private final Long id;
  private String oidcSub;
  private String email;
  private String fullName;
  private String fullNameKana;
  @Nullable private String departmentName;
  private UserStatus status;
  private long version;
  @Nullable private LocalDateTime deletedAt;

  public User(
      Long id,
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName,
      UserStatus status,
      long version,
      @Nullable LocalDateTime deletedAt) {
    this.id = id;
    this.oidcSub = oidcSub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
    this.status = status;
    this.version = version;
    this.deletedAt = deletedAt;
  }

  /**
   * 論理削除 + 個人情報匿名化(ADR-0006 §3.4 ステップ 1〜6)。
   *
   * <p>step 7(version の自動 increment)は JPA {@code @Version} により保存時に自動適用される。 step 8(audit_logs への
   * ANONYMIZE 記録)は {@link UserAnonymizationDomainService} の TODO コメントを参照(#144 依存)。
   */
  public void anonymize(LocalDateTime now) {
    this.deletedAt = now;
    this.email = "__deleted__" + id + "@deleted.invalid";
    this.oidcSub = "__deleted__" + id;
    this.fullName = "__deleted__";
    this.fullNameKana = "__deleted__";
    this.departmentName = null;
  }
}
