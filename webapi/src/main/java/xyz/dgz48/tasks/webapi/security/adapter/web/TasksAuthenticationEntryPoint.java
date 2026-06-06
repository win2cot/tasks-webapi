package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;

/** 認証失敗(401)時に {@link ErrorResponse} 形式の JSON を返す(ADR-0011 / 設計規約 §2.3)。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TasksAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

  private final JsonMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    auditLog(authException, request);

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(JST),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            ErrorCode.E_UNAUTHORIZED,
            "認証が必要です",
            request.getRequestURI());
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }

  private void auditLog(AuthenticationException authException, HttpServletRequest request) {
    if (authException instanceof UserNotRegisteredException) {
      // TODO(#144): audit_logs に action='LOGIN_FAILED', detail='USER_NOT_REGISTERED' を記録する
      log.warn("USER_NOT_REGISTERED: JWT sub に対応するユーザーが存在しません path={}", request.getRequestURI());
    } else if (authException instanceof UserInactiveException) {
      // TODO(#144): audit_logs に action='LOGIN_FAILED', detail='USER_INACTIVE' を記録する
      log.warn("USER_INACTIVE: 無効化済みユーザーの認証試行 path={}", request.getRequestURI());
    } else if (authException instanceof UserAnonymizedException) {
      // TODO(#144): audit_logs に action='LOGIN_FAILED', detail='USER_ANONYMIZED' を記録する(監査必須)
      log.warn("USER_ANONYMIZED: 匿名化済みユーザーの認証試行 path={}", request.getRequestURI());
    }
  }
}
