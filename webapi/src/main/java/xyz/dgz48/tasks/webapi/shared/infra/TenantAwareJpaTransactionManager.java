package xyz.dgz48.tasks.webapi.shared.infra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;

/**
 * トランザクション開始時に Hibernate Filter "tenantFilter" を自動有効化する JpaTransactionManager 拡張。
 *
 * <p>TenantContext に tenantId が設定されている場合のみフィルタを有効化するため、 TenantContext
 * が未設定の管理系操作・テスト用トランザクションには影響しない。
 */
class TenantAwareJpaTransactionManager extends JpaTransactionManager {

  TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
    super(emf);
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    super.doBegin(transaction, definition);
    Long tenantId = TenantContext.get();
    if (tenantId == null) {
      return;
    }
    EntityManagerFactory emf = getEntityManagerFactory();
    if (emf == null) {
      return;
    }
    EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
    if (em == null) {
      return;
    }
    em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
  }
}
