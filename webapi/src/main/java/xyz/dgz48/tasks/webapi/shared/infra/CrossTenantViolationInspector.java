package xyz.dgz48.tasks.webapi.shared.infra;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.shared.domain.CrossTenantViolationDetectedEvent;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.domain.TenantFilterBypassContext;

/**
 * Hibernate Filter が未設定の状態で tenant-filtered テーブルへの SQL が発行された場合を検知する {@link StatementInspector}。
 *
 * <p>検知条件:
 *
 * <ol>
 *   <li>{@link TenantContext#get()} が {@code null}(フィルタ未設定)
 *   <li>{@link TenantFilterBypassContext#isActive()} が {@code false}(D-1 正当 bypass でない)
 *   <li>SQL が {@link #TENANT_FILTERED_TABLES} に含まれるテーブル名を参照している
 * </ol>
 *
 * <p>検知時は WARN ログを出力し {@link CrossTenantViolationDetectedEvent} を発火する。 イベントは {@code
 * CrossTenantViolationAuditService} が {@code REQUIRES_NEW} トランザクションで {@code audit_logs} に記録する。
 *
 * <p>{@link #IN_VIOLATION_HANDLING} フラグで再帰的な発火(audit 書き込み中の Inspector 呼び出し)を防止する。
 *
 * <p><strong>テーブルセットの保守</strong>: {@code TenantFilteredEntity} サブクラスのテーブルが増えたら {@code
 * TENANT_FILTERED_TABLES} に追加すること。{@code HibernateFilterEntityAuditTest} が CI でサブクラスの付与漏れを検知する。
 */
@Component
class CrossTenantViolationInspector implements StatementInspector, HibernatePropertiesCustomizer {

  private static final Logger log = LoggerFactory.getLogger(CrossTenantViolationInspector.class);

  private static final ThreadLocal<Boolean> IN_VIOLATION_HANDLING =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  /**
   * Hibernate Filter 対象テーブル名セット。{@code TenantFilteredEntity} サブクラスのテーブルをすべて列挙する (ADR-0010
   * §6.1)。新規テーブル追加時はここも更新すること。
   */
  private static final Set<String> TENANT_FILTERED_TABLES = Set.of("tasks");

  private static final Pattern TABLE_PATTERN;

  static {
    String alternation = String.join("|", TENANT_FILTERED_TABLES);
    TABLE_PATTERN = Pattern.compile("\\b(" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
  }

  private final ApplicationEventPublisher eventPublisher;

  CrossTenantViolationInspector(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public @Nullable String inspect(String sql) {
    if (Boolean.TRUE.equals(IN_VIOLATION_HANDLING.get())) {
      return sql;
    }
    if (TenantContext.get() != null) {
      return sql;
    }
    if (TenantFilterBypassContext.isActive()) {
      return sql;
    }

    var matcher = TABLE_PATTERN.matcher(sql);
    if (!matcher.find()) {
      return sql;
    }

    String tableName = matcher.group(1);
    String sqlType = detectSqlType(sql);

    log.warn(
        "クロステナント違反検知: TenantContext 未設定で tenant-filtered テーブルへの SQL 発行 table={} sqlType={}",
        tableName,
        sqlType);

    IN_VIOLATION_HANDLING.set(Boolean.TRUE);
    try {
      eventPublisher.publishEvent(
          new CrossTenantViolationDetectedEvent(null, null, tableName, sqlType));
    } finally {
      IN_VIOLATION_HANDLING.remove();
    }

    return sql;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put("hibernate.session_factory.statement_inspector", this);
  }

  private static String detectSqlType(String sql) {
    String upper = sql.stripLeading().toUpperCase(Locale.ROOT);
    if (upper.startsWith("SELECT")) return "SELECT";
    if (upper.startsWith("INSERT")) return "INSERT";
    if (upper.startsWith("UPDATE")) return "UPDATE";
    if (upper.startsWith("DELETE")) return "DELETE";
    return "UNKNOWN";
  }
}
