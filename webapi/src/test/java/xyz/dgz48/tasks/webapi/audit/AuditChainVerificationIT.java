package xyz.dgz48.tasks.webapi.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.dgz48.tasks.webapi.MockJwtDecoderConfiguration;
import xyz.dgz48.tasks.webapi.TestcontainersConfiguration;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch.Reason;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase.AuditChainVerificationSummary;

/**
 * B-05 監査ログ ハッシュチェーン検証(ADR-0038 §3.7)の統合テスト。
 *
 * <p>実テナント連鎖・プラットフォーム連鎖の連結と、改ざん注入(行改変 / 削除 / 並べ替え / 末尾切り詰め)の検出、保管削除後の チェックポイント起点検証を Testcontainers
 * MySQL で検証する。{@code audit_logs} は FK を持たない(テナント・ユーザー削除後も保持)ため、 実際のテナント / ユーザーを作らず任意 ID で連鎖を構成できる。
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MockJwtDecoderConfiguration.class})
class AuditChainVerificationIT {

  private static final long TENANT_A = 9100L;
  private static final long TENANT_B = 9200L;

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

  /** record() は @Transactional(REQUIRED) のため tx 外から呼ぶと 1 件ごとに独立コミットされる。 */
  private void writeRows(Long tenantId, int count) {
    for (int i = 0; i < count; i++) {
      auditLogPort.record(
          AuditEventType.TASK_UPDATED, tenantId, 1L, Map.of("i", i, "note", "監査" + i));
    }
  }

  @Test
  void cleanChains_acrossTenantsAndPlatform_verifyCleanAndCheckpointAppended() {
    writeRows(TENANT_A, 3);
    writeRows(TENANT_B, 2);
    writeRows(null, 2); // プラットフォーム連鎖(chain_key = 0)

    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();

    assertThat(summary.chainsChecked()).isEqualTo(3);
    assertThat(summary.chainsClean()).isEqualTo(3);
    assertThat(summary.mismatches()).isEmpty();

    // 各連鎖の末尾でチェックポイントが 1 件ずつ追記される。
    List<Map<String, Object>> anchors =
        jdbcTemplate.queryForList(
            "SELECT chain_key, seq_at_checkpoint FROM audit_anchors ORDER BY chain_key");
    assertThat(anchors).hasSize(3);
    assertThat(anchors)
        .extracting(r -> ((Number) r.get("chain_key")).longValue())
        .containsExactly(0L, TENANT_A, TENANT_B);
  }

  @Test
  void modifiedRow_isDetectedAsHashMismatch() {
    writeRows(TENANT_A, 3);
    // 2 件目の action を改変(canonical が変わり再計算ハッシュと不一致になる)。
    jdbcTemplate.update(
        "UPDATE audit_logs SET action = 'HACKED' WHERE tenant_id = ? AND chain_seq = 2", TENANT_A);

    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();

    assertThat(summary.mismatches())
        .containsExactly(new AuditChainMismatch(TENANT_A, 2L, Reason.HASH_MISMATCH));
  }

  @Test
  void deletedMiddleRow_isDetectedAsSequenceBroken() {
    writeRows(TENANT_A, 3);
    jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id = ? AND chain_seq = 2", TENANT_A);

    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();

    // seq1 の次に seq2 を期待するが seq3 が来るため SEQUENCE_BROKEN。
    assertThat(summary.mismatches())
        .containsExactly(new AuditChainMismatch(TENANT_A, 2L, Reason.SEQUENCE_BROKEN));
  }

  @Test
  void reorderedRows_areDetected() {
    writeRows(TENANT_A, 3);
    // seq2 と seq3 を入れ替える(chain_seq は一意制約を持たないため一時値経由で交換)。
    jdbcTemplate.update(
        "UPDATE audit_logs SET chain_seq = 999 WHERE tenant_id = ? AND chain_seq = 2", TENANT_A);
    jdbcTemplate.update(
        "UPDATE audit_logs SET chain_seq = 2 WHERE tenant_id = ? AND chain_seq = 3", TENANT_A);
    jdbcTemplate.update(
        "UPDATE audit_logs SET chain_seq = 3 WHERE tenant_id = ? AND chain_seq = 999", TENANT_A);

    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();

    // 並べ替えで chain_seq=2 の行内容が変わり、HMAC 再計算が格納値と不一致になる。
    assertThat(summary.mismatches()).hasSize(1);
    assertThat(summary.mismatches().get(0).chainKey()).isEqualTo(TENANT_A);
    assertThat(summary.mismatches().get(0).reason()).isEqualTo(Reason.HASH_MISMATCH);
  }

  @Test
  void truncatedTail_isDetectedAgainstChainHead() {
    writeRows(TENANT_A, 3);
    // 末尾行(seq3)を削除。chain_heads は更新されないため末尾不整合として検出される。
    jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id = ? AND chain_seq = 3", TENANT_A);

    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();

    assertThat(summary.mismatches())
        .containsExactly(new AuditChainMismatch(TENANT_A, 3L, Reason.TAIL_MISMATCH));
  }

  @Test
  void retainedAnchor_allowsVerificationAfterPrefixDeletion() {
    // 1) 5 件書込 → 検証(チェックポイント seq5 追記)。
    writeRows(TENANT_A, 5);
    assertThat(verifyAuditChainUseCase.execute().mismatches()).isEmpty();

    // 2) さらに 5 件書込(seq 6..10)。
    writeRows(TENANT_A, 5);

    // 3) B-03 を模擬: アンカー seq5 までの prefix を削除(seq <= 5)。
    jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id = ? AND chain_seq <= 5", TENANT_A);

    // 4) 再検証: retained アンカー seq5 を起点に seq6..10 を検証 → 整合。
    AuditChainVerificationSummary summary = verifyAuditChainUseCase.execute();
    assertThat(summary.mismatches()).isEmpty();
    assertThat(summary.chainsClean()).isEqualTo(1);

    // 末尾 seq10 の新チェックポイントが追記される。
    Long maxAnchorSeq =
        jdbcTemplate.queryForObject(
            "SELECT MAX(seq_at_checkpoint) FROM audit_anchors WHERE chain_key = ?",
            Long.class,
            TENANT_A);
    assertThat(maxAnchorSeq).isEqualTo(10L);
  }
}
