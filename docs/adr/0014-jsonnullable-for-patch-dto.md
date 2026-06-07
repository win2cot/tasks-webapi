# ADR-0014: PATCH 部分更新 DTO に `JsonNullable<T>` を採用する

- **Status**: Accepted
- **Date**: 2026-06-01
- **Deciders**: win2cot
- **Tags**: api, web, dto, serialization, native-image

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: `org.openapitools:jackson-databind-nullable` の `JsonNullable<T>` を採用](#選択肢-a-orgopenapitoolsjackson-databind-nullable-の-jsonnullablet-を採用)
  - [選択肢 B: 独自 `Patch<T>` wrapper を `shared` に定義](#選択肢-b-独自-patcht-wrapper-を-shared-に定義)
  - [選択肢 C: sentinel 値で表現(`__UNSET__` 等)](#選択肢-c-sentinel-値で表現__unset__-等)
  - [選択肢 D: PATCH を使わず PUT に戻す(`#329` で却下済)](#選択肢-d-patch-を使わず-put-に戻す329-で却下済)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Issue #329 で `PATCH /api/tasks/{id}` 新設 + A-14 `PUT /api/tasks/{id}` 廃止が確定し、ダッシュボードの行内編集 6 項目のうち期限 / 担当者 / 優先度 / タイトル / 説明の 5 項目を任意の組み合わせで部分更新できるようにする。残り 2 項目(ステータス / 公開範囲)は遷移ルール・副作用が個別の特殊系で、既存 A-16 / A-17 専用 PATCH を維持する。

部分更新 DTO は「undefined(フィールド省略 = 変更なし)」「null(明示的に null を設定 = クリア)」「値(更新)」の 3 状態を表現する必要がある。Java `record` + Jackson 標準では、フィールド省略時もコンストラクタが呼ばれてフィールドが `null` で埋まるため、**「未指定」と「明示 null」が区別できない**。本 ADR は Sprint 0 / Phase 1 Sprint 1 で新設する全部分更新 DTO(`TaskPatchRequest` 等)に対する表現方式を確定し、OpenAPI v1.5.x 反映(#334)と build 依存追加の前提資料とする。

関連:

- Issue #331(本 ADR 起票元)/ Issue #329(PATCH 新設 + PUT 廃止 確定)
- Issue #334(OpenAPI v1.5.0 反映、本 ADR の Accepted を前提に blocks chain)
- ADR-0008(GraalVM Native Image、リフレクション登録要件)
- ADR-0011(独自 `record ErrorResponse`、record + Jackson 親和の先行事例)
- ADR-0012(ETag / If-Match、`tasks.version` カラム新設・同 migration window)
- コーディング規約 §5(型選択 record / class)/ §5.3(REST 入出力 DTO)
- 設計規約 §2.1(OpenAPI ファースト)/ §2.2(部分更新は PATCH)
- 派生 Issue #287(record vs class 層別使い分け、Option α 採用済)

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: `org.openapitools:jackson-databind-nullable` の `JsonNullable<T>` を採用

- 概要: `org.openapitools:jackson-databind-nullable:0.2.10` を `webapi/build.gradle` に追加し、`JsonNullableJackson3Module` を Jackson 3 に登録。部分更新 DTO のフィールド型を `JsonNullable<String>` / `JsonNullable<LocalDate>` 等の wrapper にして、`isPresent()` で undefined vs 値 / null を識別する。
- 利点:
  - **業界標準型**: OpenAPI Generator が `nullable: true` フィールドに対して emit するデフォルト型なので、将来 openapi-generator 導入時の摩擦が最小。
  - **record と共存**: `JsonNullable<T>` は不変 wrapper のため `public record TaskPatchRequest(JsonNullable<String> title, ...) {}` で素直に書ける。
  - **JSON 仕様に忠実**: `null` はそのまま null(`isPresent() == true` かつ `get() == null`)、フィールド省略は `JsonNullable.undefined()`(`isPresent() == false`)で表現され、JSON 言語仕様と一致する。
  - **Bean Validation 互換**: Hibernate Validator の cascade(`@Valid`)と組み合わせて `JsonNullable<@Size(max=255) @NotBlank String> title` のように内包値へバリデーション伝播可能。
  - **Jackson 3 ネイティブ対応(0.2.10〜)**: v0.2.10 で `JsonNullableJackson3Module`(`tools.jackson.databind.JacksonModule` 拡張)が追加され、Spring Boot 4 の Jackson 3 ObjectMapper に直接登録可能。Jackson 2 系と Jackson 3 系の依存混在が解消される。
- 欠点:
  - **依存追加 1 件**: `jackson-databind-nullable` は OpenAPI Tools が保守する小規模ライブラリ。リリース頻度は年 1〜2 回程度で、外部依存リスクは存在(ただし MIT、ソース小、フォーク容易)。
  - **boilerplate**: usecase 層へ渡すマッパーで `if (req.title().isPresent()) cmd.title(req.title().get())` 形式の分岐が項目数だけ並ぶ。
  - **GraalVM Native Image**: `JsonNullable` / `JsonNullableJackson3Module` / 各 DTO の `JsonNullable<T>` 具体化型をリフレクション登録する必要(ADR-0008 §20.1)。
- リスク・未知数:
  - Spring Boot 4 の `JacksonAutoConfiguration` は `tools.jackson.databind.JacksonModule` 型の `@Bean` を自動検出して ObjectMapper へ登録するため、`@Bean JsonNullableJackson3Module` の明示登録で確実に配線される。`spring.jackson.serialization-inclusion=non_null` と併用しないと undefined フィールドが `{"title":null}` でなく `{"title":{"present":false}}` 形式でレスポンスへ漏れる既知ハマり所がある。

### 選択肢 B: 独自 `Patch<T>` wrapper を `shared` に定義

- 概要: `shared` パッケージ(ADR-0003 Open module)に `public sealed interface Patch<T> permits Patch.Set, Patch.Clear, Patch.Unchanged {}` 相当を起こし、Jackson の `JsonDeserializer<Patch<T>>` を自作。DTO 側は `Patch<String> title` で記述する。
- 利点:
  - **依存ゼロ**: 外部ライブラリのリリースサイクル / 廃止リスクから独立。
  - **命名を自社規約に合わせられる**: `Patch.set(v)` / `Patch.clear()` / `Patch.unchanged()` 等、ドメイン語彙で書ける。
  - **GraalVM**: 自社管理下の型のみリフレクション登録すれば済む。
- 欠点:
  - **業界 OSS と概念重複**: 同じ問題を解く既存 OSS があるのに独自実装すると、新規入社者の学習コスト + 保守コスト + テストコストが恒久的に発生。
  - **将来 openapi-generator 導入時の二重型問題**: 生成コードは `JsonNullable<T>` を emit するため、手書き DTO と生成 DTO で wrapper 型が並走しマッパー層が複雑化。
  - **Jackson serializer/deserializer 自作**: 3 状態の serialization / Bean Validation cascade / null 安全(`@NullMarked`/NullAway)の動作確認を自前で網羅する手間。
- リスク・未知数:
  - sealed interface + record 実装で書く場合、Jackson の polymorphic deserialization 設定が必要(`@JsonTypeInfo` 等)で、JSON 表現がフラットにならない懸念。フラット表現にするには custom deserializer 必須。

### 選択肢 C: sentinel 値で表現(`__UNSET__` 等)

- 概要: `String __UNSET__ = "__UNSET__"` のような sentinel を導入し、部分更新 DTO は `String title` 直書きで「`null` = クリア / sentinel = 未指定 / それ以外 = 更新」を運ぶ。
- 利点:
  - **型ゼロ追加**: 既存 String / Long / enum をそのまま使える。
- 欠点:
  - **型安全でない**: sentinel が業務ドメイン値と衝突するリスク(`title == "__UNSET__"` を意図的に送るケース)。
  - **JSON 仕様に反する hack**: 「`"__UNSET__"` という文字列を null 区別に使う」のは JSON 言語仕様外の規約で、OpenAPI スキーマでも表現困難。
  - **Bean Validation 衝突**: `@NotBlank String title` に sentinel を許容しようとすると `@Size` / `@Pattern` を別途整備する必要があり、規約が破綻する。
  - **数値型・enum で適用不能**: `LocalDate` / `Priority` 等の sentinel を別途設計するのは非現実的。
- リスク・未知数: 採用すべきでない方向だが「何もしない」相当ケースとして記録する。

### 選択肢 D: PATCH を使わず PUT に戻す(`#329` で却下済)

- Issue #329 結論で「行内編集 1 項目で全項目送信」「後勝ち」「コンフリクト window 拡大」の課題が未解決として却下済。本 ADR では選択肢の対称性のため言及するのみで再評価しない。

## 3. 決定(Decision)

**採用**: 選択肢 A(`org.openapitools:jackson-databind-nullable` の `JsonNullable<T>`)

新設する部分更新 DTO(初出は `TaskPatchRequest` for `PATCH /api/tasks/{id}`)のフィールド型を `JsonNullable<T>` とし、`webapi/build.gradle` に `implementation 'org.openapitools:jackson-databind-nullable:0.2.10'` を追加、`infra` 層に `@Configuration` クラスを 1 つ起こして `@Bean JsonNullableJackson3Module` を登録する。既存の単項目 PATCH(`A-16 PATCH /tasks/{id}/status` / `A-17 PATCH /tasks/{id}/visibility`)は当該 1 フィールドが `required` で undefined を許さないため、本 ADR の対象外として現状維持する(遡及適用しない)。

## 4. 理由(Rationale)

- **業界標準型は学習資産を最大化する**: 新規入社者・将来の openapi-generator 採用・社外公開ドキュメントすべてで `JsonNullable<T>` は通用する語彙。独自型(選択肢 B)は同等機能で恒久保守コストを抱え込む。
- **record + Jackson の既存規約と矛盾しない**: コーディング規約 §5.3 が REST DTO = record を確定済(Issue #287 Option α)。`JsonNullable<T>` は record の不変性を壊さない wrapper であり、規約の例外を作らずに済む。
- **JSON 仕様への忠実性**: `undefined`(キー不在)/ `null`(明示 null)/ 値 の 3 状態は JSON 自身が持つ意味区別なので、wrapper 型でこれを直接写像する設計は API 仕様としてシンプル。sentinel(選択肢 C)は型安全と JSON 仕様の両方を犠牲にする。
- **OpenAPI ファーストとの整合**: 設計規約 §2.1 で OpenAPI を SSOT とする運用が確定。`JsonNullable<T>` は OpenAPI の `nullable: true` フィールドと自然に対応するため、yaml ↔ Java の同期コストが最小。
- **GraalVM Native Image 制約と両立**: ADR-0008 が要求するリフレクション明示登録は、`JsonNullable` / `JsonNullableModule` を 1 回登録するだけで済み、独自 wrapper を全 DTO 分自前で reflection-config に並べる手間より低コスト。
- **依存リスクは限定的**: `jackson-databind-nullable` は OpenAPI Tools 公式の単機能ライブラリで、ソース規模が小さくフォーク・置換が現実的。Spring Boot 4 の Jackson 3 環境では v0.2.10 以降の `JsonNullableJackson3Module` を `@Bean` 登録することで吸収可能。v0.2.6 以前は Jackson 2 モジュールのみ提供であり、Spring Boot 4 の Jackson 3 ObjectMapper に登録されず Jackson 2/3 の依存混在が生じるため、**v0.2.10 以降を使用すること**。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 部分更新 DTO の表現が JSON 言語仕様と 1:1 対応し、レビュアー・OpenAPI 読み手の認知負荷が下がる。
- 将来 openapi-generator(client SDK 生成 / Server stub)を導入する場合、生成コードと手書きコードで wrapper 型が一致するので統合が滑らか。
- record + Bean Validation の規約(コーディング規約 §5.3)を例外なく適用できる。
- A-16 / A-17 既存 PATCH には触らないため、既存テスト・実装の改修コストがゼロ。

### 悪い影響・制約(Negative)

- `webapi/build.gradle` の implementation 依存が 1 件増える(年 1〜2 回のリリースを watch する保守コスト)。
- `infra` 層に `JsonNullableJacksonConfiguration`(仮称)を 1 ファイル新設する必要がある。
- usecase 層への mapper コードが `JsonNullable.isPresent()` 分岐で項目数 × 行数増える。共通ユーティリティ(例: `JsonNullables.applyIfPresent(req.title(), cmd::title)`)を `shared` に置くかは実装時に判断する。
- GraalVM Native Image build で `JsonNullable` 系のリフレクション登録漏れは Native build 失敗で顕在化。CI(コーディング規約 §20.5)で検出可能なので致命的でない。
- Jackson の `@JsonInclude(JsonInclude.Include.NON_NULL)` 単独では `JsonNullable.undefined()` を完全に除外しきれないケースがあるため、レスポンス側で `JsonNullable<T>` を使う場合は `JsonInclude.Include.CUSTOM` + フィルタ実装か `JsonNullableModule` の Setting を明示する(実装メモ参照)。

### 既存ドキュメント・規約への波及

- **コーディング規約 §5.3** に「PATCH 部分更新 DTO は ADR-0014 を参照し `JsonNullable<T>` を使用する」旨のポインタ節(§5.4 新設、または §5.3 末尾 1 段落追記)を加える。本 ADR を SSOT とし、規約側は ADR を指す pointer に留める(マトリクスの一部に編入するため §5.1 の `adapter.web` Request DTO 行に「PATCH 部分更新は `JsonNullable<T>` 内包」の脚注を追加してもよい)。
- **設計規約 §2.2**(PATCH メソッド規約)に「PATCH 部分更新 DTO の表現は ADR-0014」を 1 行参照追記してもよい(任意、本 PR では実施しない)。
- **基本設計書 §5.1**(API 一覧)は #334 OpenAPI v1.5.x 反映 Issue で同時改訂されるため、本 ADR では触らない。
- **`webapi/build.gradle`** に `implementation 'org.openapitools:jackson-databind-nullable:0.2.10'` を追加(#334 または C-1〜C-3 系の Sprint 1 App PR で同時実施)。
- **`infra` 層**に `JsonNullableJacksonConfiguration`(仮称、`@Configuration` クラス)を新設し `@Bean JsonNullableJackson3Module` を登録(同上 PR)。
- **OpenAPI yaml**(`api/openapi.yaml`)で部分更新 DTO のフィールドは `nullable: true` を付け、`required:` 配列から除外することで undefined を許す(#334 で実施)。
- **GraalVM Native Image** リフレクション設定(コーディング規約 §20.1)に `JsonNullable` / `JsonNullableModule` を追記(Sprint 0 後の Native build 検証時に同期)。

## 6. 実装メモ(Implementation Notes)

- **ビルド依存と Bean 登録**:

  ```gradle
  // webapi/build.gradle
  // v0.2.10 以降: Jackson 3 (tools.jackson) ネイティブ対応済み。v0.2.6 以前は Jackson 2 モジュールのみで Spring Boot 4 と非互換。
  implementation 'org.openapitools:jackson-databind-nullable:0.2.10'
  ```

  ```java
  // shared/infra/JsonNullableJacksonConfiguration.java
  @Configuration
  public class JsonNullableJacksonConfiguration {
    @Bean
    public JsonNullableJackson3Module jsonNullableModule() {
      return new JsonNullableJackson3Module();
    }
  }
  ```

  > **Note**: `JsonNullableJackson3Module` は `tools.jackson.databind.JacksonModule` を実装しており、Spring Boot 4 の `JacksonAutoConfiguration` が `@Bean` として自動検出し ObjectMapper へ登録する。SPI(`META-INF/services/tools.jackson.databind.JacksonModule`)でも自動登録されるが、`@Bean` による明示登録を採用してコンテキスト管理下に置く。

- **DTO 記法**:

  ```java
  // adapter.web.dto.TaskPatchRequest
  public record TaskPatchRequest(
      JsonNullable<@Size(max = 255) @NotBlank String> title,
      JsonNullable<@Size(max = 65_535) String> body,
      JsonNullable<LocalDate> dueDate,
      JsonNullable<Long> assigneeId,
      JsonNullable<Priority> priority) {}
  ```

- **Mapper 補助ユーティリティ**(`shared` に置く案):

  ```java
  // shared/util/JsonNullables.java
  public final class JsonNullables {
    public static <T> void applyIfPresent(JsonNullable<T> v, Consumer<T> sink) {
      if (v != null && v.isPresent()) sink.accept(v.get());
    }
    private JsonNullables() {}
  }
  ```

- **Jackson 設定の Configuration 注意点**: `@JsonInclude(JsonInclude.Include.NON_NULL)` 単独では `JsonNullable.undefined()` がレスポンスに `{"field":{"present":false}}` で漏れることがあるため、レスポンス側で `JsonNullable<T>` を使う場合は専用設定が必要。本 ADR では **PATCH リクエスト側でのみ** `JsonNullable<T>` を使用し、レスポンス側は通常の record + nullable 直書きとして問題回避する(レスポンスはサーバが現値を返すので「未指定」状態を出力する必要がない)。
- **PR 分割方針**: 本 ADR Accepted 化は本 PR(`feature/issue-331-jsonnullable-adr`)単独。build.gradle 依存追加 / Jackson Configuration 新設 / `TaskPatchRequest` 実装は #334 OpenAPI v1.5.0 反映 PR、もしくは Sprint 1 App C 系 PR(#311〜#313)で同時実施する。
- **GraalVM 検証タイミング**: Sprint 0 Native build 検証(コーディング規約 §20.5)前に reflection-config 追記が必要。Native build CI が落ちる形で気づける想定なので、追加チェック工程は不要。

## 7. 参考リンク(References)

- Issue #331(本 ADR 起票元、結論 SSOT)
- Issue #329(`PATCH /api/tasks/{id}` 新設 + A-14 PUT 廃止 確定)
- Issue #287(record vs class 層別使い分け、Option α 採用 / コーディング規約 §5 マージ済)
- Issue #334(OpenAPI v1.5.0 反映、本 ADR Accepted を blocks chain の前提)
- ADR-0008 GraalVM Native Image / ADR-0011 独自 record ErrorResponse / ADR-0012 楽観ロック ETag/If-Match
- コーディング規約 §5 / §5.3 / §20(Native Image 実装制約)
- 設計規約 §2.1 / §2.2 / §2.4 / §2.5
- 基本設計書 v1.4.x §5.1(API 一覧、#334 で改訂)
- [OpenAPITools/jackson-databind-nullable](https://github.com/OpenAPITools/jackson-databind-nullable)(MIT、`JsonNullable<T>` / `JsonNullableModule` 提供)
