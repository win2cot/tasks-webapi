package xyz.dgz48.tasks.webapi.security.adapter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import xyz.dgz48.tasks.webapi.shared.domain.TenantContext;
import xyz.dgz48.tasks.webapi.shared.infra.AppZones;
import xyz.dgz48.tasks.webapi.shared.web.ErrorCode;
import xyz.dgz48.tasks.webapi.shared.web.ErrorResponse;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantMembership;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;
import xyz.dgz48.tasks.webapi.tenant.usecase.TenantMembershipPort;
import xyz.dgz48.tasks.webapi.tenant.usecase.UserTenantsResolverService;

/**
 * X-Tenant-Id ヘッダを読み取り、user_tenants を検証して TenantContext を設定する。
 *
 * <p>免除パス({@code /api/auth/**}, {@code /api/tenants/**}, {@code /api/platform/**}, {@code
 * /actuator/**}, {@code /api/users/me})はヘッダの有無に関わらず素通りさせる。stale な X-Tenant-Id が付いていても membership
 * 検証しない(#816)。これらはテナントスコープの業務データを読まないため安全。
 *
 * <p>ヘッダ指定時(非免除パス): 認証済みユーザーが指定テナントの ACTIVE メンバーでない場合は 403 を返す。メンバーの場合は ROLE_TENANT_ADMIN または
 * ROLE_MEMBER を SecurityContext に付与する。
 *
 * <p>ヘッダ未指定時: 免除パス({@code /api/auth/**}, {@code /api/tenants/**}, {@code /api/platform/**}, {@code
 * /actuator/**})および非認証リクエストはそのまま通過。それ以外の認証済みリクエストは {@link UserTenantsResolverService}
 * で初期テナントを自動解決する(ADR-0016)。 所属テナント 0 件の場合は 403 を返す。
 *
 * <p>エラー応答は ADR-0011 の {@link ErrorResponse} 形式で返す。
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

  static final String HEADER_X_TENANT_ID = "X-Tenant-Id";

  private final TenantMembershipPort tenantMembershipPort;
  private final UserTenantsResolverService userTenantsResolverService;
  private final JsonMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // テナント不要枠(基本設計書 §5.2)は X-Tenant-Id ヘッダの有無に関わらず素通りさせる。
    // ヘッダが付いていても membership 検証しないことで、ユーザー切替時に前ユーザーの stale な
    // X-Tenant-Id が残っても /api/auth/me 等が 403 にならない(#816)。これら経路はテナント
    // スコープの業務データを読まない(自分の identity / 所属一覧 / プラットフォーム API)ため安全。
    if (isExemptPath(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String tenantIdHeader = request.getHeader(HEADER_X_TENANT_ID);
    if (tenantIdHeader == null) {
      Authentication preAuth = SecurityContextHolder.getContext().getAuthentication();
      if (!(preAuth instanceof TasksAuthenticationToken preToken)) {
        filterChain.doFilter(request, response);
        return;
      }
      Optional<TenantMembership> resolved =
          userTenantsResolverService.resolveInitial(preToken.getPrincipal().getId());
      if (resolved.isEmpty()) {
        writeErrorResponse(
            response, HttpStatus.FORBIDDEN, ErrorCode.E_FORBIDDEN, "所属テナントがありません", request);
        return;
      }
      TenantMembership membership = resolved.get();
      SecurityContextHolder.getContext()
          .setAuthentication(withTenantRole(preToken, membership.role()));
      TenantContext.set(membership.tenantId());
      try {
        filterChain.doFilter(request, response);
      } finally {
        TenantContext.clear();
      }
      return;
    }

    Long tenantId;
    try {
      tenantId = Long.parseLong(tenantIdHeader);
    } catch (NumberFormatException e) {
      writeErrorResponse(
          response,
          HttpStatus.BAD_REQUEST,
          ErrorCode.E_VALIDATION,
          "X-Tenant-Id ヘッダが不正です",
          request);
      return;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof TasksAuthenticationToken token)) {
      writeErrorResponse(
          response, HttpStatus.UNAUTHORIZED, ErrorCode.E_UNAUTHORIZED, "認証が必要です", request);
      return;
    }

    Optional<TenantRole> roleOpt =
        tenantMembershipPort.findActiveRole(token.getPrincipal().getId(), tenantId);
    if (roleOpt.isEmpty()) {
      writeErrorResponse(
          response, HttpStatus.FORBIDDEN, ErrorCode.E_FORBIDDEN, "指定テナントのメンバーではありません", request);
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

  /**
   * 初期テナント自動解決を行わない免除パスか判定する。
   *
   * <p>/api/auth/** (me / select / logout)、/api/tenants/** (SaaS Admin)、/api/platform/** (SaaS
   * Admin プラットフォーム API、A-27)、/actuator/** はテナントコンテキスト不要(基本設計書 §5.2 X-Tenant-Id 不要枠)。 /api/users/me
   * (A-07 プロフィール取得) もテナント選択非依存のため免除する。ただし完全一致のみ免除し、 /api/users/me/notification-settings
   * (S-10、テナントスコープ) は免除しない。
   *
   * <p>SaaS Admin はテナント未所属が通常のため、/api/platform を免除しないと初期テナント解決で 403 になる。
   */
  private static boolean isExemptPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.startsWith("/api/auth/")
        || uri.startsWith("/api/tenants")
        || uri.startsWith("/api/platform")
        || uri.startsWith("/actuator/")
        || uri.equals("/actuator")
        || uri.equals("/api/users/me");
  }

  private void writeErrorResponse(
      HttpServletResponse response,
      HttpStatus status,
      ErrorCode code,
      String message,
      HttpServletRequest request)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    ErrorResponse errorResponse =
        new ErrorResponse(
            OffsetDateTime.now(AppZones.JST),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            request.getRequestURI());
    objectMapper.writeValue(response.getWriter(), errorResponse);
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
