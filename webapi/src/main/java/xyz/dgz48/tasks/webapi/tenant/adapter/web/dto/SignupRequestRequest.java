package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/signup/request リクエスト(email のみ。ADR-0040 §3.3)。 */
public record SignupRequestRequest(@NotBlank @Email @Size(max = 255) String email) {}
