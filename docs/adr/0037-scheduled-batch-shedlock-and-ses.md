# ADR-0037: スケジュールバッチ基盤(ShedLock)と通知メール送出(Amazon SES)

- **Status**: Accepted
- **Date**: 2026-06-25
- **Deciders**: win2cot
- **Tags**: architecture, batch, infrastructure, dependencies

## 1. コンテキスト(Context)

B-01 期限当日通知メール(F-18、Issue #750)を実装するにあたり、次の基盤が必要になる(基本設計書 §8 / 設計規約 §7):

- **スケジュール実行**: 毎日 09:00 JST に当日期限タスクの通知を送出する。
- **多重起動制御**: 本番は複数 ECS Fargate タスクで冗長化されるため、同一バッチが複数ノードで同時実行されないようにする。
- **メール送出**: 基本設計書 §2 のとおり Amazon SES を用いる。
- **ローカル/テスト**: SES 認証情報なしで起動・テストできること(SES はモック/フォールバック)。
- **GraalVM Native Image**: 本番は native image でビルドするため、追加ライブラリが AOT/native と両立すること。

新規ライブラリ(ShedLock / AWS SDK SES v2)の導入を伴うため ADR を起票する(CLAUDE.md 規約)。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: Spring `@Scheduled` + ShedLock(JDBC プロバイダ)+ AWS SDK SES v2(採用)

- 概要: アプリ内で `@Scheduled` によりバッチを駆動し、`@SchedulerLock` + `JdbcTemplateLockProvider`(既存 `shedlock` テーブル)で多重起動を排他。メールは SES v2 `SendEmail`。
- 利点: 設計規約 §7 / 基本設計書 §8.2 の方針そのまま。既存 `shedlock` テーブル(基本設計書 §4.2.8)・既存 DataSource・既存 AWS SDK BOM を再利用。`@ConditionalOnProperty` で SES を本番のみ有効化できる。
- 欠点: アプリプロセスにバッチ責務が同居する。ライブラリが 2 つ増える。

### 選択肢 B: ECS Scheduled Task(EventBridge)で別プロセス起動

- 概要: バッチ専用のタスク定義を EventBridge スケジュールで起動。
- 利点: アプリと分離。ShedLock 不要(単発起動)。
- 欠点: MVP には過剰。インフラ構成(別タスク定義・IAM・イメージ起動引数)が増え、Sprint 4 のスコープを超える。基本設計書 §8.2 も「@Scheduled で実装」を主方針としている。

### 選択肢 C: `@Scheduled` のみ(ロックなし)

- 概要: ShedLock を入れず単純にスケジュール。
- 欠点: 複数ノードで同一受信者に重複送信が起きる。受け入れ条件「ShedLock で単一ノード実行が担保」を満たさない。

## 3. 決定(Decision)

**採用**: 選択肢 A。`@Scheduled` + ShedLock(`shedlock-spring` / `shedlock-provider-jdbc-template` 6.10.0)+ AWS SDK `sesv2`(既存 BOM 2.46.15)。

SES クライアントとその送出実装は `notification.email.provider=ses` のときのみ生成し、既定(`log`)はローカル/テスト用のログフォールバック(`LoggingEmailSender`)を使う。

## 4. 理由(Rationale)

- 設計規約 §7・基本設計書 §8.2 が `@Scheduled` + ShedLock を主方針として明記しており、最小の追加で要件を満たす。
- `shedlock` テーブル・DataSource・AWS SDK BOM が既存。`JdbcTemplateLockProvider#usingDbTime()` で DB 時刻基準ロックとし、ノード間クロックスキューに依存しない。
- `@ConditionalOnProperty` により SES を本番限定で有効化でき、ローカル/CI は認証情報なしで動作(テストは `EmailSenderPort` をモック、AC「SES はモック」)。
- ShedLock 6.x / AWS SDK SES v2 は AOT/native 対応。`processTestAot` / `compileAotTestJava` がローカル check で成功することを確認済み(native build は CI ゲートで最終確認)。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 多重起動が構造的に排他され、重複送信を防止。
- メール経路が設定で切替可能(本番 SES / 非本番ログ)。新規マイグレーション不要(既存テーブル使用)。

### 悪い影響・制約(Negative)

- 依存が 2 つ増える(ShedLock 2 アーティファクト + `sesv2`)。native image の到達可能性メタデータに依存するため、ライブラリ更新時は native build で回帰確認が必要。
- アプリプロセスにバッチが同居する。将来バッチが増えて負荷分離が必要になれば選択肢 B(EventBridge 起動)へ移行余地を残す。

### 既存ドキュメント・規約への波及

- 基本設計書 §8.2 は「tenant_id ループで処理」と例示するが、本実装は **全テナント横断の単一 native クエリ** で対象を抽出し、結果を `(tenant_id, user_id)` でグループ化してテナント単位に分離する(設計規約 §3.3 native 許容(1)= モジュール境界越えのバッチ集計)。バッチには `TenantContext` が無く Hibernate Filter(ADR-0010)が適用されないため、native クエリで `tenant_id` を明示保持する。テナント分離の意図(§8.2)は維持される。

## 6. 実装メモ(Implementation Notes)

- `notification` feature(クリーンアーキ4層)に閉じる。他 feature の Java 型は参照せず、`tasks` / `users` / `user_notification_settings` への参照は native クエリのみ(モジュール自己完結)。
- スケジューラ: `@Scheduled(cron="${notification.batch.cron:0 0 9 * * *}", zone="${notification.batch.zone:Asia/Tokyo}")` + `@SchedulerLock(name="dueTodayNotificationBatch", lockAtLeastFor=PT1M, lockAtMostFor=PT10M)`。MDC に `batchId=B-01` を設定(設計規約 §7)。`notification.batch.enabled`(既定 true)で無効化可能。
- メール送出はトランザクション外で受信者単位に実行し、1 件の失敗が他をブロックしない。ログに PII(宛先・タスクタイトル)を出力しない(AC)。
- 抽出条件は ADR-0005 §3.4(F-18: 所有者・担当者のみ、関係者対象外)。`email_due_today` が偽の受信者・INACTIVE/匿名化ユーザー・DONE/未来日タスクは除外。
- MVP 対象は B-01 のみ。`email_overdue`(B-02)・`email_stakeholder` 等は後続スコープ。

## 7. 参考リンク(References)

- Issue #750(通知バッチ)/ 要件 F-18(要件定義書 §3.7)
- 基本設計書 §8(バッチ設計)/ §4.2.7(user_notification_settings)/ §4.2.8(shedlock)
- 設計規約 §7(バッチ設計規約)/ §3.3(クエリ実装優先順位・native 許容)
- ADR-0005 §3.4(通知ルール)/ ADR-0010(Hibernate Filter テナント分離)
- ShedLock: https://github.com/lukas-krecan/ShedLock
