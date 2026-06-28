package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * POST /api/signup/{token}/complete リクエスト(ADR-0040 §3.3)。
 *
 * <p>会員登録に使う profile + password。email は確認トークン側が保持するため受け取らない(なりすまし防止)。
 */
public record SignupCompleteRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Size(max = 255) String fullNameKana,
    @Nullable @Size(max = 255) String departmentName,
    @NotBlank @Size(min = 8, max = 255) String password) {}
