# ADR-0033: RDS IAM 認証のアプリ側配線方式 — AWS SDK v2 RdsUtilities 採用と DataSource 構成

- **Status**: Accepted
- **Date**: 2026-06-19
- **Deciders**: 開発チーム(win2cot)
- **Tags**: security, persistence, infrastructure, native-image, rds, iam

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

infra 側は RDS IAM 認証を前提に構築済みである(#448 RDS / #479 Task Role `rds-db:connect` / #478 native イメージ、署名ホスト整合は infra [ADR-0008](../../infra/docs/adr/0008-rds-direct-endpoint.md))。一方アプリ側 DataSource は素の `com.mysql.cj.jdbc.Driver` + username/password のままで、infra の受入条件「パスワード未使用で接続成功」を満たす受け皿が境界に落ちていた。

CSP 検証中に「IAM 認証が未配線で DB 接続できない」事象を発見し、議論を経ず動作優先で暫定対応した(`feat(infra): RDS IAM 認証を Spring Boot に実装` 31cfdc6 ほか)。この暫定対応で **新規ライブラリ `software.amazon.awssdk:rds`(AWS SDK for Java v2)が ADR なしで merge** されており、コーディング規約「新規ライブラリ導入は同 PR で ADR」に未準拠の状態にある。本 ADR でこれを後追いで正規化し、認証方式・DataSource 構成・環境差の切替・native image 対応・トークン期限対応を確定する。

重要な制約:

- 本プロジェクトの DB は **Amazon RDS for MySQL 単一インスタンス**(Aurora ではない)。
- 配信は **GraalVM Native Image**([ADR-0008](0008-graalvm-native-image.md) / [ADR-0028](0028-build-once-promote-digest.md))がハード制約。
- 環境設定は **Spring Profile 不使用**・env 変数のみで切り替える方針([設計規約](../specs/設計規約.md) §5.5)。

## 2. 検討した選択肢

### 選択肢 A: AWS SDK v2 RdsUtilities でトークン生成 + カスタム DataSource(暫定対応の正式化)

- 概要: `RdsUtilities.generateAuthenticationToken` で IAM トークン(SigV4)を生成し、HikariCP の物理コネクション取得ごとに新トークンで接続する薄いカスタム DataSource を持つ。`@ConditionalOnProperty(rds.iam-auth.enabled=true)` で標準 DataSource を置換。
- 利点: `RdsUtilities` は **AWS 公式 API**(AWS ドキュメント "Generating an authentication token" のサンプルがこの方式)。トークン生成のみを使うため native image が軽量で、CI native スモークテストが green。自前コードは小さくテスト3層で固められる。
- 欠点: トークンのライフサイクル(15分失効前の再認証)を `maxLifetime` 設定との暗黙の結合で担保しており、設定とコードの対応が見えにくい。アプリが AWS SDK 依存を保持する。
- リスク・未知数: なし(稼働実績あり)。

### 選択肢 B: AWS Advanced JDBC Wrapper の IAM Authentication Plugin

- 概要: `mysql-connector-j` をラップする公式ドライバに乗せ、IAM 認証をドライバが自動でハンドリングする。自前 DataSource は不要になる。
- 利点: トークンのライフサイクル(生成・キャッシュ・更新)をドライバが吸収。フェイルオーバー / トポロジ認識 / 読み書き分割などの高機能を将来得られる。
- 欠点: それらの目玉機能は **Amazon Aurora クラスタ向け**で、RDS 単一インスタンスでは無価値。依存が重く(RDS SDK 一式で約 22MB)native image が膨らむ。プラグインを**リフレクションで動的ロード**する設計で、**GraalVM native image の一級サポート・reachability metadata 提供が公式に確認できず**、native を通すための reflection hints を自前で抱える可能性が高い(= 削ったはずの「製品依存コード」が native 設定として別形で増える)。
- リスク・未知数: native image 適合性が未検証。本プロジェクトのハード制約に対して致命的になり得る。

### 選択肢 C: 暫定対応を無設計のまま放置 / Secrets Manager + password 認証へ後退

- 概要: 現状の暫定コードを文書化せず放置する、または IAM をやめて password を Parameter Store / Secrets Manager で配る。
- 利点: 追加作業が当面ゼロ。
- 欠点: ライブラリ追加が ADR なしのまま規約違反状態で残る。password 後退は infra(#448 / #479 / ADR-0008)の IAM 前提を無効化する大きな後戻りで、設計規約 §5.5「IAM 認証・password 不使用」「Secrets Manager 未採用」とも矛盾する。
- リスク・未知数: 設計の負債が固定化する。

## 3. 決定

**採用**: 選択肢 A — **AWS SDK v2 `RdsUtilities` によるトークン生成 + カスタム DataSource を正式方式とする。**

あわせて、暫定対応で残っていた脆弱・冗長な構成を以下の目標形に正す:

- **接続先の唯一の可変値は DB ホスト**である(全環境で port=3306・db=`tasks`・タイムゾーンパラメータは定数、SSL パラメータは認証モードに従属。local/CI=`useSSL=false`、IAM=`sslMode=REQUIRED`)。したがって **`DB_HOST`(必要なら `DB_PORT`)のみを env で注入**し、JDBC URL テンプレートは `application.yml` にハードコードする。
- これにより、暫定対応にあった **JDBC URL の自前パース(`extractHost` / `extractPort`)と `withSsl()` を全廃**する。host / port は同一の `DB_HOST` / `DB_PORT` から URL 組み立てとトークン生成の双方に使い、**同一情報の二重注入を排除**する(SSOT = `DB_HOST`)。
- 環境差は `RDS_IAM_AUTH_ENABLED` のみで切替(local/CI=password、stg/prd=IAM)、Profile 不使用方針と整合。
- トークン15分失効は HikariCP `maxLifetime=840000`(14分)で再接続を強制して担保。
- native image の AWS SDK reflection は GraalVM Reachability Metadata Repository に委ね、`processAot` で `RDS_IAM_AUTH_ENABLED=true` を焼き込み条件 bean を同梱。CI native スモークテストで AWS SDK のトークン生成到達を検証する。

## 4. 理由

1. **公式性と軽量性の両立**: `RdsUtilities` は AWS 公式の標準トークン生成 API。トークン生成だけを使うため native image が軽く、スモークテストが green の稼働実績がある。
2. **B の利点が本構成では効かない**: Advanced JDBC Wrapper の価値(failover・トポロジ・読み書き分割)は Aurora 向けで、RDS 単一インスタンスでは無価値。さらに native image サポートが未検証で、ハード制約に対するリスクが利得を上回る。「製品依存コードを減らす」目的に対しても、wrapper は AWS 依存を config + ドライバ層へ移すだけで消えず、native 設定の負担をむしろ増やしうる。
3. **二重注入の排除**: 可変部が実質 host のみと判明したため、`DB_HOST` 単一注入 + テンプレートのハードコードが最も筋が良い。脆い URL パースが不要になり、抱える独自コードが「トークン生成 → 接続」の不可避な最小核に収束する。
4. **既存方針との整合**: env-only 切替は Profile 不使用方針と、IAM・password 不使用は設計規約 §5.5 と整合する。
5. **捨てるもの**: トークンライフサイクルを `maxLifetime` の暗黙結合で担保する読みにくさは残す(コメントと本 ADR で補う)。将来 Aurora へ移行する判断が出た場合は、その時点で wrapper 移行を新規 ADR で再評価する。

## 5. 影響

### 良い影響(Positive)

- ライブラリ追加(`software.amazon.awssdk:rds`)が ADR で正規化され、規約違反状態が解消する。
- `DB_HOST` を SSOT とし、URL パース・`withSsl` が消え、独自コードと注入値の冗長が減る。
- 環境差が env のみで成立し、native image は現行の green を維持する。

### 悪い影響・制約(Negative)

- トークン失効とプール `maxLifetime` の暗黙の結合は残る(設定変更時に見落としやすい)。
- アプリが AWS SDK 依存を保持する(ただし IAM 認証を採る以上どの選択肢でも不可避)。
- `DB_HOST` 化は `application.yml` のほか **infra `ecs_service.tf`(`SPRING_DATASOURCE_URL` → `DB_HOST`)・`.env.local`・docker-compose・スモークテストに及ぶ横断リネーム**になる(commit 前に対象パターン全文 grep)。

### 既存ドキュメント・規約への波及

- [設計規約](../specs/設計規約.md) §5.5: 既存の「IAM 認証(K-A 案)・password 空文字」記述に、実装方式(SDK v2 `RdsUtilities` + カスタム DataSource + `DB_HOST` 注入)を追記し本 ADR を参照。**最終形(DB_HOST 化後)を反映するため、規約の追記は DB_HOST 化リファクタの PR に同梱する**(§6)。
- infra [ADR-0008](../../infra/docs/adr/0008-rds-direct-endpoint.md) / #479 と相互参照(下表)。

infra #479(Task Role)受入とアプリ側設定の対応:

| infra #479 / ADR-0008 | アプリ側の対応 |
|---|---|
| Task Role `rds-db:connect`(ARN scoped, dbuser `tasks_webapi`) | `DefaultCredentialsProvider`(ECS タスクロール)でトークン署名 |
| RDS 実エンドポイント直注入(署名ホスト整合) | `DB_HOST` に実エンドポイントを注入、トークン署名ホストと接続ホストが一致 |
| password 未使用で接続成功 | `RDS_IAM_AUTH_ENABLED=true` で `RdsIamDataSourceConfig` が password を読まずトークン認証 |
| TLS 必須 | テンプレートで `sslMode=REQUIRED`(IAM 経路) |

## 6. 実装メモ

- **本 ADR の採択(#660 クローズ)と DB_HOST 化リファクタは分離する。** #660 は ADR + #479 対応の doc 化で受入条件を満たしクローズする。`DB_HOST` 化(URL パース / `withSsl` 廃止 + infra/env/compose/スモークテストの横断リネーム + 設計規約 §5.5 同期)は area/backend + area/infra の実装 sub-issue として起票し、本 ADR を blocked-by とする。
- 暫定対応のうち env-only 切替・`maxLifetime=840000`・RMR / `processAot` の native 対応・テスト3層(単体 / Testcontainers / native スモーク)は**現行どおり維持**する。
- リファクタ後は `RdsIamDataSourceConfig` から `extractHost` / `extractPort` / `withSsl` が消え、`DB_HOST` / `DB_PORT` から URL 組み立てとトークン生成を直接行う最小構成になる。

## 7. 参考リンク

- infra [ADR-0008: RDS エンドポイント直接注入](../../infra/docs/adr/0008-rds-direct-endpoint.md)
- [ADR-0008: GraalVM Native Image 採用](0008-graalvm-native-image.md) / [ADR-0021: ランタイム JDK 25](0021-runtime-jdk-25.md) / [ADR-0028: build once / promote digest](0028-build-once-promote-digest.md)
- Issue #448(RDS)/ #479(Task Role rds-db:connect)/ #478(native イメージ)/ #660(本 ADR)
- [AWS: Generating an authentication token (RDS IAM)](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.Connecting.html)
- [aws/aws-advanced-jdbc-wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper)(選択肢 B、却下)
