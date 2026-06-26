package xyz.dgz48.tasks.webapi.tenant.usecase;

import xyz.dgz48.tasks.webapi.tenant.domain.Tenant;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantUserInfo;

/** セルフサインアップ(A-05)結果。作成テナントと、初代 TENANT_ADMIN として登録されたユーザー情報を運ぶ(OpenAPI TenantCreatedResponse)。 */
public record CreateTenantResult(Tenant tenant, TenantUserInfo initialAdmin) {}
