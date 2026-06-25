package xyz.dgz48.tasks.webapi.shared.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;

/**
 * 全 JPA エンティティが Hibernate Filter 付与基準を満たすことを CI で静的検証するアーキテクチャテスト。
 *
 * <p>設計規約 §3.3.1 / ADR-0010 §6.1 で定めた除外テーブル一覧に基づき、各 JPA エンティティが 「{@link TenantFilteredEntity}
 * を継承する」か「除外リスト内にある」かのいずれかであることを検証する。
 *
 * <p>新規 JPA エンティティを追加した際に、この基準を外れた場合にテストが fail することで付与漏れを防ぐ。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class HibernateFilterEntityAuditTest {

  /**
   * Hibernate Filter を適用しない除外エンティティの simple class name 一覧。
   *
   * <p>設計規約 §3.3.1 / ADR-0010 §6.1 の除外テーブル一覧に対応する。
   */
  private static final Set<String> FILTER_EXCLUDED_ENTITY_NAMES =
      Set.of(
          "TenantJpaEntity", // tenants: マスタテーブル、tenant_id 列なし
          "UserJpaEntity", // users: プラットフォーム横断ユーザー、tenant_id 列なし
          "UserTenantJpaEntity", // user_tenants: TenantContext 確立前にクロステナント参照が必要
          "AppAdminUserJpaEntity", // app_admin_users: SaaS Admin ユーザー管理、tenant_id 列なし
          "AuditLogJpaEntity", // audit_logs: tenant_id が nullable、テナント範囲を超えた参照が必要
          "ChainHeadJpaEntity" // chain_heads: chain_key(=tenant_id/0)が PK、横断連鎖も持つ補助表(ADR-0038)
          );

  @Autowired EntityManagerFactory emf;

  @Test
  void allEntitiesAreEitherTenantFilteredOrExplicitlyExcluded() {
    Set<EntityType<?>> allEntities = emf.getMetamodel().getEntities();

    Set<String> violations =
        allEntities.stream()
            .filter(
                et -> {
                  Class<?> javaType = et.getJavaType();
                  boolean isFiltered = TenantFilteredEntity.class.isAssignableFrom(javaType);
                  boolean isExcluded =
                      FILTER_EXCLUDED_ENTITY_NAMES.contains(javaType.getSimpleName());
                  return !isFiltered && !isExcluded;
                })
            .map(et -> et.getJavaType().getName())
            .collect(Collectors.toSet());

    assertThat(violations)
        .as(
            "以下の JPA エンティティは TenantFilteredEntity を継承しておらず、除外リストにも存在しません。"
                + " 設計規約 §3.3.1 の付与基準を確認し、業務エンティティなら TenantFilteredEntity を継承するか、"
                + " 除外テーブルなら FILTER_EXCLUDED_ENTITY_NAMES に追加してください: %s",
            violations)
        .isEmpty();
  }
}
