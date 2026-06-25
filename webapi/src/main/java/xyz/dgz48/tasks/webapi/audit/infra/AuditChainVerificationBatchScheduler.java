package xyz.dgz48.tasks.webapi.audit.infra;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase;

/**
 * B-05 監査ログ整合性検証バッチのスケジューラ(基本設計書 §8.1、毎日 02:00 JST、ADR-0038 §3.7)。
 *
 * <p>{@link SchedulerLock} により複数ノードでも単一ノードのみが実行する(設計規約 §7、ADR-0037)。実行時刻・タイムゾーン・有効化は {@code
 * audit.verification.batch.*} で設定可能(既定: 02:00 / Asia/Tokyo / 有効)。MDC に {@code batchId} を設定する。
 * 改ざん検知は fail-open であり、検証は監査書込をブロックしない。
 */
@Component
@ConditionalOnProperty(
    name = "audit.verification.batch.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class AuditChainVerificationBatchScheduler {

  private static final String BATCH_ID = "B-05";

  private final VerifyAuditChainUseCase verifyAuditChainUseCase;

  @Scheduled(
      cron = "${audit.verification.batch.cron:0 0 2 * * *}",
      zone = "${audit.verification.batch.zone:Asia/Tokyo}")
  @SchedulerLock(
      name = "auditChainVerificationBatch",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT30M")
  public void runAuditChainVerification() {
    MDC.put("batchId", BATCH_ID);
    try {
      verifyAuditChainUseCase.execute();
    } finally {
      MDC.remove("batchId");
    }
  }
}
