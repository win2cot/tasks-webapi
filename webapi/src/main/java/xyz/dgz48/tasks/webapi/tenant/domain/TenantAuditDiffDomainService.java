package xyz.dgz48.tasks.webapi.tenant.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * テナント属性更新差分を field-by-field で計算する純粋関数ドメインサービス(ADR-0013)。
 *
 * <p>Spring 非依存。{@code TenantInfraConfig} で {@code @Bean} 登録する。
 */
public class TenantAuditDiffDomainService {

  /**
   * 旧テナントと更新コマンドを比較し、変更があったフィールドの差分リストを返す。
   *
   * <p>現フェーズで更新可能なフィールドは {@code name} のみ(ADR-0020 §3.3)。
   */
  public List<FieldChange> diff(Tenant previous, TenantUpdateCommand cmd) {
    List<FieldChange> changes = new ArrayList<>();
    if (!cmd.name().equals(previous.getName())) {
      changes.add(new FieldChange("name", previous.getName(), cmd.name()));
    }
    return changes;
  }
}
