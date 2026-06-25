package xyz.dgz48.tasks.webapi.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase;

/**
 * 同一連鎖への並行 INSERT が分岐しないことの統合テスト(ADR-0038 §3.4)。
 *
 * <p>{@code chain_heads} 行の {@code SELECT ... FOR UPDATE} による直列化を実 MySQL(Testcontainers)で検証する。
 * 並行書込後に {@code chain_seq} が 1..N の連続値(ギャップ・重複なし)であり、検証が整合することを確認する。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class AuditChainConcurrentInsertIT {

  private static final long TENANT = 9300L;
  private static final int THREADS = 6;
  private static final int PER_THREAD = 4;
  private static final int TOTAL = THREADS * PER_THREAD;

  @Autowired AuditLogPort auditLogPort;
  @Autowired VerifyAuditChainUseCase verifyAuditChainUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  @AfterEach
  void cleanChainTables() {
    jdbcTemplate.execute("DELETE FROM audit_anchors");
    jdbcTemplate.execute("DELETE FROM audit_logs");
    jdbcTemplate.execute("DELETE FROM chain_heads");
  }

  @Test
  void concurrentInserts_produceContiguousChainSeq_andVerifyClean() throws InterruptedException {
    var startGate = new CountDownLatch(1);
    var done = new CountDownLatch(THREADS);
    var errors = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int t = 0; t < THREADS; t++) {
        var unused =
            pool.submit(
                () -> {
                  try {
                    startGate.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                      // record() は REQUIRES 外呼び出しのためスレッドごとに独立 tx を開始する。
                      auditLogPort.record(AuditEventType.TASK_UPDATED, TENANT, 1L, Map.of("x", i));
                    }
                  } catch (RuntimeException | InterruptedException e) {
                    errors.incrementAndGet();
                  } finally {
                    done.countDown();
                  }
                });
      }
      startGate.countDown();
      assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(errors.get()).isZero();

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?", Long.class, TENANT);
    Long distinct =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT chain_seq) FROM audit_logs WHERE tenant_id = ?",
            Long.class,
            TENANT);
    Long maxSeq =
        jdbcTemplate.queryForObject(
            "SELECT MAX(chain_seq) FROM audit_logs WHERE tenant_id = ?", Long.class, TENANT);
    Long minSeq =
        jdbcTemplate.queryForObject(
            "SELECT MIN(chain_seq) FROM audit_logs WHERE tenant_id = ?", Long.class, TENANT);

    assertThat(count).isEqualTo((long) TOTAL);
    assertThat(distinct).isEqualTo((long) TOTAL); // 重複なし
    assertThat(minSeq).isEqualTo(1L);
    assertThat(maxSeq).isEqualTo((long) TOTAL); // ギャップなし(連続)

    // 直列化されていれば連鎖は整合する。
    assertThat(verifyAuditChainUseCase.execute().mismatches()).isEmpty();
  }
}
