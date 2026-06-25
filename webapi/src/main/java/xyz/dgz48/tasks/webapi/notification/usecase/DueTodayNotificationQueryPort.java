package xyz.dgz48.tasks.webapi.notification.usecase;

import java.time.LocalDate;
import java.util.List;
import xyz.dgz48.tasks.webapi.notification.domain.DueTodayNotification;

/** 期限当日通知(B-01)の対象受信者を抽出する out port。実装は adapter.persistence。 */
public interface DueTodayNotificationQueryPort {

  /**
   * 当日(JST)期限の未完了タスクについて、通知対象となる受信者を抽出する。
   *
   * <p>抽出条件(ADR-0005 §3.4 / 基本設計書 §8.1 B-01):
   *
   * <ul>
   *   <li>{@code due_date = today} かつ {@code status <> DONE} かつ未削除のタスク
   *   <li>受信者はそのタスクの <b>所有者または担当者</b>(関係者は対象外)
   *   <li>受信者の {@code user_notification_settings.email_due_today} が真(行が無い場合は既定 TRUE)
   *   <li>受信者ユーザーが ACTIVE かつ未匿名化
   * </ul>
   *
   * <p>テナント横断の抽出だが、各受信者の通知は所属テナント単位で分離される(テナント分離維持)。
   *
   * @param today サーバ側システム日付(JST)
   * @return テナント × ユーザー単位にまとめた通知(タスク 0 件の受信者は含まない)
   */
  List<DueTodayNotification> findDueTodayRecipients(LocalDate today);
}
