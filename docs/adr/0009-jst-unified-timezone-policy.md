# ADR-0009: 全層タイムゾーンを Asia/Tokyo(JST)に統一する

- **Status**: Accepted
- **Date**: 2026-05-25
- **Deciders**: 開発チーム
- **Tags**: timezone, api, infrastructure, coding-convention

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: 全層 JST 統一](#選択肢-a-全層-jst-統一)
  - [選択肢 B: UTC 統一継続(API レスポンスも UTC)](#選択肢-b-utc-統一継続api-レスポンスも-utc)
  - [選択肢 C: 現状維持(JVM/CI/DB は JST、API レスポンスだけ UTC)](#選択肢-c-現状維持jvmcidb-は-jstapi-レスポンスだけ-utc)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響](#良い影響)
  - [悪い影響・制約](#悪い影響制約)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

tasks-webapi は日本国内向け SaaS として運用される前提である。Sprint 0 着手前(2026-05-25 時点)に以下の非対称な状態が発覚した:

- CI 環境・ECS Task・MySQL サーバーはいずれも `Asia/Tokyo` で運用
- しかし設計規約 §2.4 / コーディング規約 §12.1 では「API レスポンスの `timestamp` は UTC(`Z`) で返す」と規定
- `docs/architecture/infrastructure-plan.md` の JDBC URL に `serverTimezone=UTC`(MySQL Connector/J 8.0.23 で deprecated)が残存し、§12.1 の「古い `serverTimezone` パラメータは使わない」と矛盾

この非対称性はバグの温床となる(DB の値と API レスポンスの時刻がオフセット違いで異なって見える、シリアライズ層で暗黙変換が発生する、テストと本番で挙動が変わるなど)。

Issue #265 で「日本圏前提・全層 JST 統一」を確定方針として議論し、PR #266 で規約・infrastructure-plan を一括改訂した。

## 2. 検討した選択肢

### 選択肢 A: 全層 JST 統一

- 概要: JVM・CI・DB・API レスポンスの全層を `Asia/Tokyo` に揃える。API レスポンスの `timestamp` は `+09:00` サフィックスで返し、`Z` は禁止。
- 利点:
  - 全層の時刻表現が一致し、オフセット変換ミスが発生しない。
  - 日本人ユーザー向けに直感的な時刻が返る。
  - テストコードで `ZoneOffset.UTC` と `ZoneId.of("Asia/Tokyo")` が混在しない。
- 欠点:
  - 将来グローバル展開する場合は再設計が必要。
  - UTC ベースのログ集計ツール(CloudWatch 等)でオフセットを意識する必要がある。
- リスク・未知数: GraalVM Native Image ビルド時に `-Duser.timezone=Asia/Tokyo` が有効かは Sprint 0/1 で検証要。

### 選択肢 B: UTC 統一継続(API レスポンスも UTC)

- 概要: 全層を UTC に揃え直す。JVM・CI・DB も UTC に変更。
- 利点:
  - 国際標準に従い、将来のグローバル展開が容易。
  - UTC ベースのツールとの親和性が高い。
- 欠点:
  - CI 環境・ECS Task・MySQL はすでに `Asia/Tokyo` 前提で構築されており、変更コストが高い。
  - ユーザー向け UI で常にオフセット変換が必要になる。
  - 「日本圏前提」という製品要件に反する。

### 選択肢 C: 現状維持(JVM/CI/DB は JST、API レスポンスだけ UTC)

- 概要: 既存の非対称状態を継続する。
- 利点: 変更コストがかからない。
- 欠点:
  - 前述の通り、バグの温床。
  - コーディング規約 §12.1 と infrastructure-plan.md が矛盾したままになる。
  - レビューのたびに「なぜ UTC か」の説明を要する。

## 3. 決定

**採用**: 選択肢 A(全層 JST 統一)

具体的には:

- API レスポンスの `timestamp` は `OffsetDateTime` を `ZoneOffset.of("+09:00")` または `ZoneId.of("Asia/Tokyo")` でシリアライズし、`+09:00` サフィックスで返す。`Z` サフィックスは禁止。
- `application.yml` に `spring.jackson.time-zone: Asia/Tokyo` を設定する。
- ECS Task 定義に `TZ=Asia/Tokyo`(OS 経由)と `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Tokyo`(JVM 起動オプション)を二重設定する。
- JDBC URL の `serverTimezone=UTC` を撤去し、`connectionTimeZone=SERVER&forceConnectionTimeZoneToSession=true` に置換する。

## 4. 理由

- **製品要件との整合**: 本サービスは日本国内向け SaaS であり、ユーザーに JST の時刻を返すのが自然。
- **非対称状態の解消**: JVM・CI・DB がすでに JST で動いており、API レスポンスだけ UTC に変換する処理は「変換忘れ」バグを誘発する。
- **deprecated パラメータの除去**: MySQL Connector/J 8.0.23 以降で `serverTimezone` は deprecated。`connectionTimeZone=SERVER` へ移行することでドライバの警告を除去できる。
- **テストの信頼性向上**: CI 環境(`TZ=Asia/Tokyo`)と本番環境が一致し、タイムゾーン起因の CI-only 合格を防止できる。
- **コードの単純化**: `ZoneOffset.UTC` と `ZoneId.of("Asia/Tokyo")` の使い分け判断が不要になる。

## 5. 影響

### 良い影響

- 全層の時刻表現が統一されバグが減る。
- 規約・infrastructure-plan・openapi の記述が一貫し、新規参画メンバーが混乱しない。

### 悪い影響・制約

- 将来のグローバル展開時はタイムゾーン戦略を再設計する必要がある(その際は本 ADR を Superseded にして新 ADR を立てる)。
- GraalVM Native Image ビルド時に `-Duser.timezone=Asia/Tokyo` が `JAVA_TOOL_OPTIONS` 経由で有効か、Sprint 0/1 の Native build 検証で確認が必要(必要なら `org.graalvm.nativeimage.options` への移行を検討)。
- CloudWatch 等 UTC ベースのツールでのログ閲覧時は `+09:00` を意識する。

### 既存ドキュメント・規約への波及

- `docs/specs/設計規約.md` §2.4: API レスポンス TZ を UTC → JST に反転済み(PR #266)。
- `docs/specs/コーディング規約.md` §12.1: 同様に反転 + JVM TZ 二重強制の参照追記済み(PR #266)。
- `docs/architecture/infrastructure-plan.md` §3.4: JDBC URL 修正 + ECS env 追記済み(PR #266)。
- `api/openapi.yaml`: `newTenantsLast24h` の比較 TZ 記述を UTC → JST に修正済み(PR #266)。
- `docs/specs/基本設計書.md` §5.4: エラー応答例の `timestamp` を `+09:00` に修正済み(PR #266)。

## 6. 実装メモ

後続 PR での適用対象:

- `#142` `application.yml` に `spring.jackson.time-zone: Asia/Tokyo` / `connectionTimeZone=SERVER` を設定する。
- `#132` `@PrePersist/@PreUpdate` での `LocalDateTime.now()` を Clock DI(`ZoneId.of("Asia/Tokyo")`) に置き換える。
- `#144` 各 Entity の Auditing 設定で JST クロックを使用する。
- Native Image 対応(ADR-0008 参照): `-Duser.timezone=Asia/Tokyo` の GraalVM 互換性を Sprint 0/1 で検証。

## 7. 参考リンク

- Issue #265(本 ADR の決定を議論した Issue)
- PR #266(規約・infrastructure-plan の一括改訂)
- PR #135(設計規約 §2.4 / コーディング規約 §12.1 初期策定)
- ADR-0008(GraalVM Native Image 採用)
- MySQL Connector/J 8.0.23 リリースノート(`serverTimezone` deprecated)
- `docs/specs/設計規約.md` §2.4
- `docs/specs/コーディング規約.md` §12.1
- `docs/architecture/infrastructure-plan.md` §3.4
