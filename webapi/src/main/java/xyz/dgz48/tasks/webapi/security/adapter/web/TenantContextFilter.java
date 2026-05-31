package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;

/**
 * X-Tenant-Id ヘッダを読み取り、user_tenants を検証して TenantContext を設定する。
 *
 * <p>ヘッダ未指定の場合は TenantContext を設定せずに通過させる。認証済みユーザーが指定テナントの ACTIVE メンバーでない場合は 403 を返す。メンバーの場合は
 * ROLE_TENANT_ADMIN または ROLE_MEMBER を SecurityContext に付与する。
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

    Optional<TenantRole> roleOpt =
        tenantMembershipPort.findActiveRole(token.getPrincipal().getId(), tenantId);
    if (roleOpt.isEmpty()) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Not a member of the specified tenant");
      return;
    }

    SecurityContextHolder.getContext().setAuthentication(withTenantRole(token, roleOpt.get()));
    TenantContext.set(tenantId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static TasksAuthenticationToken withTenantRole(
      TasksAuthenticationToken existing, TenantRole role) {
    if (role == TenantRole.SAAS_ADMIN) {
      // SaaS Admin は Keycloak が管理し user_tenants には存在しない(設計規約 §5.2)
      throw new IllegalArgumentException("SAAS_ADMIN は user_tenants から返却されない");
    }
    List<GrantedAuthority> authorities = new ArrayList<>(existing.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
    return new TasksAuthenticationToken(existing.getPrincipal(), authorities);
  }
}
