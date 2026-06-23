package xyz.dgz48.tasks.webapi.security.adapter.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuditLogPort;

/** {@link TasksAuthenticationEntryPoint} の LOGIN_FAILED 記録(#734)を検証する。 */
class TasksAuthenticationEntryPointTest {

  private AuditLogPort auditLogPort;
  private TasksAuthenticationEntryPoint entryPoint;
  private HttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() throws IOException {
    auditLogPort = mock(AuditLogPort.class);
    entryPoint = new TasksAuthenticationEntryPoint(JsonMapper.builder().build(), auditLogPort);
    request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/tasks");
    response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
  }

  @Test
  void commence_userNotRegistered_recordsLoginFailed() throws IOException {
    entryPoint.commence(request, response, new UserNotRegisteredException());
    verifyLoginFailedRecorded("USER_NOT_REGISTERED");
  }

  @Test
  void commence_userInactive_recordsLoginFailed() throws IOException {
    entryPoint.commence(request, response, new UserInactiveException());
    verifyLoginFailedRecorded("USER_INACTIVE");
  }

  @Test
  void commence_userAnonymized_recordsLoginFailed() throws IOException {
    entryPoint.commence(request, response, new UserAnonymizedException());
    verifyLoginFailedRecorded("USER_ANONYMIZED");
  }

  @Test
  void commence_otherAuthException_doesNotRecord() throws IOException {
    AuthenticationException other = new BadCredentialsException("bad");
    entryPoint.commence(request, response, other);
    verifyNoInteractions(auditLogPort);
  }

  @Test
  void commence_alwaysReturns401() throws IOException {
    entryPoint.commence(request, response, new UserAnonymizedException());
    verify(response).setStatus(401);
  }

  private void verifyLoginFailedRecorded(String reason) {
    // tenant_id / user_id は認証未確立のため null、reason を detail に残す(4 引数 default を使用)。
    verify(auditLogPort)
        .record(eq(AuditEventType.LOGIN_FAILED), isNull(), isNull(), eq(Map.of("reason", reason)));
  }
}
