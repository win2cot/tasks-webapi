package xyz.dgz48.tasks.webapi.tenant.usecase;

import org.jspecify.annotations.Nullable;

/**
 * セルフサインアップ(A-05)入力。呼び出しユーザーのプロフィール(初代 admin レスポンス組み立て用)とテナント表示名を運ぶ。
 *
 * @param callerId 呼び出しユーザー ID(初代 TENANT_ADMIN になる)
 * @param callerEmail 呼び出しユーザーのメール
 * @param callerFullName 呼び出しユーザーの氏名
 * @param callerDepartmentName 部署名(無ければ null)
 * @param name 作成するテナントの表示名
 */
public record CreateTenantCommand(
    Long callerId,
    String callerEmail,
    String callerFullName,
    @Nullable String callerDepartmentName,
    String name) {}
