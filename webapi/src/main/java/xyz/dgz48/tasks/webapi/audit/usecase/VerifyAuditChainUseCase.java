package xyz.dgz48.tasks.webapi.audit.usecase;

import io.micrometer.observation.annotation.Observed;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;

/**
 * B-05 監査ログ整合性検証ユースケース(ADR-0038 §3.7 / 基本設計書 §8.1)。
 *
 * <p>全 {@code chain_key} を {@link AuditChainVerifier} で個別に検証する。改ざん検知は <b>fail-open</b> であり、 1
 * 連鎖の検証失敗が他連鎖を止めない(例外は捕捉してログのみ)。検出した不整合は {@link AuditChainAlertPort} で通知し、 戻り値の集計でも返す(バッチ・テストの観測用)。
 */
@Service
@RequiredArgsConstructor
public class VerifyAuditChainUseCase {

  private static final Logger log = LoggerFactory.getLogger(VerifyAuditChainUseCase.class);

  private final AuditChainQueryPort queryPort;
  private final AuditChainVerifier verifier;
  private final AuditChainAlertPort alertPort;

  @Observed(name = "audit.chainVerification")
  public AuditChainVerificationSummary execute() {
    List<Long> chainKeys = queryPort.findActiveChainKeys();
    List<AuditChainMismatch> mismatches = new ArrayList<>();
    int clean = 0;
    for (long chainKey : chainKeys) {
      try {
        List<AuditChainMismatch> chainMismatches = verifier.verify(chainKey);
        if (chainMismatches.isEmpty()) {
          clean++;
        } else {
          for (AuditChainMismatch mismatch : chainMismatches) {
            alertPort.alert(mismatch);
          }
          mismatches.addAll(chainMismatches);
        }
      } catch (RuntimeException e) {
        // fail-open: 1 連鎖の検証エラーが他連鎖の検証を止めないようにする。
        log.error("監査連鎖の検証中にエラーが発生しました chainKey={}: {}", chainKey, e.getMessage(), e);
      }
    }

    var summary =
        new AuditChainVerificationSummary(chainKeys.size(), clean, List.copyOf(mismatches));
    log.info(
        "監査連鎖検証バッチ完了 chains={} clean={} mismatches={}",
        summary.chainsChecked(),
        summary.chainsClean(),
        summary.mismatches().size());
    return summary;
  }

  /**
   * 検証結果の集計(ログ・監視・テスト用)。
   *
   * @param chainsChecked 検証対象の連鎖数
   * @param chainsClean 整合していた連鎖数
   * @param mismatches 検出した不整合
   */
  public record AuditChainVerificationSummary(
      int chainsChecked, int chainsClean, List<AuditChainMismatch> mismatches) {}
}
