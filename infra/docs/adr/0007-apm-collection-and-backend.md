# ADR-0007: アプリ性能測定/APM の収集経路と backend — ADOT Collector サイドカー + CloudWatch Application Signals(東京)

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, observability, apm, cloudwatch, xray, adot, terraform, nist

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: ADOT Collector サイドカー + CloudWatch / X-Ray(採用案)](#選択肢-a-adot-collector-サイドカー--cloudwatch--x-ray採用案)
  - [選択肢 B: アプリから OTLP を直接送出](#選択肢-b-アプリから-otlp-を直接送出)
  - [選択肢 C: 自前 backend を AWS に常駐(Tempo/Mimir/Loki or otel-lgtm)](#選択肢-c-自前-backend-を-aws-に常駐tempomimirloki-or-otel-lgtm)
  - [選択肢 D: Datadog 等の SaaS APM](#選択肢-d-datadog-等の-saas-apm)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

ADR-0029(アプリ側)で、性能測定の計装を **Spring Boot 4 公式 OpenTelemetry スターターによる OTLP 送出**に確定した。本 ADR は、その OTLP シグナルを **AWS(東京リージョン ap-northeast-1)でどう受け、どこで閲覧するか**(収集経路と backend)を確定する。

制約・前提:

- リージョンは **東京 ap-northeast-1 固定**(アプリ・データの所在を国内に保つ判断、レイテンシ・データ越境の観点)。
- 運用は **win2cot 単独**、コスト感度が高い(infra ADR-0005: 監視・ログの初期予算は数千円/月)。性能測定は全環境で常時必要ではなく、**設定で ON/OFF** できることが要件(ADR-0029 §3.2)。
- infra ADR-0005 で **ログ・監視は CloudWatch に single pane of glass で集約**する方針(ECS/RDS/ALB メトリクスと同一基盤)を確定済み。
- **X-Ray の OTLP エンドポイントは SigV4 認証が必須**で、OpenTelemetry Java SDK は HTTP クライアントの差し込み・リクエスト署名のフックを持たないため(opentelemetry-java #7002 は未解決)、**アプリから直接 SigV4 署名して送ることは現実的でない**。
- 実行基盤は ECS on Fargate([ADR-0008](../../../docs/adr/0008-graalvm-native-image.md))。Private サブネットからの外向き通信経路は infra ADR-0003 で設計済み(`logs` 等は NAT / Interface Endpoint)。

調査日付: 2026-06-12。一次ソースは §7 参照。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: ADOT Collector サイドカー + CloudWatch / X-Ray(採用案)

- 概要: ECS タスク内に AWS Distro for OpenTelemetry(ADOT)Collector をサイドカーとして配置。アプリは無署名 OTLP を `localhost` に送り、Collector が `sigv4auth` 拡張で署名して送出する。トレースは X-Ray OTLP エンドポイント → CloudWatch Application Signals、メトリクスは CloudWatch EMF。
- 利点: SigV4 署名という横断関心事をアプリから切り出せる。Application Signals は東京で GA(throughput / レイテンシ / エラーの標準ダッシュボード + transaction search)。backend が CloudWatch に揃い infra ADR-0005 の single pane と整合。新規 backend インフラ(常駐サーバ)が不要で IAM とサイドカーのみ。
- 欠点: タスクにサイドカー 1 個分の Fargate リソース(vCPU/メモリ)が増える。Collector 設定の保守が要る。
- リスク・未知数: メトリクスのネイティブ OTLP 受信は東京がプレビュー対象外のため EMF 経由になる(将来 GA 化で見直し)。

### 選択肢 B: アプリから OTLP を直接送出

- 概要: サイドカーを置かず、アプリの OTLP エクスポータから直接 CloudWatch / X-Ray エンドポイントへ送る。
- 利点: サイドカー不要で構成が単純。
- 欠点: X-Ray OTLP は SigV4 必須だが、OpenTelemetry Java SDK は署名フックを持たない(#7002)。自前のカスタムエクスポータで署名する道はあるが SDK 設計に逆らう保守負債になる。
- リスク・未知数: SDK 側 SigV4 対応の時期は未定。採用不可。

### 選択肢 C: 自前 backend を AWS に常駐(Tempo/Mimir/Loki or otel-lgtm)

- 概要: OTLP 受信 + 保存 + 可視化(Grafana 等)を AWS 上に常駐させる。
- 利点: Grafana / PromQL の表現力。
- 欠点: 常時稼働の固定費・パッチ・スケールのお守りが単独運用に重い。CloudWatch 集約(single pane)から外れる。なお `otel-lgtm` は揮発ストレージのデモ用途で本番に使うものではない(ローカル開発の手元確認には別途使う)。
- リスク・未知数: MVP の低予算と不適合。

### 選択肢 D: Datadog 等の SaaS APM

- 概要: OTLP を SaaS APM に直接送る。
- 利点: 高機能。OTLP 標準ゆえ将来差し替え可能。
- 欠点: 追加のサブスクリプション費用。single pane(CloudWatch)から分断。
- リスク・未知数: MVP のコスト方針と不適合。OTLP 標準なので将来必要になれば送出先変更で移行できる。

## 3. 決定(Decision)

**採用**: 選択肢 A — **ADOT Collector をサイドカーとして配置し、CloudWatch / X-Ray を backend とする。**

- **収集経路**: アプリ → 無署名 OTLP(`localhost`)→ ADOT Collector サイドカー(`sigv4auth`)→ AWS。
- **トレース**: X-Ray OTLP エンドポイント(`https://xray.ap-northeast-1.amazonaws.com/v1/traces`)→ **CloudWatch Application Signals** で閲覧(東京 GA)。
- **メトリクス**: ADOT の **CloudWatch EMF エクスポータ**経由で CloudWatch メトリクス(OTLP メトリクスのネイティブ受信は東京プレビュー対象外のため)。
- **ログ**: 既存どおり Fargate の `awslogs` ドライバ → CloudWatch Logs(infra ADR-0005 / [ADR-0019](../../../docs/adr/0019-structured-logging-boot-standard.md))。OTel ログシグナルの OTLP 送出は採らない。
- **閲覧**: すべて CloudWatch コンソール(Application Signals / メトリクス / Logs)。ALB 入口や Grafana は設けない。
- **IAM**: X-Ray トレース取り込みと CloudWatch メトリクス出力に必要な**最小権限**を付与し、Terraform規約に従い**権限はそれを使うリソースと同一 PR**で追加する。リソース ARN スコープを基本とし、ARN スコープ不可のアクション(例: `cloudwatch:PutMetricData`)は条件キー(namespace 等)で絞る。正確なアクション集合は実装時に AWS 公式ドキュメントで確定する。
- **ON/OFF とコスト**: 環境別に外部化プロパティで切替(既定 OFF、dev 中心に必要時 ON)。送出量(= Application Signals の span データ量課金)はサンプリングで抑える。サイドカー付きタスク定義を必要時のみ deploy する余地も残す。
- **再評価トリガ**: CloudWatch のネイティブ OTLP メトリクスが東京で GA 化したら、EMF 経由からの切替を再評価する。

## 4. 理由(Rationale)

- X-Ray OTLP の SigV4 要件をアプリに持ち込まず、サイドカーに分離できる(選択肢 B の保守負債を回避)。
- Application Signals が東京で GA で、パフォーマンステストに必要な throughput / レイテンシ / エラーのダッシュボードと transaction search を新規インフラなしで得られる。
- backend が CloudWatch に揃い、infra ADR-0005 の single pane of glass と矛盾しない(別系統の監視運用を増やさない)。
- 単独運用・低予算と整合(常駐 backend やサブスクリプションを避け、コストは ON/OFF + サンプリングで制御)。
- OTLP 標準を境界に保つため、将来 backend を AMP+Managed Grafana や SaaS へ変えてもアプリ・収集経路の大半を再利用できる。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 新規常駐基盤なしで APM(トレース + メトリクス)を東京で実現できる。
- 認証(SigV4)・リトライ・将来の機微情報フィルタを Collector に集約できる。
- ログ・メトリクス・トレースの一次参照先が CloudWatch に統一される。

### 悪い影響・制約(Negative)

- Fargate タスクにサイドカー 1 個分のリソース増。コストは ON/OFF・サンプリング・必要時 deploy で抑える。
- メトリクスは当面 EMF 経由(東京で OTLP メトリクス未 GA)。
- Collector 設定(receiver / sigv4auth / exporter)の保守が新たに発生する。
- X-Ray / `monitoring`(CloudWatch)宛の外向き通信経路を infra ADR-0003 のカタログに追加する必要がある(NAT / Interface Endpoint の選択)。

### 既存ドキュメント・規約への波及

- infra ADR-0003(private-subnet-outbound): 外向き宛先に X-Ray / CloudWatch(`monitoring`)を追記。
- infra ADR-0005(logging-platform): 監視の single pane に APM(Application Signals)を含める旨を補記。
- Terraform 規約: 最小権限 IAM を ADOT 用に追加(リソースと同一 PR)。
- `docs/specs/基本設計書.md`: 監視・APM 構成の節を追補。
- ADR-0029(本 ADR の app 側 companion): 計装・ON/OFF・診断の SSOT。

## 6. 実装メモ(Implementation Notes)

- ADOT Collector イメージ: `public.ecr.aws/aws-observability/aws-otel-collector` をサイドカーとして ECS タスク定義に追加。
- Collector 設定の骨子:
  - `receivers`: `otlp`(grpc `4317` / http `4318`、`localhost` 受信)
  - `extensions`: `sigv4auth`(region = `ap-northeast-1`)
  - `exporters`: トレース = `otlphttp`(X-Ray OTLP エンドポイント + `sigv4auth`)、メトリクス = `awsemf`
  - `service.pipelines`: `traces` / `metrics` を上記で結線
- IAM: X-Ray のトレース取り込みと EMF(CloudWatch Logs / メトリクス)出力に必要なアクションのみ。`PutMetricData` 等 ARN 非対応アクションは namespace 条件で限定。実装 PR で AWS 公式の必要権限を確認して確定。
- ネットワーク: Private サブネットから X-Ray / CloudWatch への到達経路(NAT or Interface Endpoint)を infra ADR-0003 に沿って用意。
- ローカル開発: 本 ADR の対象外。手元確認は `grafana/otel-lgtm` を docker-compose 連携で起動し、アプリから OTLP を直接受ける(常駐 backend とは別物)。

## 7. 参考リンク(References)

- ADR-0029(アプリ性能測定・診断の app 側方針、本 ADR の companion)
- infra ADR-0003(private-subnet-outbound)/ infra ADR-0005(logging-platform、single pane of glass)/ [ADR-0008](../../../docs/adr/0008-graalvm-native-image.md)/ [ADR-0019](../../../docs/adr/0019-structured-logging-boot-standard.md)
- AWS, "OTLP Endpoints — Amazon CloudWatch": <https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-OTLPEndpoint.html>
- AWS, "Application Signals provides OTEL support via X-Ray OTLP endpoint for traces": <https://aws.amazon.com/about-aws/whats-new/2024/11/application-signals-otel-x-ray-otlp-endpoint-traces/>
- AWS Distro for OpenTelemetry, "Adding the Sigv4 Extension to the ADOT Collector": <https://aws-otel.github.io/docs/sigv4/>
- opentelemetry-java Issue #7002(OTLP exporter の SigV4 対応): <https://github.com/open-telemetry/opentelemetry-java/issues/7002>
- AWS, "Amazon CloudWatch now supports OpenTelemetry metrics in public preview": <https://aws.amazon.com/about-aws/whats-new/2026/04/amazon-cloudwatch-opentelemetry-metrics/>
