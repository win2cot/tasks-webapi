package xyz.dgz48.tasks.webapi.notification.domain;

import java.util.List;

/**
 * 1 受信者(テナント × ユーザー)あての期限当日通知(B-01 / F-18)。
 *
 * <p>1 ユーザーが複数テナントに所属する場合はテナントごとに別通知になる(通知設定・対象タスクはテナント単位)。{@code tasks} は当該ユーザーが <b>所有者または担当者</b>
 * である当日期限の未完了タスク(ADR-0005 §3.4、関係者は対象外)。
 */
public record DueTodayNotification(
    Long tenantId, Long userId, String email, String fullName, List<DueTask> tasks) {

  /** 通知メール 1 行分のタスク要約。タイトルは PII を含みうるため本文専用でログには出力しない。 */
  public record DueTask(Long taskId, String title) {}
}
