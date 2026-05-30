# ADR-0011: エラー応答の戻り型 — 独自 `record ErrorResponse` を採用

- **Status**: Accepted
- **Date**: 2026-05-31
- **Deciders**: win2cot
- **Tags**: api, error-handling, web, shared

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: `ProblemDetail` を継承して `code` 等を拡張プロパティで乗せる](#選択肢-a-problemdetail-を継承して-code-等を拡張プロパティで乗せる)
  - [選択肢 B: 独自 `record ErrorResponse(...)` を作る](#選択肢-b-独自-record-errorresponse-を作る)
  - [選択肢 C: `ProblemDetail` を素のまま使い `properties` Map に `code` を載せる](#選択肢-c-problemdetail-を素のまま使い-properties-map-に-code-を載せる)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

- 基本設計書 v1.4.x §5.4 と設計規約 §2.4 で、全 API 共通のエラー応答スキーマを **6 フィールド**(`timestamp` / `status` / `error` / `code` / `message` / `path`)で固定済み。
- コーディング規約 §6.2 は `@RestControllerAdvice` で全例外を 6 フィールド スキーマへ変換するルールを定めているが、**具体的な戻り型は ADR で確定** とされ「想定 ADR-0005」のままになっていた(ADR-0001 §6 候補リスト)。実 ADR 番号は採番順により本書 ADR-0011 となる。
- Spring Boot 3+(本プロジェクトは Spring Boot 4.0.6 採用、開発計画書 v1.3)では `org.springframework.http.ProblemDetail`(RFC 7807)が標準で、`ResponseEntityExceptionHandler` の built-in 例外ハンドラも `ProblemDetail` を返す。
- 一方、規約上の 6 フィールド スキーマは `ProblemDetail` 既定フィールド(`type` / `title` / `status` / `detail` / `instance`)と**完全には一致しない**。`code` / `error` / `message` / `path` / `timestamp` のフラット 5 フィールドは `ProblemDetail` に直接の対応概念を持たない。
- クライアントは社内 JS フロントエンドが中心で、RFC 7807 仕様に依存した外部システム連携は当面想定しない(将来 `code` を OpenAPI / TypeScript 生成に厳密反映できる方が便益が大きい)。
- GraalVM Native Image(ADR-0008)前提のため、リフレクション境界が明確な型(record + enum)を選びたい。
- 参照: 設計規約 §2.4 / コーディング規約 §6.1〜§6.2 / 基本設計書 §5.4 / ADR-0001 §6 / ADR-0003(`shared` Open module)/ ADR-0008(GraalVM Native Image)/ ADR-0009(JST 全層統一)/ Issue #146 / 親トラッカ #121。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: `ProblemDetail` を継承して `code` 等を拡張プロパティで乗せる

- 概要: `ProblemDetail` を継承(または委譲)し、`code` / `error` / `timestamp` / `path` を追加プロパティとして付与。`ResponseEntityExceptionHandler` の built-in 動作に乗せる。
- 利点:
  - RFC 7807 準拠の世界観に乗れる(将来外部クライアントが期待しても適合)。
  - Spring 6+ の `ResponseEntityExceptionHandler` が標準で返す型と整合し、built-in 例外(`MethodArgumentNotValidException` 等)の取り扱いが素直。
- 欠点:
  - `ProblemDetail` 既定フィールド(`type` / `title` / `status` / `detail` / `instance`)と規約 6 フィールド(`timestamp` / `status` / `error` / `code` / `message` / `path`)が直接対応せず、`@JsonProperty` リネームか `MixIn` か独自シリアライザで矯正することになる。結局「Spring 標準を活かす」便益が消える。
  - `ProblemDetail` は mutable(record 化できない)。継承禁止ではないが、フィールド追加で immutable な値オブジェクトに保つにはコンポジション + カスタム シリアライザが現実解で、コードが増える。
- リスク・未知数: GraalVM Native Image 下で Jackson 反射ヒントを `ProblemDetail` + 拡張クラスの双方で持つ必要が出る。

### 選択肢 B: 独自 `record ErrorResponse(...)` を作る

- 概要: `shared` モジュール(ADR-0003)配下に `record ErrorResponse(OffsetDateTime timestamp, int status, String error, ErrorCode code, String message, String path)` を新設。`code` は `enum ErrorCode { E_VALIDATION, E_UNAUTHORIZED, E_FORBIDDEN, E_NOT_FOUND, E_CONFLICT, E_UNPROCESSABLE, E_INTERNAL }`(基本設計書 §5.4 SSoT および設計規約 §2.4 ミラーと完全一致 — 7 値)。
- 利点:
  - 規約 6 フィールドと 1:1 対応の immutable record。`code` が enum で型安全になり、OpenAPI / TypeScript 生成側でも厳密に出る。
  - テストが `assertThat(body.code()).isEqualTo(ErrorCode.E_NOT_FOUND)` のように書ける(コーディング規約 §9.4 のクロステナント漏洩テストで `code` 検証が一律に書ける)。
  - record + enum は GraalVM Native Image(ADR-0008)との相性が良い。リフレクション ヒントが明確に閉じる。
  - `shared` モジュール(ADR-0003 で Open module 化)配置で feature 横断の依存方向が綺麗(`task` / `usecase` などから `shared` への単方向参照)。
- 欠点:
  - `ResponseEntityExceptionHandler` の built-in ハンドラ群が `ProblemDetail` を返すため、`handleExceptionInternal(...)` を override して `ErrorResponse` に詰め替えるヘルパーが要る(変換ロジックは 1 メソッドに集約可能)。
  - RFC 7807 互換性は捨てる。将来 RFC 7807 へ寄せる必要が生じたら新規 ADR(supersedes 本 ADR)で再決定する。
- リスク・未知数: なし(クライアントが内部限定の前提が崩れる事態は ADR で再決定の対象)。

### 選択肢 C: `ProblemDetail` を素のまま使い `properties` Map に `code` を載せる

- 概要: `ProblemDetail.forStatusAndDetail(...)` を使い、`setProperty("code", "E_NOT_FOUND")` 等で拡張。
- 利点: 書き換えが最も少なく、Spring の素の挙動を維持。
- 欠点:
  - `code` が `Object` 型として `properties` Map に入るため、OpenAPI に厳密な `code` enum を書きにくく、フロント側の TypeScript 生成も `any` 寄りに濁る。
  - 規約 §2.4 が定める「6 フィールド フラット」と、`ProblemDetail` 既定 5 フィールド + `properties.code` の構造が**ずれたまま固着**しやすい(`properties` をフラット化する Jackson 設定を入れても、`code` 以外の規約フィールド差分は残る)。
- リスク・未知数: `code` 文字列のタイプミスを compile time に検出できず、テスト依存になる。

## 3. 決定(Decision)

**採用**: 選択肢 B(独自 `record ErrorResponse(...)`)

`@RestControllerAdvice` 実装は **`ResponseEntityExceptionHandler` を継承**する方針を併せて採用する。Spring built-in 例外(Bean Validation 失敗 / JSON parse 失敗 / 405 / 415 等)の取りこぼし防止のため、Spring が返す `ProblemDetail` を `ErrorResponse` に詰め替える hook(本プロジェクトが採用する Spring Boot 4.0.x / Spring Framework 7.x の最新シグネチャに合わせて `handleExceptionInternal(...)` または `createResponseEntity(...)` のいずれか深い側)を override する。具体的な hook 名・シグネチャは実装着手時点の Spring 版本に従う(Spring Framework のマイナー更新で hook 名が変わる前例があるため、本 ADR では特定メソッドにピン留めしない)。

## 4. 理由(Rationale)

- 規約上の 6 フィールド スキーマと `ProblemDetail` 既定フィールドが**そもそも対応していない**ため、A / C を採っても結局 Jackson 層で `ProblemDetail` を矯正する作業が発生する。であれば最初から 6 フィールド record を作り `code` を enum 化して型安全性を得る方が、コード量・読みやすさ・テスト容易性のいずれでも優位。
- クライアントは内部 JS フロントエンド中心で RFC 7807 準拠の便益が小さい。逆に `code` の enum 厳密化が OpenAPI / TypeScript 生成側で効く(`code: E_NOT_FOUND | E_FORBIDDEN | ...` のユニオン型が生成できる)。
- record + enum は ADR-0008(GraalVM Native Image)と相性が良く、リフレクション境界がはっきりする。
- `shared` モジュール(ADR-0003)配置で feature 横断の依存方向が単方向で綺麗に保てる。
- 捨てる利点(RFC 7807 互換性、Spring 標準の戻り型維持)は内部 API としては影響が小さく、必要になった時点で本 ADR を superseded する新規 ADR で再決定可能。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 規約 §2.4 の 6 フィールド スキーマがコード上の record 1 つに 1:1 で対応し、ドキュメントとコードの乖離が起きにくい。
- `code` が `ErrorCode` enum になり、コーディング規約 §9.4 のクロステナント漏洩テスト(`code` が `E_NOT_FOUND` / `E_FORBIDDEN` であること)が型安全に書ける。
- OpenAPI generator / TypeScript 生成側で `code` が enum 型として出るため、フロント側でも switch 網羅性チェックが効く。
- `shared` モジュール配置でテスト時に依存差し替えが少なく、`@WebMvcTest` での advice 適用が単純化する。
- GraalVM Native Image のリフレクション設定が record + enum 構成で閉じる(ADR-0008 整合)。

### 悪い影響・制約(Negative)

- `ResponseEntityExceptionHandler` の built-in 動作を override する分、Spring upgrade 時の API 変更追随が小さく増える(`handleExceptionInternal` シグネチャ変更に追随が必要)。
- RFC 7807 互換クライアントを将来追加する場合、本 ADR を superseded する新規 ADR で再決定が必要。
- 例外クラスと `ErrorCode` の 1:1 マッピングを例外側コンストラクタで強制する設計のため、新規例外クラスを追加する都度マッピングを明示する開発規律が要る(コーディング規約 §6.1 の表で一元管理する)。

### 既存ドキュメント・規約への波及

- `docs/adr/0001-record-architecture-decisions.md` §6 の「ProblemDetail vs 独自 ErrorResponse — 想定 ADR-0005」を「ADR-0011 で独自 record 採用と確定」に書き換える(本 ADR と同 PR で更新)。
- `docs/specs/設計規約.md` §2.4 末尾の「いずれが妥当かは ADR で決定」を「ADR-0011 で独自 `ErrorResponse` record 採用と確定」へ置換する(本 ADR と同 PR で更新)。
- `docs/specs/コーディング規約.md` §6.2 の「(想定 ADR-0005)」表記を「(ADR-0011)」に置換し、実 class 名 `xyz.dgz48.tasks.webapi.shared.web.ErrorResponse` / `xyz.dgz48.tasks.webapi.shared.web.ErrorCode` / `xyz.dgz48.tasks.webapi.shared.web.GlobalExceptionHandler` を明記する(本 ADR と同 PR で更新)。
- 既存の `xyz.dgz48.tasks.webapi.shared.exception` 配下のドメイン例外クラスは、`ErrorCode` を引数に取るコンストラクタを追加する形で **Sprint 0 の N4 系 PR** で対応する(本 ADR では実装コードは入れない、規約整備のみ)。

## 6. 実装メモ(Implementation Notes)

実装は Sprint 0(2026-06-14 開始)の N4 系 Issue で行い、本 ADR では規約整備のみ。実装着手時の指針:

- **配置**(`shared` モジュール、ADR-0003 整合):
  - `xyz.dgz48.tasks.webapi.shared.web.ErrorResponse`(record、6 フィールド)。
  - `xyz.dgz48.tasks.webapi.shared.web.ErrorCode`(enum、`E_VALIDATION` / `E_UNAUTHORIZED` / `E_FORBIDDEN` / `E_NOT_FOUND` / `E_CONFLICT` / `E_UNPROCESSABLE` / `E_INTERNAL`)。
  - `xyz.dgz48.tasks.webapi.shared.web.GlobalExceptionHandler`(`@RestControllerAdvice` + `extends ResponseEntityExceptionHandler`)。
- **`timestamp`**: `OffsetDateTime.now(ZoneId.of("Asia/Tokyo"))` で生成(ADR-0009 / 設計規約 §2.4 整合、UTC `Z` 出力禁止)。Jackson は `spring.jackson.time-zone: Asia/Tokyo` + `WRITE_DATES_AS_TIMESTAMPS=false` 前提(コーディング規約 §12.1 整合)。
- **`error`**: HTTP ステータスの reason phrase(例: `"Bad Request"` / `"Not Found"`)を入れる。値は `org.springframework.http.HttpStatus#getReasonPhrase()` 由来とし、独自文字列を入れない(クライアントは `code` の方で意味分岐する想定で、`error` は人間可読のヒント)。
- **`path`**: `HttpServletRequest#getRequestURI()`(または `WebRequest` から相当する URI)を入れる(`/api/tasks/123` のような形)。query string は含めない。
- **例外クラスと `ErrorCode` の 1:1 マッピング**: 例外側コンストラクタで `ErrorCode` を保持し、Handler 側のロジックを薄く保つ。例:
  - `TaskNotViewableException` ↔ `ErrorCode.E_NOT_FOUND`(HTTP 404)
  - `TaskOwnershipException` ↔ `ErrorCode.E_FORBIDDEN`(HTTP 403)
  - `TenantBoundaryViolationException` ↔ `ErrorCode.E_FORBIDDEN`(HTTP 403)
  - `ValidationFailedException` ↔ `ErrorCode.E_VALIDATION`(HTTP 400)
  - `ConflictException` ↔ `ErrorCode.E_CONFLICT`(HTTP 409)
  - 表の正本はコーディング規約 §6.1 とする(本 ADR は重複コピーを避ける)。
- **`ResponseEntityExceptionHandler` 拡張**: 本プロジェクトの Spring Boot 4.0.x(Spring Framework 7.x)で `ResponseEntityExceptionHandler` が公開する詰め替え hook(現行系列では `handleExceptionInternal(...)` または `createResponseEntity(...)`)を override し、Spring が返す `ProblemDetail` から `status` を取り出して `ErrorResponse` に詰め替えるヘルパー(`toErrorResponse(...)`)を 1 本用意する。実装着手時点で当該 Spring 版本の API を確認の上で hook を選ぶ(Spring Framework 6.1 で `createResponseEntity` が追加される等、過去にも hook 構造の変化があるため特定メソッド名にピン留めしない)。`MethodArgumentNotValidException` 系は `code = E_VALIDATION`、それ以外の built-in は `status` 由来で `ErrorCode` を決定する。
- **アプリ固有例外**: 同 advice クラス内に `@ExceptionHandler(TaskNotViewableException.class)` 等の個別ハンドラを直接生やし、例外が保持する `ErrorCode` を `ErrorResponse` に転写する。
- **テスト**: `@WebMvcTest` で `GlobalExceptionHandler` を含めたスライス テストを書き、7 つの `ErrorCode` すべてが返る経路を網羅。クロステナント漏洩テスト(コーディング規約 §9.4)で `code` 値の検証が型安全に書けることを担保。
- **OpenAPI**: `ErrorResponse` schema を共通定義として `api/openapi.yaml` に追加し、`code` は enum で記述。各エラーレスポンスから `$ref` で参照する(設計規約 §2.6 のレスポンス列挙順序ルールと整合)。本 ADR では OpenAPI 反映の具体は Sprint 0 N4 系で行う前提とし、ADR 採用時点では実装コードは入れない。

## 7. 参考リンク(References)

- ADR-0001 §6 候補リスト(想定 ADR-0005 として記載)
- ADR-0003: `shared` パッケージを Open module 化
- ADR-0008: GraalVM Native Image
- ADR-0009: JST 全層統一ポリシー
- 設計規約 §2.4 エラー応答スキーマ
- コーディング規約 §6.1〜§6.2 例外クラスとマッピング
- 基本設計書 §5.4 エラー応答仕様
- Issue #146(本 ADR 起点)
- 親トラッカ #121(Sprint 0 scaffold ↔ 設計書整合実装)
- Spring Framework: `org.springframework.http.ProblemDetail`(RFC 7807)
- RFC 7807: Problem Details for HTTP APIs
