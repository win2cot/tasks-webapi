# ADR-0019: 構造化ログ出力は Logback + Spring Boot 標準構造化ログを採用する

- **Status**: Accepted
- **Date**: 2026-06-08
- **Deciders**: win2cot
- **Tags**: logging, observability, dependencies

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

infra ADR-0005 でログ基盤を CloudWatch Logs(構造化 JSON + Logs Insights)に確定し、JSON 出力スキーマと出力実装の選定を Issue #451 に切り出した。現行実装は Logback + `net.logstash.logback:logstash-logback-encoder:8.1`(`LogstashEncoder` 素の設定のみ、デコレータ / マスカ未使用)。

制約・周辺事情:

- encoder 8.1 は Jackson 2 を transitive に持ち込み、Jackson 2 完全除去(#496)の残存ルートの 1 つ
- Spring Boot 4.0.6 は標準構造化ログ(`logging.structured.format`: ecs / gelf / logstash)を持ち、Logback / Log4j2 の両 backend で利用可能(自前 JsonWriter、Jackson 非依存)
- Native Image は判断材料にならない(Log4j 2.25.0 で GraalVM 公式対応済、Logback も Boot サポート済で両者同等)
- 議論の過程で PII 方針を「マスクして出す」から「**そもそも出さない(参照は内部 ID のみ)**」に格上げした(コーディング規約 §7 改訂)。これにより encoder 層の正規表現マスク(safety-net)は要件から外れた

調査日付: 2026-06-08。一次ソースは §7 参照。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: Logback + logstash-logback-encoder 9.0(現行延長)

- 概要: encoder を 8.1 → 9.0(Jackson 3 ネイティブ)にアップグレードして継続
- 利点: 移行コストほぼゼロ。`MaskingJsonGeneratorDecorator`(ValueMasker)による encoder 層 PII マスクが組み込みで使える唯一の案。StructuredArguments 等の表現力が最も豊富
- 欠点: 追加依存 1 つを維持。9.0 は非互換 API 再編あり(`JsonStreamContext` → `TokenStreamContext` 等)。PII 方針転換により組み込みマスクの優位が消滅
- リスク・未知数: encoder 独自機能に依存するほど将来の乗換コストが増える

### 選択肢 B: Logback + Spring Boot 標準構造化ログ(採用案)

- 概要: `logging.structured.format.console=logstash` による Boot 組み込みの JSON 出力。backend は Boot デフォルトの Logback を継続
- 利点: **追加依存ゼロ**(Boot 自前 JsonWriter、Jackson 非依存)。出力は現行 LogstashEncoder と同形(標準フィールド + MDC + fluent `addKeyValue` + markers→tags)で実質無移行。カスタムは properties(`logging.structured.json.add/exclude/rename`)+ `StructuredLoggingJsonMembersCustomizer`。#496 は「encoder 依存ごと削除」に単純化。`logback-spring.xml` も削除可能見込み(env var 切替に置換)
- 欠点: encoder 層 PII マスクの組み込みなし(PII 方針転換で要件外)。表現力は A に劣る(本プロジェクトの契約「動的 kv は string/number のみ」では差が出ない)
- リスク・未知数: Boot の構造化ログはまだ新しい機能群で、Boot アップグレード時の挙動変化に追従が要る

### 選択肢 C: Log4j2 + Spring Boot 標準構造化ログ

- 概要: backend を Log4j2(`spring-boot-starter-log4j2` = log4j-slf4j2-impl + log4j-core + log4j-jul、Jackson なし)に入替、JSON は Boot 標準(`StructuredLogLayout`)
- 利点: Async Logger(LMAX Disruptor)による高スループット。Boot 公式サポートの組み合わせ
- 欠点: backend 入替コスト(starter 入替 + 設定書換 + SLF4J provider 競合確認)。SLF4J fluent `addKeyValue` は log4j-slf4j2-impl が `CloseableThreadContext`(MDC 相当)経由で処理するため**値が string 化**される(2.26.0 ソース確認)。Boot デフォルトから外れ、毎 upgrade で追従確認が 1 軸増える
- リスク・未知数: セキュリティ event(`TENANT_CROSSED` 等)はクラッシュ時にも失われない同期書き込みが望ましく、Async Logger の喪失リスクと相性が悪い(per-logger 混在は可能だが運用複雑化)

### 選択肢 D: Log4j2 + JsonTemplateLayout

- 概要: Log4j2 ネイティブの JSON layout(`log4j-layout-template-json`、compile 依存は log4j-core + jctools-core のみ)
- 利点: garbage-free JSON 直列化で encoder 性能は最良。template JSON による形状自由度が最高
- 欠点: C の欠点すべて + template 学習・保守。性能優位が効くボトルネック(encoder CPU)が現負荷水準・現経路(stdout → awslogs → CloudWatch Logs)に存在しない
- リスク・未知数: C と同じ

## 3. 決定(Decision)

**採用**: 選択肢 B(Logback + Spring Boot 標準構造化ログ、logstash 形式)

`logging.structured.format.console=logstash` を環境別 env var(`LOGGING_STRUCTURED_FORMAT_CONSOLE`)で注入する。local / gha はテキスト出力、dev / stg / prd は JSON。スキーマ契約・環境別デフォルトは [docs/specs/ログ設計.md](../specs/ログ設計.md) が正本。

## 4. 理由(Rationale)

- **PII マスク要件の消滅が決定打**: 選定の決め手と位置づけていた encoder 層マスク(ValueMasker)は、PII 方針を「出力禁止(ID 参照のみ)」へ格上げしたことで要件から外れた。正規表現マスクは形式の定まるメール・電話にしか効かず、氏名・自由文には無力で、safety-net としても中途半端だった。基盤側 safety-net が必要なら CloudWatch Logs data protection policy で実現でき、アプリ実装と切り離せる(採否は S3Infra-3)
- **依存最小**: 追加依存ゼロで Jackson 2 残存ルート(#496)が依存削除で消える。脆弱性追従対象も減る
- **移行実質ゼロ**: Boot logstash 形式の出力は現行 LogstashEncoder と同形。MDC・fluent addKeyValue・markers の経路もすべて維持される
- **性能差は顕在化していない**: 現構成のボトルネックは stdout → awslogs → CloudWatch Logs 経路と取り込み従量(ADR-0005)であり、C / D の encoder 性能優位(garbage-free / Async Logger)が効く局面にない。捨てた利点として認識し、再評価トリガを §5 に明記する
- **同期書き込みの安全性**: セキュリティ event の確実な記録を優先し、Async Logger 前提の構成を避ける

## 5. 影響(Consequences)

### 良い影響(Positive)

- `net.logstash.logback:logstash-logback-encoder` を依存から削除でき、#496 のやること 1 が「9.0 アップグレード + 非互換確認」から「依存削除」に単純化される
- `logback-spring.xml` を削除し、出力形式・レベルとも env var 切替に一本化できる見込み(全環境共通イメージの原則と整合)
- JSON フィールド追加は properties 1 行(静的)/ fluent `addKeyValue`(動的)で完結する

### 悪い影響・制約(Negative)

- encoder 独自の表現力(StructuredArguments、ネスト構造、組み込みマスク)を放棄する。ログ設計.md の契約(string/number のみ・ネスト禁止)の範囲では実害なし
- Boot の構造化ログ機能へのロックイン。Boot アップグレード時に出力形の回帰確認が必要

### 既存ドキュメント・規約への波及

- [docs/specs/ログ設計.md](../specs/ログ設計.md) 新設(本 ADR と同 PR)
- コーディング規約 §7 改訂(PII 出力禁止への格上げ + 非意図的漏洩経路への対策)
- 設計規約 §4.2 からログ設計.md への参照追加
- #496: やること 1 を「logstash-logback-encoder の依存削除」に変更(Issue 本文に折込済の分岐)

### 再評価トリガ

以下のいずれかが観測されたら C / D(Log4j2 系)を再評価する:

1. ログ出力(encoder CPU / アロケーション)がプロファイル計測で有意なボトルネックとして顕在化した場合 → D(JsonTemplateLayout)が第一候補
2. Boot 標準構造化ログの機能不足(必要なカスタムが properties / Customizer で実現不能)が具体的に発生した場合 → A(logstash-logback-encoder)復帰を含めて再評価

## 6. 実装メモ(Implementation Notes)

- `webapi/build.gradle` から `logstash-logback-encoder` を削除(#496 と同期)
- `logback-spring.xml` / `logback-test.xml` の扱い: 標準構造化ログへの移行で削除できるか、`CONSOLE_LOG_STRUCTURED_FORMAT` 尊重の最小設定が要るかを実装時に確認
- ECS task definition(dev)に `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` を追加(S3Infra-3 / S2Infra-2 の管轄)
- 出力 JSON が現行 LogstashEncoder と同形であることをテストで確認(フィールド名・MDC・addKeyValue・stack_trace)

## 7. 参考リンク(References)

- Issue #451 / infra ADR-0005(ログ基盤)/ #496(Jackson 2 除去)
- [docs/specs/ログ設計.md](../specs/ログ設計.md)(スキーマ契約の正本)
- Spring Boot 4.0.6 Reference: Structured Logging(`logging.structured.format`、ecs / gelf / logstash、MDC + fluent addKeyValue 同梱を明記)— 2026-06-08 参照
- spring-boot v4.0.6 `starter/spring-boot-starter-log4j2/build.gradle`(starter 構成 = slf4j2-impl + core + jul)— 2026-06-08 参照
- apache/logging-log4j2 rel/2.26.0 `Log4jEventBuilder#log()`(fluent kv → CloseableThreadContext 経由で string 化)— 2026-06-08 参照
- logfellow/logstash-logback-encoder 9.0 release notes(2025-10-27、Jackson 3 移行・非互換 API 再編)— 2026-06-08 参照
- apache/logging-log4j2 releases(2.26.0 = 2026-05-07)/ `log4j-layout-template-json` 2.26.0 pom(compile 依存 = log4j-core + jctools-core)— 2026-06-08 参照
