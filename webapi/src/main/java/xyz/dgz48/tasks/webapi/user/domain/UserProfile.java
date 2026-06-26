package xyz.dgz48.tasks.webapi.user.domain;

import org.jspecify.annotations.Nullable;

/**
 * ログイン中ユーザー自身のプロフィール(A-07、S-09)。{@code users} テーブルの公開項目に対応(OpenAPI {@code UserProfile})。
 *
 * <p>テナント選択状態に依存しない。所属テナント情報を含む {@code /api/auth/me}(MeResponse)とは用途が異なる。
 */
public record UserProfile(
    Long id, String email, String fullName, String fullNameKana, @Nullable String departmentName) {}
