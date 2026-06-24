package xyz.dgz48.tasks.webapi.dashboard.usecase;

import java.time.LocalDate;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardSummary;
import xyz.dgz48.tasks.webapi.dashboard.domain.DashboardTaskSections;

/**
 * ダッシュボード集計の取得ポート(クリーンアーキの out port)。
 *
 * <p>実装は adapter.persistence。テナント分離は Hibernate Filter(ADR-0010)が自動付与し、visibility 3 役割評価(ADR-0005)は
 * クエリ側で適用する。両エンドポイントは同一の認可済みタスク集合を対象とする。
 */
public interface DashboardQueryPort {

  /**
   * S-03 の 4 セクション(overdue / today / upcoming / completedToday)を取得する。
   *
   * <p>対象は <b>ログイン中ユーザーが所有者または担当者</b>(owner OR assignee の論理和)のタスク。各セクションは DB 側でソート済み。
   *
   * @param userId ログイン中ユーザー ID
   * @param today サーバ側システム日付(JST)
   * @param dueWithinDays {@code upcoming} の先読み日数(今日 &lt; dueDate ≤ 今日 + dueWithinDays)
   */
  DashboardTaskSections findTaskSections(Long userId, LocalDate today, int dueWithinDays);

  /**
   * S-03 の数値カード集計を取得する。
   *
   * <p>集計対象は §6.2.1 の 3 役割評価で参照可能なタスク集合(ADR-0005)。
   *
   * @param userId ログイン中ユーザー ID
   * @param today サーバ側システム日付(JST)
   */
  DashboardSummary aggregateSummary(Long userId, LocalDate today);
}
