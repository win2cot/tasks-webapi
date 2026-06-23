# ADR-0005: ログ基盤選定 — MVP は Amazon CloudWatch Logs(構造化 JSON + Logs Insights)

- **Status**: Accepted
- **Date**: 2026-06-06
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, observability, logging, cloudwatch, terraform, nist

> **2026-06-08 注記(infra ADR-0006)**: stg/prd の ALB に WAF(REGIONAL Web ACL)を導入する決定に伴い、§3 platform スコープのログカタログに WAF アクセスログ行を追加した(下表)。ログ基盤選定の技術決定は不変。

> **2026-06-21 注記(infra ADR-0007)**: ADOT Collector サイドカー導入に伴い、§3 の single pane of glass に APM(CloudWatch Application Signals / トレース・メトリクス)を含める。ログ基盤選定の技術決定は不変。

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

### 背景

MVP リリース時点で採用するログ基盤を確定する。`docs/architecture/infrastructure-plan.md` v5 §7(並行ストリーム P-LOG-A)および `docs/specs/開発計画書.md` v1.3 §4.3.2 で「ログ基盤選定 ADR は **Phase 1 Sprint 1 Infra 着手までに方針確定が望ましい**」とされ、実装は Phase 1 Sprint 3 Infra S3Infra-3 で行う計画になっている。本 ADR はその方針(どの基盤を採るか・何を MVP scope に含めるか・将来どう乗せ換えるか)を確定し、S3Infra-3 が参照できる状態にする。

候補は CloudWatch Logs / Amazon OpenSearch Service / Grafana Loki(self-hosted)/ S3 + Athena の 4 つ。infrastructure-plan v5 §6 のリスクシナリオでは「選定が長引いた場合は CloudWatch Logs 暫定採用、本格選定は MVP 後」とされていたが、本 ADR では**この段階で 4 候補を実際に比較したうえで採用基盤を確定する**(暫定回避ではなく正式選定)。

前提として次が確定している。

- 実行基盤は **ECS on Fargate**(EC2 不使用、ADR-0003 / infrastructure-plan §2)。
- MVP 稼働環境は **local + gha + dev のみ**。stg / prd は Post-Sprint-0(infrastructure-plan §2)。
- 運用は **win2cot 単独**、コスト感度が高い(数千円/月 が監視・ログの初期予算、開発計画書 §の費用見積)。
- タイムゾーンは全層 JST 統一(ADR-0009)。ログのタイムスタンプも JST。
- ログ・監視は **tasks 専用**の関心事(ADR-0004 の専用/共有 仕分けで、ECS / per-task 課金リソース側)。共有 platform にログ基盤を立てる必要は MVP ではない。
- 開発・運用で **Claude Code / claude-automation(AI エージェント)を多用**しており、ログは人だけでなく **AI もプログラマティックに参照**する前提が現実的にある。

### MVP で必要なログ用途

- **アプリケーションログ**: tasks-webapi(Spring Boot)/ Keycloak の構造化(JSON)ログ。Fargate の log driver で送出。
- **アクセスログ**: ALB(L7)+ CloudFront(フロント配信)アクセスログ。運用者の参照は CloudWatch Logs に集約する(ALB は S3 → Lambda 転送、CloudFront は standard logging v2 で直接出力)。
- **監査ログ**: 業務監査(`audit_logs` テーブルが SoT、ADR-0013)とは別に、**クロステナント違反検知ログ等のセキュリティ監査証跡**(D-4 #438 で実装済)を保持・検索可能にする。
- **検索 / 調査**: 障害調査・テナント越境調査のためのアドホック検索(単独運用なので「軽量で十分」)。**人の参照(Console / クエリ)と AI の参照(MCP / SDK)の両方**を想定する。
- **アラート連携**: 特定パターン(ERROR 急増・クロステナント違反)を検知して通知。

### 保持要件(2026-06-06 確定)

| ログ種別 | 保持期間(MVP) | 備考 |
|---|---|---|
| アプリ / アクセスログ | 14 日 | 環境ごとに可変(Terraform 変数)。「約 10 日」の意図を CloudWatch Logs の許容値(離散)最近接の 14 に丸め |
| 監査ログ(セキュリティ証跡) | 1 か月(30 日) | 環境ごとに可変。長期化要件が出たら §3 の長期保管層へ |

いずれも環境ごとに可変とする(dev は短く、将来 prd は要件に応じて延長)。**MVP 段階の保持は短期で、長期保管(年単位)は当面要件に入らない**。これは選定に強く効く(後述)。

### 制約・非機能

- 単独運用ゆえ、**常時稼働の運用負荷(パッチ・スケール・監視のお守り)を持つ基盤は割に合わない**。
- **固定費(idle でも課金される基盤)は MVP の月予算(数千円)を容易に食い潰す**。
- **ログの参照は人だけでなく AI(障害解析エージェント・claude-automation 等)も行う**前提で、プログラマティック / MCP からのアクセスしやすさも評価軸に含める。
- NIST SP 800-53 の監査系 control(AU 系)との整合は Sprint 4 Infra S4Infra-2 で本格整備するが、本 ADR で採用基盤が AU 系を満たせる土台かを確認しておく。

### 参照ドキュメント

- `docs/architecture/infrastructure-plan.md` v5 §7(P-LOG-A)/ §6(リスクシナリオ)/ §3(構成)
- `docs/specs/開発計画書.md` v1.3 §4.3.2(並行ストリーム)
- `infra/docs/adr/0003-private-subnet-outbound.md`(CloudWatch Logs は `logs` 宛 outbound として既に経路設計に含まれる)
- `infra/docs/adr/0004-platform-project-infra-separation.md`(専用/共有 仕分け)
- `docs/adr/0009-jst-unified-timezone-policy.md`(タイムスタンプ JST)
- `docs/adr/0013-audit-log-field-diff-strategy.md`(業務監査ログの SoT は DB テーブル)

---

## 2. 検討した選択肢(Options Considered)

価格はいずれも 2026-06 時点・ap-northeast-1(東京)の概算。MVP のログ量は単一アプリ + Keycloak + dev 部分稼働で **月 3〜5 GB 程度**(構造化 JSON、短期保持)と見積もる。

### 評価軸サマリ

Issue #326 の議論ポイント(コスト / 検索性能 / 保持 / 構造化 / アラート連携 / 将来移行)に加え、**参照のしやすさ**(人の探索 UX と **AI / プログラマティック参照** の両面)と**監視・アラート機構との相性**を明示軸として全候補に揃えて評価する。

| 評価軸 | (a) CloudWatch Logs | (b) OpenSearch | (c) Loki(self-host) | (d) S3 + Athena |
|---|---|---|---|---|
| 初期費用 | ほぼゼロ(ロググループのみ) | クラスタ構築 | 構築負荷大 | バケット + Firehose/Glue |
| 固定費(idle) | なし(従量のみ) | 高($26〜350+/月) | 中(常時稼働タスク) | ほぼなし |
| 運用負荷 | ゼロ(マネージド) | 高(クラスタ運用) | 最大(自前運用) | 低 |
| 参照のしやすさ(人の探索 UX) | 中(Console / Live Tail / Logs Insights、ダッシュボードは弱) | 高(Dashboards・全文ドリルダウン) | 中〜高(Grafana Explore / LogQL) | 低(SQL バッチ、即時性なし) |
| AI / プログラマティック参照 | 高(AWS 公式 CloudWatch MCP / Log-Analyzer MCP・AWS SDK・IAM 統一) | 中〜高(REST/Query DSL は強力、MCP は Bedrock AgentCore で別途構築) | 中(HTTP/LogQL API、MCP は自前 endpoint 運用) | 中(Athena SQL API、バッチ分析向き) |
| 検索性能 | 中(フィールド絞り込み・集計) | 高(全文・大規模集計) | 中(ラベル中心、全文は不得手) | 中(SQL、低頻度向け) |
| 監視・アラート相性 | 最良(metric filter→Alarm→SNS、AWS 統合・EventBridge・異常検知バンド) | 高(Alerting plugin、別系統運用) | 高(Grafana Alerting / Loki ruler、自前運用) | 不可(リアルタイム不可) |
| 構造化(JSON) | 自動パース | ネイティブ | ラベル + JSON | Glue スキーマ |
| 保持の柔軟性 | ロググループ単位で日数指定 | インデックス ILM | 設定で制御 | S3 ライフサイクル(年単位・最安) |
| 将来移行 | エクスポート/サブスクリプションで他基盤へ | 移送コスト | 移送コスト | 一次基盤には不向き |
| MVP 月額目安 | 数百円〜1 千円 | $26〜350+(¥4 千〜) | 固定費 + 運用 | 僅少(アーカイブ用途) |

要点: **人の参照のしやすさ**は OpenSearch / Loki が上だが、単独運用 + MVP の調査用途では CloudWatch(Console + Logs Insights)で足りる。**AI / プログラマティック参照**では、CloudWatch が AWS 公式の CloudWatch MCP サーバ / Log-Analyzer-with-MCP を備え、AWS SDK・IAM とも統一されるため、本プロジェクトの Claude Code / claude-automation 運用に最も馴染む(OpenSearch も MCP テンプレートはあるが Bedrock AgentCore で別途構築が要る)。**監視・アラート相性**も CloudWatch が AWS ネイティブで最良(ECS/RDS/ALB メトリクスと同一コンソールで統合監視でき、別系統の運用が増えない)。これらの軸でも CloudWatch の総合優位は崩れない。

### 選択肢 (a): Amazon CloudWatch Logs(採用案)

Fargate の `awslogs` / `awsfirelens` ドライバでロググループ(`/tasks/<env>/<service>`)へ送出。検索は Logs Insights、アラートは metric filter → CloudWatch Alarm → SNS。

- **コスト**: 取り込み $0.76/GB(Standard)、保存 $0.033/GB・月、Logs Insights クエリ $0.005/GB スキャン。**固定費ゼロ・従量のみ**。無料枠 5 GB 取り込み/月。低頻度クラス(Infrequent Access)は取り込みが約半額。
  - MVP 見積: 月 3〜5 GB 取り込み ≈ $2.3〜3.8(≈ ¥350〜600)+ 保存は短期保持で僅少。実質、月数百円〜1 千円。
- **検索性能 / 保持**: Logs Insights は構造化 JSON フィールドでフィルタ・集計可能。保持はロググループ単位で日数指定(14 / 30 日を変数化)。フルテキスト・大規模集計は OpenSearch に劣るが、単独運用の調査には十分。
- **参照のしやすさ(人 + AI)**: 人は Console の Live Tail(準リアルタイム追尾)+ Logs Insights で横断調査。**AI 参照は AWS 公式の CloudWatch MCP サーバ / Log-Analyzer-with-MCP** がログの検索・相関・根本原因分析を提供し、AWS SDK・IAM と統一されるため、Claude Code / claude-automation からのプログラマティック参照が容易。構造化 JSON が機械可読性を高める。リッチなダッシュボード(可視化)は OpenSearch/Grafana に劣るが MVP では足りる。
- **構造化ログ**: JSON を自動パースし `field` 抽出可能。
- **アラート連携**: metric filter + Alarm + SNS が標準。クロステナント違反パターンの検知に直結。**AWS 標準監視と同一エコシステム**(EventBridge 連携、ECS/RDS/ALB メトリクスと同一コンソール、Alarm の異常検知バンド)で相性が最良。
- **利点**:
  - **Fargate ネイティブ**。立てるインフラがゼロ(ロググループとロール/保持設定のみ)。outbound 経路(ADR-0003 の `logs`)に既に織り込み済み。
  - **固定費ゼロ**。MVP の低ログ量では月数百円で収まり、予算に収まる。
  - **運用負荷ゼロ**。マネージドでパッチ・スケール・可用性のお守り不要(単独運用に最適)。
  - **監視・アラートが AWS ネイティブ**で、別系統の監視基盤を持たずに済む。
  - **AI 参照が公式 MCP + SDK/IAM で完結**し、本プロジェクトの AI 運用と相性最良。
  - AU 系 control(生成・内容・タイムスタンプ・レビュー)の土台を満たす。
- **欠点 / リスク**:
  - 取り込み単価 $0.76/GB は東京リージョンでは割高で、**ログ量が増えると線形に効く**(後述の再評価トリガで監視)。
  - Logs Insights はリッチなダッシュボード・横断相関・全文検索で OpenSearch/Loki に劣る。
  - ログの改ざん防止(AU-9)はロググループの IAM 制御止まりで、WORM 相当の不変性は別途(S3 Object Lock 等)が必要 → 長期監査保管で考慮。

### 選択肢 (b): Amazon OpenSearch Service(managed cluster)

ログを OpenSearch に取り込み、Dashboards で全文検索・可視化。

- **コスト**: 最小 `t3.small.search` ≈ $26〜30/月/ノード(+ EBS)。可用性のため実運用は最低 2〜3 ノード。Serverless は最小 ≈ $350/月(idle でも)。**いずれも常時固定費**。
- **利点**: 強力な全文検索・集計・ダッシュボード。大規模ログ・横断相関に強い。**人の参照のしやすさは最良**(Dashboards でのドリルダウン探索)。**AI 参照**は REST / Query DSL が強力で、MCP テンプレート(Bedrock AgentCore へデプロイ)も用意されるが別途構築が要る。**アラートも Alerting plugin(monitor/trigger)で柔軟**(通知は SNS/Slack/webhook)だが AWS 標準監視とは別系統の設定・運用になる。
- **欠点 / リスク**:
  - **固定費が MVP 月予算(数千円)を単独で超える**。idle でも課金。
  - クラスタ運用(バージョン・シャード設計・スケール)の保守負荷が単独運用に重い。
  - MVP のログ量・検索要件に対して明確なオーバースペック。
  - 取り込みパイプライン(Fluent Bit / Firehose)を別途構築。

### 選択肢 (c): Grafana Loki(self-hosted on ECS Fargate, S3 backend)

Loki を ECS 上で常時稼働、ストレージは S3、収集は FireLens / Fluent Bit、可視化は Grafana。

- **コスト**: ストレージは S3 で安価(ラベルのみインデックス)。ただし **Loki / Grafana の常時稼働 Fargate タスク**(小構成でも月 $10〜15+)+ 各タスクへの Fluent Bit サイドカー + 設定共有用 EFS。
- **利点**: ストレージ単価が安く、ログ量が大きい将来は CloudWatch より総額で有利になりうる。OSS で柔軟。**人の参照は Grafana の Explore / LogQL** でラベルベース探索が快適(ただしフルテキストは不得手)。**AI 参照は HTTP / LogQL API 経由**(MCP は自前 endpoint の運用が前提)。**アラートは Grafana Alerting / Loki ruler(LogQL)**で柔軟だが、いずれも自前運用が前提。
- **欠点 / リスク**:
  - **構築・運用の負荷が最大**(コンテナ運用・アップグレード・クエリ層スケール・サイドカー全タスク配備・EFS 管理)。単独運用 + MVP では割に合わない。
  - 常時稼働ぶんの固定費が MVP の低ログ量では CloudWatch の従量を上回る。
  - 可用性・バックアップを自前で担保する必要。

### 選択肢 (d): S3 + Athena(長期保管・監査アーカイブ層)

ログを S3 に出力(Firehose や CloudWatch Logs エクスポート経由)、検索は Athena(SQL、$5/TB スキャン)。

- **コスト**: S3 保存 ≈ $0.025/GB・月(東京、Standard。さらに安い階層あり)、Athena $5/TB スキャン。**書き込み主体・低頻度クエリのアーカイブに最安**。
- **利点**: 長期保管が極めて安い。S3 Object Lock で WORM(改ざん防止、AU-9)を実現可能。**AI 参照は Athena SQL API**(バッチ分析向き)。
- **欠点 / リスク**:
  - **一次のリアルタイム調査基盤には向かない**(クエリのインタラクティブ性が低い、取り込み即時性なし)。人・AI とも参照のしやすさ(対話性)は最も低い。
  - **リアルタイム監視・アラートには使えない**(バッチクエリ前提、異常の即時発砲ができない)。
  - 監査保持要件が **現状 1 か月**のため、MVP では長期アーカイブの必要性自体が薄い。
- **位置づけ**: 一次ログ基盤の代替ではなく、**監査ログの長期・安価保管層の指定経路**として評価。

---

## 3. 決定(Decision)

**採用**: 選択肢 (a) — **Amazon CloudWatch Logs を MVP の一次ログ基盤として正式採用する**(暫定ではなく選定確定)。

具体構成:

1. **収集**: Fargate の log driver でロググループへ送出。tasks-webapi / Keycloak は **構造化(JSON)ログ**(logback JSON エンコーダ等)で出力し、Logs Insights のフィールド抽出を効かせる。ALB アクセスログは S3(共有 platform、正本)に出力し、運用者参照のため Lambda で CloudWatch Logs に転送する(single pane、後述「収集対象ログ」参照)。
2. **ロググループ命名**: `/tasks/<env>/<service>`(例 `/tasks/dev/webapi`、`/tasks/dev/keycloak`)。ADR-0004 の `tasks` 専用・`/tasks/*` 名前空間に揃える。
3. **保持期間**: アプリ/アクセス = 14 日、監査(セキュリティ証跡) = 30 日。**環境ごとに可変**(Terraform 変数 `log_retention_days` をログ種別 × 環境で設定)。retention_in_days は AWS が許容する離散値(0/1/3/5/7/14/30/60/90/...)のみ取れるため、「約 10 日」の意図は最近接の 14 に丸める。
4. **検索 / 参照(人 + AI)**: 人は CloudWatch Logs Insights + Console Live Tail。**AI 参照は AWS 公式 CloudWatch MCP サーバ / Log-Analyzer-with-MCP を介したプログラマティック参照**を想定(AWS SDK / IAM 統一、Claude Code / claude-automation から利用)。
5. **アラート**: metric filter → CloudWatch Alarm → SNS。最低限、ERROR 急増 と **クロステナント違反検知**(D-4 #438)のパターンをアラート化。AWS 標準監視(ECS/RDS/ALB メトリクス)と同一基盤で統合する。
6. **監査ログの長期・安価保管**: **指定経路は S3 エクスポート + Athena(+ 必要なら S3 Object Lock で WORM)**。ただし**現状の監査保持要件は 1 か月のため MVP scope 外**とし、保持要件が年単位に伸びる時点(または NIST AU-11 の確定時、S4Infra-2)で実装を起票する。

OpenSearch(b)・Loki(c)は **MVP では不採用**(固定費・運用負荷が MVP の量・予算・単独運用に見合わない)。下記の再評価トリガに該当した時点で改めて ADR を起こす。

### 収集対象ログ(MVP 初版カタログ)

MVP で収集するログを以下に確定する(実装は S3Infra-3)。所有は ADR-0004 の仕分けに従い tasks 専用と共有 platform に分ける。**platform 所有ログの正本は platform 側設計**だが、全体像把握のため本 ADR にも参照として記載する。

**設計原則(single pane of glass)**: 運用者が日常的に見るログは **CloudWatch Logs に集約**する。CWL に直接出せるもの(CloudFront standard logging v2 / RDS / ECS アプリ)は直接送り、直接出せないもの(ALB は S3 のみ)は **S3 → S3 イベント駆動 Lambda で CWL へ転送**する。S3 は正本 / アーカイブとして併存させ、運用者の一次参照先は CWL に一本化する(Athena は長期アーカイブの随時参照に限定)。

#### tasks スコープ(本 ADR / S3Infra-3 が所有)

| ソース | ログ | MVP 方針 | 備考 |
|---|---|---|---|
| webapi | アプリログ(JSON) | 収集 | logback JSON → awslogs。`/tasks/<env>/webapi` |
| webapi | アクセスログ(HTTP) | 収集 | アプリ層で tenantId/userId/requestId 付与(MDC、コーディング規約 §7)。ALB アクセスログとは別物、越境・監査調査に有用 |
| Keycloak | サーバログ | 収集 | stdout(jboss-logging)。`/tasks/<env>/keycloak` |
| Keycloak | user events(login 等) | 収集(ログ出力) | `org.keycloak.events` を出力。DB 保存は MVP off |
| Keycloak | admin events | 収集(ログ出力) | Admin API 経由の変更追跡。DB 保存は MVP off |
| RDS MySQL | error log | 収集 | 既定 on。CloudWatch へエクスポート |
| RDS MySQL | slow query log | 収集 | 性能調査。閾値設定 |
| RDS MySQL | audit log | MVP off → S4Infra-2 で要否確定 | MariaDB Audit Plugin + option group が必要で重い。業務監査は app 層(`audit_logs`、ADR-0013)+ クロステナント検知(D-4 #438)でカバー済 |
| RDS MySQL | general query log | off | 全 SQL で超冗長・高コスト。デバッグ時のみ一時的に on |
| CloudFront(フロント配信) | アクセスログ | 収集 | standard logging v2 で CloudWatch Logs へ直接出力(Lambda 不要)。フロント到達性・障害切り分け |
| S3(フロント配信バケット) | アクセスログ | off | CloudFront + OAC でバケット非公開・end-user 不可視のため CloudFront ログと冗長。直接アクセス試行の検知が要れば CloudTrail S3 data events を後日 |
| フロントエンド(ブラウザ) | クライアント側ログ / JS エラー | 見送り(post-MVP) | CloudFront 配信ログでは捕捉不可。採用時も **CloudWatch Logs に集約できる方式に限定**(例: DIY endpoint → CWL)。Sentry 等の外部 SaaS は single pane 原則で不採用、CloudWatch RUM は CWL へ集約可能な場合のみ |

#### platform スコープ(ADR-0004 で共有 platform 所有、本 ADR は参照)

各ログを内容・価値・コストで評価し MVP 方針を示す(正本は platform 側設計)。

| ログ | 内容 | 価値 | コスト | MVP 方針 |
|---|---|---|---|---|
| ALB アクセスログ | L7 リクエスト(送信元 IP・レイテンシ・ステータス) | 高(アクセス全体像・調査) | 低〜中(S3 保存 + CWL 取り込み従量) | **収集**。ALB は **S3 のみ出力**のため、S3(正本)→ **S3 イベント駆動 Lambda で CloudWatch Logs へ転送**し運用者の単一画面に集約。CWL retention は短め、S3 は長期保管 |
| CloudTrail(管理イベント) | AWS API 操作の監査(誰が何を変更) | 高(セキュリティ / NIST AU) | ほぼ無料(管理イベント 1 本目) | **収集**(アカウント / platform で有効化) |
| SES 送信イベント | bounce / complaint / delivery | 中〜高(Keycloak メール到達性・SES 評価維持) | 低(低ボリューム) | **収集**(最低 bounce / complaint、SNS / EventBridge 経由) |
| VPC Flow Logs | ネットワークフロー(許可 / 拒否) | 中(侵入検知・フォレンジック) | 中(全量は嵩む) | **dev: off / stg・prd: on**(dev はコスト優先、prd で SI-4 / SC-7 の証跡確保) |
| WAF アクセスログ | Web ACL の検査結果(allow / block・ルール一致・送信元 IP) | 中〜高(L7 攻撃検知・false positive 調査) | 低(CWL 取り込み従量、stg/prd のみ) | **dev: off / stg・prd: on**(infra ADR-0006、ALB の REGIONAL Web ACL。CloudWatch Logs へ直接出力可。app ログとの相関は時間 + 送信元 IP ベース) |

### 任意(コスト感度の高い)ログの個別評価

コスト感度が高く採否が分かれる任意ログ(本カタログで off / 条件付きとした項目)を、いずれも**検討対象として**内容・価値・コストで評価し MVP 判断を示す(判断は要件次第で反転可。すべて S4Infra-2 の NIST 整備で再評価対象)。

| ログ | 内容 | 価値 | コスト | MVP 判断 | 根拠 |
|---|---|---|---|---|---|
| RDS 監査ログ(audit) | DB レベルの接続・クエリ・権限変更の監査 | 高(DB 直アクセス監査・NIST AU・越境の最終証跡) | 中〜高(custom option group + 取り込み増) | 推奨 off → S4Infra-2 | 業務監査は app 層(`audit_logs` ADR-0013 + クロステナント検知 D-4 #438)で大半カバー。DB レベル監査が要る要件が立てば ON |
| VPC Flow Logs | ENI 単位の通信メタ(許可 / 拒否、5-tuple) | 中〜高(侵入検知・フォレンジック・NIST SC/SI) | 中(全量は嵩む、REJECT / サンプリングで圧縮可) | **dev: off / stg・prd: on**(platform 所有) | dev はコスト優先で off、stg・prd で SI-4 / SC-7 の証跡を確保。全量 or REJECT 中心かは prd 構築時に確定 |
| RDS general query log | 全 SQL + 接続 / 切断 | 中(網羅的だが構造化監査は audit の役割) | 高(極めて冗長、取り込み費が跳ねる) | off(デバッグ時のみ一時 on) | コスト対効果が MVP では悪い |
| Keycloak events DB 保存 | user / admin events を Keycloak DB に永続化(Console 閲覧) | 中(Console 閲覧性。保持は CloudWatch 出力でも代替) | 低〜中(DB 容量・書き込み・別 retention) | off(ログ出力のみ) | CloudWatch 出力で検索・保持・アラートを一元化でき DB 二重持ちは不要。Console 閲覧要件が立てば ON |

### 収集判断と NIST 800-53 の関係

収集の on/off が NIST 800-53(Rev 5)準拠に**効く**もの(=判断を誤ると control 充足にギャップが出る)を明示する(本格マッピングは S4Infra-2)。

| 収集判断 | 関連 control | 準拠への効き方 |
|---|---|---|
| Keycloak user / admin events を収集 | AU-2 / AU-12(イベント生成)、AC-7(ログイン失敗)、IA 系、CM(管理変更) | **効く**。認証・identity・管理操作は AU-2 の必須イベント。捕捉しないと認証 / 権限変更の監査ギャップ。※ ログ出力か DB 保存かは準拠に**無関係**(どちらでも保持できればよい) |
| RDS audit log | AU-2 / AU-12(DB レイヤのデータアクセス監査) | **条件付きで効く**。app をバイパスする**直接 DB アクセス**(運用者の手動接続等)の監査は RDS audit log でしか取れない。app 層監査(`audit_logs` / D-4 #438)は業務経路のみ。直接アクセスを運用上禁止 / 制限できれば off でも穴は小さい |
| VPC Flow Logs | SI-4(システム監視)、SC-7(境界防御)、AC-4(情報フロー) | **効く**。ネットワーク監視・境界トラフィックの証跡。**dev は off(コスト優先で許容)、stg・prd は on で充足**。off だと SI-4 / SC-7 の証跡が欠落 |
| CloudTrail(管理イベント) | AU-2 / AU-12(管理プレーン監査) | **強く効く**。AWS 上の「誰が何を変更したか」。AWS ベースラインでは事実上必須、off は不可 |
| ALB アクセスログ | AU-2(システムアクセス記録) | 補助的。app 層アクセスログと重複するが L7 全体像の証跡 |
| RDS general query log | (なし) | **効かない**。デバッグ用途で監査要件ではない(構造化監査は audit log の役割)。off で準拠に影響なし |

横断事項:

- **保持期間は AU-11(監査記録の保持)に直結**。本 ADR の監査 30 日は MVP 値で、moderate 相当の一般パラメータ(例: オンライン 90 日 + アーカイブ 1 年)には不足しうる。要件確定時に保持延長 + 長期保管層(S3 + Athena / S3 Object Lock = AU-9)で対応する(S4Infra-2)。
- **AU-9(監査情報の保護)**: CloudWatch の IAM 制御に加え、改ざん防止が要る監査ログは S3 Object Lock(WORM)で補完する(MVP scope 外、S4Infra-2)。
- **「prd で完全準拠」は収集だけでは成立しない**。VPC Flow Logs を prd で on にすると SI-4 / SC-7 の*ログ証跡*の穴は塞がるが、完全準拠には他に (1) AU-11 の保持延長(監査 30 日→要件水準)、(2) AU-9 の WORM(S3 Object Lock)、(3) RDS audit log による直接 DB アクセスの AU-2 充足(または直接アクセスの運用禁止)、(4) AU-6 のレビュー運用、さらに**ログ以外の多数の control(AC / CM / IR / RA 等)**が必要。これらは S4Infra-2 + 各 Sprint で対応する。本 ADR の収集設計は**準拠の前提(必要条件)を満たすが十分条件ではない**。

> 上記は MVP 初版。NIST 800-53 の本格整備(S4Infra-2)で AU / SI / SC 系 control に照らし、RDS audit log・VPC Flow Logs の全量化・S3 Object Lock 長期保管を再判断する。

### 再評価トリガ(MVP 後に (b)/(c)/(d) を検討する条件)

- 月間ログ取り込み量が増え、**CloudWatch の従量が OpenSearch/Loki の固定費を上回る**水準に達したとき。
- リッチなダッシュボード・全文検索・複数サービス横断相関が**運用上必須**になったとき。
- 共有 platform で**複数プロジェクトのログ集約**が要るようになったとき。
- 監査保持要件が**年単位に伸び**、長期・安価保管(d / S3 Object Lock)が必要になったとき(S4Infra-2 と連携)。

---

## 4. 理由(Rationale)

1. **コスト(固定費 vs 従量)**: MVP のログ量(月 3〜5 GB)では CloudWatch の従量は月数百円〜1 千円で予算内。対して OpenSearch は最小でも月 $26〜30+(idle 課金)、Loki も常時稼働ぶんの固定費があり、**いずれも MVP のログ予算(数千円)を本来用途の前に固定費で消費する**。保持が短期(14/30 日)なため保存費も僅少で、CloudWatch の従量モデルが最も効率的。
2. **運用負荷(単独運用)**: CloudWatch は完全マネージドで運用ゼロ。OpenSearch のクラスタ運用・Loki の自前運用は単独運用に対して保守負荷が重く、本質でない作業を増やす。
3. **Fargate ネイティブ / 既存設計との整合**: log driver で送るだけで完結し、立てるインフラがない。outbound 経路(ADR-0003 の `logs`)・専用/共有 仕分け(ADR-0004 の `/tasks/*`)・JST(ADR-0009)と既に整合。
4. **参照のしやすさ(人 + AI)・監視/アラート相性**: 人の調査は Console(Live Tail)+ Logs Insights で足りる(リッチなダッシュボードは OpenSearch/Grafana に譲るが現時点でオーバースペック)。**AI による参照**(障害解析エージェント・claude-automation 等)も、AWS 公式 CloudWatch MCP / Log-Analyzer-with-MCP + AWS SDK/IAM 統一により本プロジェクトの Claude Code 運用と最も相性がよい。**監視・アラートは AWS ネイティブが最良**で、metric filter → Alarm → SNS、EventBridge 連携、異常検知バンドが使え、ECS/RDS/ALB のメトリクスと同一コンソールで統合監視できる。OpenSearch/Loki も柔軟なアラート・AI 連携を持つが、別系統の設定・運用が増える。
5. **将来移行容易性**: CloudWatch Logs は **S3 エクスポート / サブスクリプションフィルタ(Firehose)** で他基盤(OpenSearch / Loki / S3+Athena)へ流せる。アプリ側を JSON 構造化しておけば、後の乗せ換え時もスキーマがそのまま活きる。**ロックインは小さく、後戻りコストは低い**。
6. **NIST AU 系の土台**: 生成(AU-12)・内容(AU-3)・タイムスタンプ(AU-8、JST)・レビュー(AU-6、Logs Insights)・保持(AU-11、retention)を満たす。改ざん防止(AU-9)の強化は長期保管層(S3 Object Lock)で補完する方針を明記済(本格整備は S4Infra-2)。

捨てた利点: OpenSearch のリッチな検索・可視化、Loki の安価ストレージは魅力だが、MVP の量・予算・単独運用ではいずれも固定費/運用負荷が上回る。再評価トリガで将来判断する。

---

## 5. 影響(Consequences)

### 良い影響(Positive)

- 立てるインフラがほぼなく(ロググループ + 保持 + ロール + metric filter)、S3Infra-3 の実装が軽量になる。
- 固定費ゼロ・従量のみで、MVP のログ予算に収まる。
- マネージドで運用負荷ゼロ。単独運用の本質作業を圧迫しない。
- 監視・アラートが AWS 標準監視と同一基盤で完結し、別系統を持たずに済む。
- AI による参照が公式 MCP + SDK/IAM で完結し、Claude Code / claude-automation 運用と噛み合う。
- JSON 構造化を前提にするため、将来の基盤乗せ換え時もスキーマが活きる。

### 悪い影響・制約(Negative)

- 取り込み単価 $0.76/GB(東京)は割高で、**ログ量増加に線形**。冗長ログの抑制(ログレベル運用・サンプリング)が必要。再評価トリガで監視する。
- Logs Insights はリッチなダッシュボード・全文検索・横断相関で OpenSearch/Loki に劣る(MVP では許容)。
- ログ改ざん防止が IAM 制御止まり。年単位の監査保管・WORM が要る段階で S3 Object Lock を追加する(MVP scope 外)。

### 既存ドキュメント・規約への波及

- **S3Infra-3(Phase 1 Sprint 3 Infra、ログ基盤実装)**: 本 ADR を参照し、CloudWatch Logs のロググループ/保持/metric filter/Alarm を Terraform で実装する。
- **S4Infra-2(Phase 1 Sprint 4 Infra、NIST control 整合)**: AU 系 control の本格マッピングと、必要なら監査ログの S3 Object Lock 長期保管をここで確定する。
- **`infrastructure-plan.md` v5 §7**: P-LOG-A の「方針確定」を本 ADR で達成。§6 リスクシナリオの「暫定採用」表現は本 ADR の正式採用で解消(軽微な doc 同期、別途実施可)。
- **ADR-0004**: ログは `tasks` 専用・`/tasks/*` 名前空間。共有 platform のログ基盤は MVP では不要(整合)。

### 本 ADR の非対象(別途検討)

本 ADR はログ基盤の選定・収集対象・保持・single pane・NIST 整合を範囲とし、以下は対象外:

- **各ログの詳細フォーマット / JSON フィールド契約**: アプリ側の基本(MDC `tenantId` / `userId` / `requestId`、SLF4J プレースホルダ、PII マスク、バッチ `batchId`)は **コーディング規約 §7 / 設計規約 §4.2 で既定**。実際に出力する JSON スキーマ(field 一覧・型)は S3Infra-3 実装時に確定する。
- **ログ間の紐づけ(相関キー)設計**: app 内は `requestId` を MDC で保持するが、**ALB / CloudFront / RDS の各ログと app ログを横断で相関させる方式**(例: ALB `X-Amzn-Trace-Id` ↔ app `requestId`、CloudFront request id の伝播、Keycloak events ↔ app)は未定。S3Infra-3 + 必要なら 設計規約 §4.2 / コーディング規約 §7 の拡張で扱う。
- **`event` フィールドのタクソノミ**(例: `CROSS_TENANT_VIOLATION`)、ログレベル / サンプリング方針の体系化。

これらは S3Infra-3(ログ基盤実装)着手時、または規約改訂として **別途 Issue で起票**する。

---

## 6. 実装メモ(Implementation Notes)

### ロググループと保持(環境ごと可変)

| ログ種別 | ロググループ例 | 保持(dev) | 保持変数 |
|---|---|---|---|
| webapi アプリ | `/tasks/dev/webapi` | 14 日 | `var.log_retention_days.app` |
| Keycloak | `/tasks/dev/keycloak` | 14 日 | `var.log_retention_days.app` |
| 監査(セキュリティ証跡) | `/tasks/dev/audit` | 30 日 | `var.log_retention_days.audit` |
| ALB アクセスログ | S3(正本)→ `/tasks/<env>/alb`(Lambda 転送) | 14 日 | `var.log_retention_days.app` |
| CloudFront アクセスログ | `/tasks/<env>/cloudfront`(v2 直接) | 14 日 | `var.log_retention_days.app` |

### Terraform 雛形(例)

```hcl
variable "log_retention_days" {
  type = object({
    app   = number
    audit = number
  })
  default = { app = 14, audit = 30 } # 環境ごとに上書き(retention_in_days 許容値に丸め)
}

resource "aws_cloudwatch_log_group" "webapi" {
  name              = "/tasks/${var.env}/webapi"
  retention_in_days = var.log_retention_days.app
}

resource "aws_cloudwatch_log_group" "audit" {
  name              = "/tasks/${var.env}/audit"
  retention_in_days = var.log_retention_days.audit
}

# 例: クロステナント違反検知をアラート化
resource "aws_cloudwatch_log_metric_filter" "cross_tenant" {
  name           = "cross-tenant-violation"
  log_group_name = aws_cloudwatch_log_group.audit.name
  pattern        = "{ $.event = \"CROSS_TENANT_VIOLATION\" }"
  metric_transformation {
    name      = "CrossTenantViolation"
    namespace = "tasks/${var.env}"
    value     = "1"
  }
}
```

### ログ送出(アプリ側)

- tasks-webapi: logback の JSON エンコーダで構造化出力 → Fargate `awslogs`(または FireLens/Fluent Bit)で送出。タイムスタンプは JST(ADR-0009)。
- フィールド例: `timestamp` / `level` / `tenantId` / `userId` / `requestId` / `event` / `message`(MDC キーは コーディング規約 §7 / 設計規約 §4.2 に準拠)。Logs Insights で `tenantId` / `event` による絞り込みを効かせる。構造化することで AI(MCP)による解析・相関も容易になる。

### AI / プログラマティック参照(MCP)

- 障害解析・調査時、Claude Code / claude-automation から **AWS 公式 CloudWatch MCP サーバ**(`awslabs.cloudwatch-mcp-server`)/ **Log-Analyzer-with-MCP** を介してロググループの検索・相関・根本原因分析を行える。
- 認証は AWS IAM(読み取り専用ロール)で統一。別系統の認証基盤を持たずに済む。
- 構造化 JSON 前提なので、AI が `tenantId` / `event` 等のフィールドで絞り込み・集計しやすい。

### NIST 800-53 AU 系の対応(概要、本格整備は S4Infra-2)

| Control | 概要 | CloudWatch Logs での対応 |
|---|---|---|
| AU-2 / AU-12 | 監査イベントの選定・生成 | アプリ/監査ロググループへ構造化出力 |
| AU-3 | 監査記録の内容 | JSON フィールド(who/what/when/tenant) |
| AU-6 | レビュー・分析 | Logs Insights クエリ(人)+ MCP(AI) |
| AU-8 | タイムスタンプ | JST 統一(ADR-0009) |
| AU-9 | 監査情報の保護 | ロググループ IAM 制御(WORM は S3 Object Lock で将来補完) |
| AU-11 | 保持 | retention_in_days(14/30 日、環境可変) |
| SI-4 | 監視 | metric filter + Alarm + SNS |

### 監査ログ長期保管(将来、MVP scope 外)

保持要件が年単位に伸びた時点で、CloudWatch Logs → S3 エクスポート(または Firehose サブスクリプション)→ Athena 検索、+ S3 Object Lock で WORM(AU-9 強化)。S4Infra-2 で要否確定。

### 再評価の判定基準(再掲)

- 月間取り込み量で CloudWatch 従量 > OpenSearch/Loki 固定費 になったか
- リッチなダッシュボード/全文検索/横断相関が必須化したか
- 共有 platform での複数プロジェクトログ集約が要るか
- 監査保持が年単位化したか(→ d / S3 Object Lock)

---

## 7. 参考リンク(References)

- `docs/architecture/infrastructure-plan.md` v5 §7(P-LOG-A)/ §6 / §3
- `docs/specs/開発計画書.md` v1.3 §4.3.2(並行ストリーム)
- `infra/docs/adr/0003-private-subnet-outbound.md`(`logs` outbound 経路)
- `infra/docs/adr/0004-platform-project-infra-separation.md`(専用/共有 仕分け)
- `docs/adr/0009-jst-unified-timezone-policy.md`(タイムスタンプ JST)
- `docs/adr/0013-audit-log-field-diff-strategy.md`(業務監査ログの SoT)
- [GitHub Issue #326](https://github.com/win2cot/tasks-webapi/issues/326): 本 ADR の起票 Issue(P-LOG-A)
- [GitHub Issue #206](https://github.com/win2cot/tasks-webapi/issues/206): 親 tracker Issue
- [Amazon CloudWatch Pricing](https://aws.amazon.com/cloudwatch/pricing/)
- [Amazon CloudWatch Logs billing and cost (AWS docs)](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/LogsBillingDetails.html)
- [Amazon OpenSearch Service Pricing](https://aws.amazon.com/opensearch-service/pricing/)
- [Amazon Athena Pricing](https://aws.amazon.com/athena/pricing/)
- [Amazon S3 Pricing](https://aws.amazon.com/s3/pricing/)
- [Deploy Grafana Loki on AWS (Grafana docs)](https://grafana.com/docs/loki/latest/setup/install/helm/deployment-guides/aws/)
- [AWS Labs MCP servers (GitHub)](https://github.com/awslabs/mcp)
- [CloudWatch MCP Server (AWS Labs)](https://awslabs.github.io/mcp/servers/cloudwatch-mcp-server)
- [Log Analyzer with MCP (AWS Labs)](https://github.com/awslabs/Log-Analyzer-with-MCP)
- [OpenSearch MCP server integration templates (AWS docs)](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/cfn-template-mcp-server.html)
