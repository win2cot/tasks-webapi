# ADR-0029: アプリ性能測定と診断の方針(OpenTelemetry / Micrometer + heap・GC・リーク診断)

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: observability, performance, diagnostics, native-image, dependencies

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: OpenTelemetry Java Agent(ゼロコード計装)](#選択肢-a-opentelemetry-java-agentゼロコード計装)
  - [選択肢 B: コミュニティ版 OpenTelemetry Spring Boot Starter](#選択肢-b-コミュニティ版-opentelemetry-spring-boot-starter)
  - [選択肢 C: Spring Boot 4 公式 OpenTelemetry Starter(採用案)](#選択肢-c-spring-boot-4-公式-opentelemetry-starter採用案)
  - [選択肢 D: 既存 Prometheus レジストリ継続のみ](#選択肢-d-既存-prometheus-レジストリ継続のみ)
- [3. 決定(Decision)](#3-決定decision)
  - [3.1 常時計装(テレメトリ)](#31-常時計装テレメトリ)
  - [3.2 有効・無効の切替(ON/OFF)](#32-有効無効の切替onoff)
  - [3.3 オンデマンド診断(heap・GC・リーク)](#33-オンデマンド診断heapgcリーク)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約・コードへの波及](#既存ドキュメント規約コードへの波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
  - [6.1 計装の導入](#61-計装の導入)
  - [6.2 ON/OFF とコストガードレール](#62-onoff-とコストガードレール)
  - [6.3 診断: JVM モードと Native Image モードの能力マトリクス](#63-診断-jvm-モードと-native-image-モードの能力マトリクス)
  - [6.4 リーク検知の運用戦略](#64-リーク検知の運用戦略)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

tasks-webapi は実行バイナリとして GraalVM Native Image を採用し([ADR-0008](0008-graalvm-native-image.md)、ランタイム JDK は [ADR-0021](0021-runtime-jdk-25.md))、Native Image 化は完了している。本格的なパフォーマンステストはこれからで、その前提として **アプリ内部の処理性能を測定する手段** と、**性能問題・メモリ問題を診断する手段** を整備する必要がある。

現状と制約:

- 計装の常設手段が未整備。アプリ内のリクエスト・メソッド単位の処理時間、ヒープ・GC の挙動を継続的に観測する仕組みがない。
- 既存スキャフォールドに `spring-boot-starter-actuator` と `io.micrometer:micrometer-registry-prometheus` が含まれ、`/actuator/prometheus` を公開している(PR #292 / Sprint 0 セットアップ #89 由来)。ただし **これをスクレイプする Prometheus サーバは存在せず**、メトリクスの backend は未確定で実質休眠状態である(infra ADR-0005 はログ基盤のみを確定している)。
- 性能測定は **全環境で常時必要ではない**。コスト感度が高い(infra ADR-0005: 監視・ログの初期予算は数千円/月)ため、**設定で有効・無効を切り替えられる**ことが要件。
- `@Profile` は **禁止**(コーディング規約 §20.2: Native Image の AOT が profile 分岐を build-time に解決するため実行時切替が機能しない)。したがって環境差・ON/OFF は **外部化プロパティ(環境変数 / Parameter Store)** で吸収する必要がある。
- Native Image は通常 JVM 上で動かないため、`MemoryPoolMXBean` 等の JVM 管理 Bean が存在せず、Micrometer の `JvmGcMetrics` バインダがバインド時に no-op 化する。すなわち **Native では `jvm.gc.pause` や詳細なヒープ・プール系メトリクスが取得できない**。このため診断手段は JVM モードと Native Image モードで別建てになる。

スコープ:

- 本 ADR は **アプリ側**(計装・ON/OFF・診断手段)を確定する。
- テレメトリの **収集経路・backend 基盤**(OTLP Collector のサイドカー、CloudWatch / X-Ray、IAM)は infra の関心であり、companion の **infra ADR-0007** で確定する。本 ADR は infra ADR-0007 を前提として参照する。

調査日付: 2026-06-12。一次ソースは §7 参照。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: OpenTelemetry Java Agent(ゼロコード計装)

- 概要: `-javaagent:opentelemetry-javaagent.jar` を起動時に付与し、バイトコード書き換えで自動計装する。
- 利点: コード変更ゼロで HTTP / JDBC / Spring 等を自動計装できる。
- 欠点: agent はライブラリのバイトコードを書き換えるため版ずれで不具合が出やすい。
- リスク・未知数: **Native Image と非両立**。Native Image は Substrate VM 上で動き、実行時の agent attach・バイトコード書き換えができない。本プロジェクトの [ADR-0008](0008-graalvm-native-image.md) と根本的に衝突するため採用不可。

### 選択肢 B: コミュニティ版 OpenTelemetry Spring Boot Starter

- 概要: `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` を使う。
- 利点: agent 不要でコンパイル時計装。Native Image との両立は選択肢 C と同様に可能。
- 欠点: OpenTelemetry 自身が「agent が既定、starter は agent が使えない場合の次善」と位置づけている。`-alpha` 接尾辞の依存を引き込む。設定は `otel.*` 系で、Spring Boot の `management.*` 系とは別系統。
- リスク・未知数: alpha 依存の安定性。Spring Boot 標準との二系統設定による混乱。

### 選択肢 C: Spring Boot 4 公式 OpenTelemetry Starter(採用案)

- 概要: Spring Boot 4.0 で新設された `spring-boot-starter-opentelemetry` を使う。中身は Micrometer ベース(Observation API + `micrometer-tracing-bridge-otel` + `micrometer-registry-otlp`)で、シグナルを **OTLP** で送出する。
- 利点: agent 不要のコンパイル時 / AOT 計装で **Native Image と両立**。Micrometer Observation API は 1 つの計装からトレースとメトリクスの両方を生成できる(`@Observed` / `@SpanTag`)。設定は Spring Boot 標準の `management.*` 系に統一。OTLP は標準プロトコルで **backend 非依存**(将来 backend を差し替えてもアプリ不変)。HTTP / JDBC / `RestClient` 等は自動計装され、W3C trace-context 伝播も既定で行われる。
- 欠点: ログシグナルの OTLP 送出は alpha 版 appender 依存で、PII 方針([ADR-0019](0019-structured-logging-boot-standard.md) / #451)との整合確認が要る。
- リスク・未知数: 公式スターターは 2025-11-20 リリースで比較的新しい。Micrometer のメトリクスを OpenTelemetry セマンティック規約名で出すには追加 Bean 定義が要る(将来改善予定)。

### 選択肢 D: 既存 Prometheus レジストリ継続のみ

- 概要: 現状の `micrometer-registry-prometheus`(`/actuator/prometheus` スクレイプ)を正式採用し、Prometheus / AMP をスクレイプ方式で運用する。
- 利点: 依存追加なし(既存)。
- 欠点: **メトリクスのみでトレース(分散・メソッド単位の処理時間)を取得できない**。スクレイプ方式のため Prometheus サーバ / AMP の常駐運用が必要(単独運用・コスト感度と相性が悪い)。APM(Application Signals 相当)が得られない。
- リスク・未知数: 「処理性能測定」の主目的(処理時間の内訳・N+1 検出)を満たせない。

## 3. 決定(Decision)

### 3.1 常時計装(テレメトリ)

**採用**: 選択肢 C — **Spring Boot 4 公式 `spring-boot-starter-opentelemetry` を採用する。**

- **トレースとメトリクスをコア**とする。計装は Micrometer Observation API(`@Observed` / `@SpanTag`)と自動計装(HTTP / JDBC / クライアント)で行う。
- **メトリクスの送出は OTLP プッシュに一本化**し、休眠中の **`micrometer-registry-prometheus` 依存と `management.endpoints.web.exposure.include` の `prometheus` を撤去する**。ローカルも OTLP レシーバ(otel-lgtm)で受けられるため、プル型スクレイプ経路は不要で二重手段を持たない。
- **ログ**は既存の CloudWatch Logs([ADR-0019](0019-structured-logging-boot-standard.md))を一次経路とする。OTel ログシグナルの OTLP 送出は alpha 依存 + #451(PII を出さない方針)との整合が要るため、**本 ADR のスコープ外(将来オプション)**とする。トレース ON 時は Spring Boot がログ行に trace ID / span ID を自動付与するため、CloudWatch Logs のままでもトレースとの相関は取れる。
- 送出先(OTLP Collector・CloudWatch / X-Ray・IAM)は **infra ADR-0007** で確定する。

### 3.2 有効・無効の切替(ON/OFF)

- ON/OFF は **外部化プロパティ**(`management.*` プロパティ + 環境変数 / Parameter Store 注入)で行う。**`@Profile` は使用しない**(コーディング規約 §20.2)。
- Native Image は closed-world のため「バイナリから計装を抜き差し」はできない。**ライブラリは常にバイナリへ同梱し、実行時プロパティで有効・無効を切り替える**。無効時は OTLP エンドポイント未設定 + サンプリング 0 でオーバーヘッドを実質ゼロにする。
- **既定は無効(OFF)**。パフォーマンステスト等の必要時に有効化し、サンプリング率で送出量(= 課金)を制御する。

### 3.3 オンデマンド診断(heap・GC・リーク)

性能測定(常時メトリクス)とは別軸の **オンデマンド診断手段** を、JVM モードと Native Image モードの双方について正式化する(詳細マトリクスは §6.3)。

- **JVM モード**: Micrometer の JVM メトリクス(ヒープ・GC・スレッド)をフルに取得できる。加えて JFR(Java Flight Recorder)・ヒープダンプ(`jmap` / `-XX:+HeapDumpOnOutOfMemoryError`)・Eclipse MAT による解析を標準手段とする。
- **Native Image モード**: JVM 管理 Bean が無くヒープ・GC メトリクスは取得できない。代替として **(1) コンテナの RSS トレンド(ECS Container Insights)を第一線の継続観測**、**(2) `--enable-monitoring=heapdump` によるヒープダンプ取得 → Eclipse MAT で確定診断**、**(3) `--enable-monitoring=jfr` による GC・アロケーションのトレンド把握(リーク専用機能は制限あり)**、必要に応じて NMT(Native Memory Tracking)を用いる。
- `--enable-monitoring=...` は **ビルド時フラグ**(closed-world)であり、診断ビルドバリアントとして整備する(§6.3 / [ADR-0018](0018-container-image-build-with-boot-build-image.md))。
- **リーク検知**は、詳細なヒープ・GC を要するソークテスト(soak test / ヒートラン)を **JVM ビルドで実施**し、Native ビルドでは RSS トレンド + ヒープダンプで確認する二段構えとする(§6.4)。

## 4. 理由(Rationale)

- **Native Image 両立が必須要件**。agent 方式(選択肢 A)は Substrate VM 上で動作せず [ADR-0008](0008-graalvm-native-image.md) と衝突する。公式スターターは agentless のコンパイル時 / AOT 計装で両立する。
- **OTLP 標準により backend 非依存**。アプリは OTLP を吐くだけで、ローカル(otel-lgtm)・AWS(CloudWatch / X-Ray)・将来の別 backend へ送出先だけ変えられる。リージョンや backend 選定にアプリ実装が縛られない。
- **Observation API で計装が一本化**。`@Observed` 1 つでトレースとメトリクスを同時に得られ、メトリクス専用の選択肢 D より目的(処理時間の内訳・N+1 検出)に直接届く。
- **既存方針との整合**。`@Profile` 禁止(コーディング規約 §20.2)と外部化プロパティ ON/OFF が一致。APM を CloudWatch に寄せる方向は infra ADR-0005 の single pane of glass(ECS/RDS/ALB と同一基盤)と整合する。
- **コスト制御は ON/OFF + サンプリングで取る**。常時全量送出ではなく、必要時のみ有効化しサンプリングで量を絞る方式が、単独運用・低予算(infra ADR-0005)に合う。トレードオフとして、常時計装で得られる「平時のベースライン」は犠牲になるが、MVP 段階では必要時計測で足りると判断した。

## 5. 影響(Consequences)

### 良い影響(Positive)

- メソッド・リクエスト単位の処理時間が滝(waterfall)で可視化され、N+1 などの性能問題を特定しやすくなる。
- 計装が Spring Boot 標準(`management.*`)に統一され、設定の二系統化を避けられる。
- メトリクス経路が OTLP プッシュ一本になり、休眠していたプル型(Prometheus)との二重手段が解消される。依存が 1 つ減り、Jackson 2 残存ルート削減・Native フットプリントにも微小ながら寄与する。
- 診断手段が JVM / Native の双方で文書化され、Native でも「リークが追えない」状態に陥らない。

### 悪い影響・制約(Negative)

- Native Image ではヒープ・GC の詳細メトリクスが取得できない(JVM 管理 Bean 不在)。診断は RSS + ヒープダンプ + JFR で補完し、深掘りは JVM ビルドで行う必要がある。
- メトリクスのネイティブ OTLP 受信は CloudWatch では東京リージョンが現状プレビュー対象外のため、メトリクスは EMF 経由になる(infra ADR-0007)。
- ログシグナルの OTLP 送出は本 ADR では見送り(alpha 依存 + #451 整合)。トレースとログの統合 UI 表示は将来課題。
- 公式スターターは新しく、セマンティック規約名対応など一部に追加設定・将来改善待ちの箇所がある。

### 既存ドキュメント・規約・コードへの波及

- `webapi/build.gradle`: `spring-boot-starter-opentelemetry` を追加、`micrometer-registry-prometheus` を撤去。
- `webapi/src/main/resources/application.yml`: `management.endpoints.web.exposure.include` から `prometheus` を除去、OTLP / トレーシング / サンプリングの `management.*` プロパティを追加(既定は OFF)。
- **infra ADR-0007(新規)**: 収集経路(ADOT Collector サイドカー)・backend(CloudWatch / X-Ray Application Signals)・最小権限 IAM を確定。
- `docs/specs/設計規約.md` / `docs/specs/基本設計書.md`: 観測性(性能測定・診断)の節を追補。
- `docs/specs/コーディング規約.md`: `@Observed` の使用指針(計装の置き場所・命名)を追補。`@Profile` 禁止(§20.2)は既存のまま整合。
- ギャップ分析(`docs/reviews/2026-05-10-scaffold-vs-design-gap-analysis.md`)の `/actuator/prometheus` 言及は本 ADR で上書き(OTLP 一本化)。

## 6. 実装メモ(Implementation Notes)

### 6.1 計装の導入

- 依存: `org.springframework.boot:spring-boot-starter-opentelemetry`(`micrometer-tracing-bridge-otel` と `micrometer-registry-otlp` を同梱)。`micrometer-registry-prometheus` は削除。
- 自動計装: HTTP サーバ、JDBC、`RestClient` / `RestClientBuilder` 等。クライアントは必ずビルダー経由で生成する(自前 `new` は trace-context 伝播が効かない)。
- 手動計装: 処理時間を測りたいメソッドに `@Observed(name = "...")`、引数の記録に `@SpanTag`。テナント識別子等の機微情報を span 属性に載せない(#451 整合)。
- スレッド跨ぎ(`@Async` / `AsyncTaskExecutor`)では `ContextPropagatingTaskDecorator` を Bean 定義し trace-context を伝播させる。

### 6.2 ON/OFF とコストガードレール

- 主要プロパティ(いずれも外部化、既定 OFF):
  - トレース送出先: `management.opentelemetry.tracing.export.otlp.endpoint`(未設定なら送らない)
  - サンプリング率: `management.tracing.sampling.probability`(既定 0、計測時に引き上げ)
  - メトリクス送出先: `management.otlp.metrics.export.url`
- 既定 OFF + 計測時のみ ON + サンプリングで送出量を抑える。Collector サイドカー付きタスク定義を必要時のみ deploy する選択肢も残す(infra ADR-0007)。

### 6.3 診断: JVM モードと Native Image モードの能力マトリクス

| 観測対象 | JVM モード | Native Image モード |
|----------|-----------|---------------------|
| ヒープ使用量(領域・プール別) | Micrometer `jvm.memory.used/committed/max`、`jvm.memory.usage.after.gc` | JVM 管理 Bean 不在のため **取得不可**。代替: コンテナ RSS(Container Insights) |
| GC ポーズ・頻度 | Micrometer `jvm.gc.pause`(回数・合計・最大)、`jvm.gc.overhead` | `JvmGcMetrics` が no-op。代替: JFR の GC イベント(`--enable-monitoring=jfr`) |
| アロケーション速度・昇格量 | `jvm.gc.memory.allocated` / `promoted` | JFR で部分的に取得 |
| スレッド・クラスロード | `jvm.threads.*` / `jvm.classes.*` | 一部のみ(値が入らない場合あり) |
| プロセス実メモリ(RSS) | OS / コンテナ指標 | OS / コンテナ指標(**Native の第一線**、Java ヒープ外も捕捉) |
| ヒープダンプ | `jmap` / `-XX:+HeapDumpOnOutOfMemoryError` → Eclipse MAT | `--enable-monitoring=heapdump`(SIGUSR1 / OOM / `-XX:+DumpHeapAndExit`)→ Eclipse MAT |
| フライトレコーダ(JFR) | フル機能 | `--enable-monitoring=jfr`(**制限あり**: スタックトレース・リーク検知 `jdk.OldObjectSample` は未成熟・進行中) |
| ネイティブメモリ追跡(NMT) | — | Native Image の NMT で Java ヒープ外の増加を切り分け |

- Native の `--enable-monitoring=...` はビルド時フラグ(closed-world)。**非本番の Native ビルドに `heapdump` を常時付与**(トリガーされるまでオーバーヘッドほぼ無し)し、**ソークテスト用ビルドに `jfr` を追加**する。bootBuildImage([ADR-0018](0018-container-image-build-with-boot-build-image.md))の診断バリアントとして整備する。

### 6.4 リーク検知の運用戦略

- ほとんどのメモリリークはロジック起因(無制限キャッシュ、static コレクション、外し忘れリスナー、ThreadLocal)で、JVM・Native のどちらでも同様に顕在化する。
- **JVM ビルド**: ソークテスト中に `jvm.memory.usage.after.gc` の右肩上がり等でリークを検知し、序盤・終盤のヒープダンプ差分を Eclipse MAT(dominator tree)で確定診断する。
- **Native ビルド**: Container Insights の RSS トレンドを第一線の signal とし(負荷一定で右肩上がり = リーク疑い)、ヒープダンプ(`--enable-monitoring=heapdump`)で確定診断する。
- パフォーマンステストの目的分担: **JVM = GC / ヒープの深掘り**、**Native = 起動時間・メモリフットプリント・スループット**。

## 7. 参考リンク(References)

- [ADR-0008](0008-graalvm-native-image.md)(GraalVM Native Image 採用)/ [ADR-0021](0021-runtime-jdk-25.md)(ランタイム JDK 25)/ [ADR-0018](0018-container-image-build-with-boot-build-image.md)(bootBuildImage)/ [ADR-0019](0019-structured-logging-boot-standard.md)(構造化ログ)
- infra ADR-0005(ログ基盤 = CloudWatch Logs、single pane of glass)/ infra ADR-0007(本 ADR の companion、収集経路・APM backend)
- コーディング規約 §20.2(`@Profile` 禁止)
- Spring Blog, "OpenTelemetry with Spring Boot"(2025-11-18): <https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/>
- Micrometer, "JVM Metrics": <https://docs.micrometer.io/micrometer/reference/reference/jvm.html>
- Micrometer Issue #2637(Native Image でのメトリクス制限調査): <https://github.com/micrometer-metrics/micrometer/issues/2637>
- GraalVM, "Create a Heap Dump from a Native Executable": <https://www.graalvm.org/latest/reference-manual/native-image/guides/create-heap-dump/>
- GraalVM, "JDK Flight Recorder (JFR) with Native Image": <https://www.graalvm.org/latest/reference-manual/native-image/debugging-and-diagnostics/JFR/>
- GraalVM, "Native Memory Tracking (NMT) with Native Image": <https://www.graalvm.org/jdk24/reference-manual/native-image/debugging-and-diagnostics/NMT/>
- Amazon ECS Container Insights metrics: <https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html>
