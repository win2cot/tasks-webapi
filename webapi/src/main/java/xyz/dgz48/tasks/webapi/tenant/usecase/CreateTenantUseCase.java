package xyz.dgz48.tasks.webapi.tenant.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeConflictException;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantCodeGenerator;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;
import xyz.dgz48.tasks.webapi.tenant.domain.UserTenantStatus;

/**
 * セルフサインアップ(A-05、モデル B): 認証済み・テナント未所属でも可のユーザーが自分でテナントを作成し、初代 {@code TENANT_ADMIN} として登録される。
 *
 * <p>{@code code} は表示名の slug から自動生成し、既存と衝突する場合は {@code -2}, {@code -3} … のサフィックスを付与してリトライする (上限超過は
 * {@link TenantCodeConflictException} → 409)。並行作成による稀な一意制約違反は {@code
 * DataIntegrityViolationException} として伝播し、Controller 層の例外ハンドラで 409 にマップされる。
 *
 * <p>{@code tenants} / {@code user_tenants} は Hibernate Filter 非適用テーブルのため {@code X-Tenant-Id}
 * 未指定(TenantContext 未設定)でも安全に書き込める。新規テナントの集計値(userCount/taskCount)は自明(1 / 0)であり、COUNT クエリ(特に {@code
 * tasks})は発行しない。
 */
@Service
@RequiredArgsConstructor
public class CreateTenantUseCase {

  /** slug サフィックスリトライの上限(超過時は 409)。 */
  private static final int MAX_CODE_ATTEMPTS = 50;

  private final AdminTenantRepository adminTenantRepository;
  private final UserTenantManagementPort userTenantManagementPort;
  private final TenantCodeGenerator tenantCodeGenerator;
  private final AuditLogPort auditLogPort;
  private final Clock clock;

  @Transactional
  public CreateTenantResult execute(CreateTenantCommand cmd) {
    String code = resolveUniqueCode(cmd.name());
    Tenant created = adminTenantRepository.createTenant(code, cmd.name());
    Long tenantId = created.getId();

    userTenantManagementPort.addMember(cmd.callerId(), tenantId, TenantRole.TENANT_ADMIN);
    LocalDateTime joinedAt = LocalDateTime.now(clock);

    auditLogPort.record(
        AuditEventType.TENANT_CREATED,
        tenantId,
        cmd.callerId(),
        Map.of("code", created.getCode(), "name", created.getName()));

    // 初代 admin 登録後の集計値に補正(userCount = 1、taskCount = 0)。
    Tenant tenant =
        new Tenant(
            tenantId,
            created.getCode(),
            created.getName(),
            created.getPlan(),
            created.getStatus(),
            created.getCreatedAt(),
            created.getUpdatedAt(),
            1L,
            0L);

    TenantUserInfo initialAdmin =
        new TenantUserInfo(
            cmd.callerId(),
            cmd.callerEmail(),
            cmd.callerFullName(),
            cmd.callerDepartmentName(),
            TenantRole.TENANT_ADMIN,
            UserTenantStatus.ACTIVE,
            joinedAt);

    return new CreateTenantResult(tenant, initialAdmin);
  }

  private String resolveUniqueCode(String name) {
    String base = tenantCodeGenerator.toBaseCode(name);
    if (!adminTenantRepository.existsByCode(base)) {
      return base;
    }
    for (int suffix = 2; suffix <= MAX_CODE_ATTEMPTS; suffix++) {
      String candidate = tenantCodeGenerator.withSuffix(base, suffix);
      if (!adminTenantRepository.existsByCode(candidate)) {
        return candidate;
      }
    }
    throw new TenantCodeConflictException(base);
  }
}
