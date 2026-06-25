package xyz.dgz48.tasks.webapi.audit.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase.AuditChainVerificationSummary;

/** {@link AuditChainVerificationBatchScheduler} のユニットテスト。 */
@ExtendWith(MockitoExtension.class)
class AuditChainVerificationBatchSchedulerTest {

  @Mock VerifyAuditChainUseCase verifyAuditChainUseCase;

  @Test
  void runAuditChainVerification_delegatesToUseCase_andClearsMdc() {
    when(verifyAuditChainUseCase.execute())
        .thenReturn(new AuditChainVerificationSummary(0, 0, List.of()));
    var scheduler = new AuditChainVerificationBatchScheduler(verifyAuditChainUseCase);

    scheduler.runAuditChainVerification();

    verify(verifyAuditChainUseCase).execute();
    // MDC の batchId は実行後に必ず除去される。
    assertThat(MDC.get("batchId")).isNull();
  }
}
