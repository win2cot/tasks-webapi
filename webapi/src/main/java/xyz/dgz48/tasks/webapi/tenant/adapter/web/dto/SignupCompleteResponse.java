package xyz.dgz48.tasks.webapi.tenant.adapter.web.dto;

/**
 * POST /api/signup/{token}/complete レスポンス(ADR-0040 §3.3)。完了後はログインし、既存 {@code POST /api/tenants} で
 * テナントを作成する。
 *
 * @param userId 登録されたユーザー id
 */
public record SignupCompleteResponse(Long userId) {}
