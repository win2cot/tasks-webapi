package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

/**
 * X-Tenant-Id ヘッダを読み取り、user_tenants を検証して TenantContext を設定する。
 *
 * <p>ヘッダ未指定の場合は TenantContext を設定せずに通過させる。 認証済みユーザーが指定テナントの ACTIVE メンバーでない場合は 403 を返す。
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

  static final String HEADER_X_TENANT_ID = "X-Tenant-Id";

  private final TenantMembershipPort tenantMembershipPort;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String tenantIdHeader = request.getHeader(HEADER_X_TENANT_ID);
    if (tenantIdHeader == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Long tenantId;
    try {
      tenantId = Long.parseLong(tenantIdHeader);
    } catch (NumberFormatException e) {
      response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid X-Tenant-Id header");
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof TasksAuthenticationToken token)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required");
      return;
    }

    if (!tenantMembershipPort.isActiveMember(token.getPrincipal().getId(), tenantId)) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Not a member of the specified tenant");
      return;
    }

    TenantContext.set(tenantId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
