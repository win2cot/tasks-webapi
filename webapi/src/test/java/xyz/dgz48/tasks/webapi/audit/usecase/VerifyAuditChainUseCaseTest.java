package xyz.dgz48.tasks.webapi.audit.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch;
import xyz.dgz48.tasks.webapi.audit.domain.AuditChainMismatch.Reason;
import xyz.dgz48.tasks.webapi.audit.usecase.VerifyAuditChainUseCase.AuditChainVerificationSummary;

/** {@link VerifyAuditChainUseCase} のユニットテスト(集約・fail-open のオーケストレーション)。 */
@ExtendWith(MockitoExtension.class)
class VerifyAuditChainUseCaseTest {

  @Mock AuditChainQueryPort queryPort;
  @Mock AuditChainVerifier verifier;
  @Mock AuditChainAlertPort alertPort;

  private VerifyAuditChainUseCase useCase() {
    return new VerifyAuditChainUseCase(queryPort, verifier, alertPort);
  }

  @Test
  void execute_aggregatesCleanAndMismatchedChains_andAlertsEachMismatch() {
    var mismatch = new AuditChainMismatch(2L, 3L, Reason.HASH_MISMATCH);
    when(queryPort.findActiveChainKeys()).thenReturn(List.of(1L, 2L));
    when(verifier.verify(1L)).thenReturn(List.of());
    when(verifier.verify(2L)).thenReturn(List.of(mismatch));

    AuditChainVerificationSummary summary = useCase().execute();

    assertThat(summary.chainsChecked()).isEqualTo(2);
    assertThat(summary.chainsClean()).isEqualTo(1);
    assertThat(summary.mismatches()).containsExactly(mismatch);
    verify(alertPort).alert(mismatch);
  }

  @Test
  void execute_isFailOpen_whenOneChainThrows() {
    when(queryPort.findActiveChainKeys()).thenReturn(List.of(3L));
    when(verifier.verify(3L)).thenThrow(new RuntimeException("DB error"));

    AuditChainVerificationSummary summary = useCase().execute();

    // 例外は捕捉され、バッチは完了する(他連鎖をブロックしない)。
    assertThat(summary.chainsChecked()).isEqualTo(1);
    assertThat(summary.chainsClean()).isZero();
    assertThat(summary.mismatches()).isEmpty();
    verify(alertPort, never()).alert(org.mockito.ArgumentMatchers.any());
  }
}
