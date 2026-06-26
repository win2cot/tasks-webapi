package xyz.dgz48.tasks.webapi.audit.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListAuditLogsUseCaseTest {

  @Test
  void execute_delegatesToPort() {
    AuditLogQueryPort port = mock(AuditLogQueryPort.class);
    AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(7L, null, null, null, 0, 50);
    AuditLogPage expected = new AuditLogPage(List.of(), 0);
    when(port.search(eq(criteria))).thenReturn(expected);

    ListAuditLogsUseCase useCase = new ListAuditLogsUseCase(port);

    assertThat(useCase.execute(criteria)).isSameAs(expected);
  }
}
