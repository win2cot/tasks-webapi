package xyz.dgz48.tasks.webapi.user.usecase;

import org.jspecify.annotations.Nullable;

/**
 * 会員登録プリミティブ({@link RegisterMemberUseCase})の入力(ADR-0040 §3.3)。
 *
 * @param email 登録する email(Keycloak username 兼 correlation キー)
 * @param fullName 氏名
 * @param fullNameKana 氏名(カナ)
 * @param departmentName 部署名(任意)
 * @param rawPassword 設定するパスワード(平文。ログ出力禁止)
 */
public record RegisterMemberCommand(
    String email,
    String fullName,
    String fullNameKana,
    @Nullable String departmentName,
    String rawPassword) {}
