package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import xyz.dgz48.tasks.webapi.tenant.domain.TenantRole;

/** POST /api/tenant/users/invite リクエスト(OpenAPI UserInviteRequest)。 */
public record UserInviteRequest(
    @NotBlank @Email @Size(max = 255) String email, @NotNull TenantRole role) {}
