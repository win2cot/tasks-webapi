# ADR-0003: `shared` パッケージを Spring Modulith の Open module として宣言する

- **Status**: Accepted
- **Date**: 2026-05-14
- **Deciders**: 開発チーム
- **Tags**: architecture, modularity

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: `shared` を **Open module** として宣言する](#選択肢-a-shared-を-open-module-として宣言する)
  - [選択肢 B: `shared` を通常モジュールとし、`@NamedInterface` で個別公開する](#選択肢-b-shared-を通常モジュールとし-namedinterface-で個別公開する)
  - [選択肢 C: `shared` を作らず、共通型を各 feature にコピー / 親パッケージに置く](#選択肢-c-shared-を作らず-共通型を各-feature-にコピー--親パッケージに置く)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響](#良い影響)
  - [悪い影響・制約](#悪い影響制約)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

設計規約 §1.2.2 は feature 単位パッケージとして `task` / `user` / `tenant` / `security` / `notification` / `audit` / `dashboard` / `shared` を定めている。このうち `shared` は「どの feature にも属さない共通型」(例外基底クラス、共通定数、汎用 Value Object 等)を置く特別な feature である。

Spring Modulith は既定では `xyz.dgz48.tasks.webapi.<feature>` 配下を**通常モジュールとして扱い**、他モジュールから依存する場合は **`<feature>.internal` 配下を直接参照不可**・`@NamedInterface` で公開した型のみ許可、というルールで `ApplicationModules.verify()` が検証する。

しかし `shared` は性質上、**全 feature から自由に参照されることが前提**である。これを既定モジュールのままで運用すると、`shared` 配下のクラスを使うたびに `@NamedInterface` で公開するか、各 feature の `package-info.java` に `@ApplicationModule(allowedDependencies = "shared")` を明示する必要が生じ、規約として煩雑になる。

PR #135 のレビュー指摘 #5(2026-05-14)で「`shared` パッケージの Open module 採用は設計規約本文に埋もれた重要なアーキテクチャ判断であり、ADR として独立記録すべき」と提案された。本 ADR はこの提案に従い、`shared` の扱いを ADR として確定する。

## 2. 検討した選択肢

### 選択肢 A: `shared` を **Open module** として宣言する

- 概要: `shared/package-info.java` に `@ApplicationModule(type = Type.OPEN)` を付与する。これにより `ApplicationModules.verify()` は他モジュールから `shared` の任意のクラスへの依存を許可する。
- 利点:
  - 各 feature 側で `allowedDependencies = "shared"` を書く必要がない(規約が単純)。
  - `shared` 配下のクラスを使う際に `@NamedInterface` で公開する手間が不要。
  - 「shared は皆が使える共通基盤」という直感的な認識と一致。
- 欠点:
  - `shared` に何でも詰め込みやすくなる(誘惑の温床)。ガードレールとして「Bean / Entity を置かない」「業務ロジックを逃がさない」を規約で明示する必要がある。
- リスク・未知数:
  - 将来 `shared` が肥大化した場合、内部の責務分割が遅れる可能性。

### 選択肢 B: `shared` を通常モジュールとし、`@NamedInterface` で個別公開する

- 概要: 各クラスを `@NamedInterface` で公開し、他 feature はそれ経由で参照する。
- 利点: 公開境界が明示的で、誤って `shared.internal` を参照される事故を防げる。
- 欠点:
  - 例外基底クラスや汎用定数を使うたびにアノテーション付与が必要で煩雑。
  - そもそも `shared` には `internal` を作る積極理由が乏しい(本質的に全公開のもの)。

### 選択肢 C: `shared` を作らず、共通型を各 feature にコピー / 親パッケージに置く

- 概要: `shared` という分類自体を廃し、`xyz.dgz48.tasks.webapi` 直下に共通クラスを置く。
- 利点: モジュール分割の議論が不要。
- 欠点:
  - `xyz.dgz48.tasks.webapi` 直下は `TasksWebapiApplication.java` の置き場所であり、共通クラスを混ぜると `ApplicationModules.of(...)` の扱いが曖昧になる。
  - 「どこに置くか」の判断が個別 PR で揺れる。

## 3. 決定

**採用**: 選択肢 A(`shared` を Open module として宣言する)

具体的には:

- `xyz/dgz48/tasks/webapi/shared/package-info.java` に以下を付与する。

```java
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
@org.jspecify.annotations.NullMarked
package xyz.dgz48.tasks.webapi.shared;
```

- `shared` に置いてよいものを以下に限定する:
  - 例外基底クラス(`DomainException` 等)
  - 共通定数 / Enum(全 feature で使う識別子等)
  - 汎用 Value Object(`Money`, `EmailAddress` 等)
- `shared` に置いてはいけないものを以下に明記する:
  - Spring `@Component` / `@Service` / `@Repository`
  - JPA Entity
  - 業務ロジック(「どの feature にも属さないが業務的意味を持つ概念」が現れた場合は新規 feature の起票を検討する)

## 4. 理由

- レビュアー指摘 #5(2026-05-14)に従い、設計規約本文の §1.3.1 に埋め込んでいた判断を ADR として可視化する。
- Open module 化は **規約として最もシンプル** で、`shared` が果たすべき役割(全 feature の共通基盤)と整合する。
- ガードレール(Bean を置かない / 業務ロジックを逃がさない)を規約で明示することで、Open 化の弊害(肥大化)を抑制できる。
- 通常モジュール + `@NamedInterface` 方式は記述コストが高く、`shared` の性質と合わない。

## 5. 影響

### 良い影響

- 共通基盤の利用ルールが明確になり、Sprint 0 以降の実装で「`shared` を使ってよいか」の判断が瞬時にできる。
- `ApplicationModules.verify()` が `shared` 関連で誤検知することを防げる。

### 悪い影響・制約

- `shared` に何でも詰め込まれるリスク。ガードレールを Code Review で守る必要がある。
- 将来 `shared` を分割する判断が必要になった場合、依存箇所が広範囲で影響範囲が大きい。

### 既存ドキュメント・規約への波及

- 設計規約 §1.3.1(`shared` パッケージの扱い)は本 ADR の結論に合致しており、本文の `package-info.java` サンプルが ADR の §3 と整合する。
- 設計規約 §1.3.1 末尾に **「決定根拠は ADR-0003 を参照」** のリンクを追加する(本 ADR と同 PR で対応)。
- ADR-0001 §6 の「次に起票が想定される候補」リストでも、本 ADR を ADR-0003 として記録し、テナント分離は想定 ADR-0004 に繰り下げる(本 ADR と同 PR で対応)。
- 新 feature 追加時に `shared` を参照するための具体的な手順は **[docs/dev/feature-template.md §5](../dev/feature-template.md#5-shared-参照ルール)** に記載した(Issue #274)。

## 6. 実装メモ

- `shared/package-info.java` の `@ApplicationModule(type = Type.OPEN)` 付与は Sprint 0 整合タスク(Issue #121 配下)で実施する。現行 scaffold には `shared` パッケージがまだ存在しないため、`shared` 新設と Open module 宣言は同一 PR で行う。
- 既存パッケージのうち `shared` に移動すべき候補(例外基底クラス等)は Sprint 0 で識別する。

## 7. 参考リンク

- Spring Modulith リファレンス: `ApplicationModule#Type.OPEN`
- 設計規約 §1.3.1
- ADR-0001(本 ADR は ADR-0001 で導入された ADR 制度の運用例)
- PR #135 レビュー指摘 #5(2026-05-14)
