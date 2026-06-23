package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.audit.domain.AuditEventType;
import xyz.dgz48.tasks.webapi.audit.usecase.AuthorizationDeniedAuditService;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;

/** 認可違反(403)時に {@link ErrorResponse} 形式の JSON を返す(ADR-0011 / 設計規約 §2.3)。 */
@Component
@RequiredArgsConstructor
public class TasksAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(TasksAccessDeniedHandler.class);

  private final JsonMapper objectMapper;
  private final AuthorizationDeniedAuditService authorizationDeniedAuditService;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    recordRoleBasedDenied(request);
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(AppZones.JST),
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            ErrorCode.E_FORBIDDEN,
            "アクセス権限がありません",
            request.getRequestURI());
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }

  /**
   * ロール権限不足(SaaS Admin 専用 API 等)を {@code ROLE_BASED_DENIED} として記録する(§6.2.3)。記録失敗で 本来の 403
   * 応答を妨げないよう、例外はログ出力に留める。
   */
  private void recordRoleBasedDenied(HttpServletRequest request) {
    try {
      authorizationDeniedAuditService.record(
          AuditEventType.ROLE_BASED_DENIED,
          TenantContext.get(),
          currentUserId(),
          Map.of("method", request.getMethod(), "path", request.getRequestURI()));
    } catch (RuntimeException e) {
      log.warn("ROLE_BASED_DENIED の監査記録に失敗しました", e);
    }
  }

  private static @Nullable Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof TasksAuthenticationToken token) {
      return token.getPrincipal().getId();
    }
    return null;
  }
}
