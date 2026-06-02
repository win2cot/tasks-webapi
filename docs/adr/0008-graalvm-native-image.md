# ADR-0008: tasks-webapi の実行バイナリとして GraalVM Native Image を採用

- **Status**: Accepted
- **Date**: 2026-05-24
- **Deciders**: 開発チーム(win2cot)
- **Tags**: runtime, infrastructure, performance, build

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: Fat JAR + JIT(現状維持)](#選択肢-a-fat-jar--jit現状維持)
  - [選択肢 B: GraalVM Native Image](#選択肢-b-graalvm-native-image)
  - [選択肢 C: Layered JAR](#選択肢-c-layered-jar)
  - [選択肢 D: CRaC(Coordinated Restore at Checkpoint)](#選択肢-d-craccoordinated-restore-at-checkpoint)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
  - [依存ライブラリの Native 対応状況](#依存ライブラリの-native-対応状況)
  - [Reflection ヒント管理戦略](#reflection-ヒント管理戦略)
  - [CI/CD への組み込み方針](#cicd-への組み込み方針)
  - [ローカル開発戦略](#ローカル開発戦略)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

2026-05-23 に策定した infrastructure-plan v5 で、tasks-webapi を **ECS Fargate** 上で動作させるアーキテクチャを確定した。Fargate ではコンテナ起動のたびに JVM の初期化・クラスロード・JIT ウォームアップが発生し、cold start が問題になる。また Fargate task size(CPU / メモリ)はコスト直結であり、JVM ヒープ用のメモリオーバープロビジョニングを避けたい。

infrastructure-plan v5 §2 #5 では **Spring プロファイル不使用**(`@Profile` 禁止)を規定した。これは GraalVM Native Image への移行を前提とした設計判断であり(Native Image はビルド時に profile 分岐を解決できないため)、本 ADR でその根拠を集約する。

本決定が必要になった背景:

- **ECS Fargate cold start**: コンテナ再起動・スケールアウト時の JVM 初期化に数秒〜十数秒かかる
- **メモリコスト**: JVM + ヒープ確保のため Fargate task size を大きくせざるを得ない
- **Container image サイズ**: JVM ランタイムを含む Fat JAR イメージはサイズが大きい
- **セキュリティ攻撃面**: JVM ランタイム同梱によりアタックサーフェスが広い
- **Spring Boot 4 AOT サポート**: Spring Boot 4 は Native Image ビルドを公式サポートしており、AOT(Ahead-of-Time)コンパイル機能が安定している

## 2. 検討した選択肢

### 選択肢 A: Fat JAR + JIT(現状維持)

- **概要**: `./gradlew :webapi:bootJar` で生成した Fat JAR をコンテナに同梱し、`java -jar` で起動する。JVM はランタイムに含まれる。
- **利点**:
  - ビルドシンプル(現状のまま)
  - Reflection・動的クラスロード・`@Profile` が使用可能
  - ライブラリ互換性のリスクなし
  - CI ビルド時間が短い(1〜2 分程度)
- **欠点**:
  - Cold start が長い(JVM 初期化 + JIT ウォームアップで 5〜15 秒)
  - メモリ消費が大きい(JVM + ヒープ 256〜512 MB 以上)
  - Container image サイズが大きい(JDK / JRE 含む 200〜400 MB)
- **リスク・未知数**: Fargate のオートスケール時に UX 影響が出る可能性

### 選択肢 B: GraalVM Native Image

- **概要**: GraalVM `native-image` コンパイラで AOT コンパイルし、JVM を含まないネイティブバイナリをコンテナに同梱する。Spring Boot 4 の GraalVM Native support (`org.springframework.boot:spring-boot-starter-aot`) を使用。
- **利点**:
  - **起動時間**: 数十〜数百ミリ秒(JVM 版比 10〜50 倍高速)
  - **メモリ**: ヒープを小さくできる(64〜128 MB が現実的)
  - **Container image サイズ**: slim ベースイメージ + バイナリのみで 50〜100 MB
  - **セキュリティ**: JVM ランタイムを含まないためアタックサーフェスが小さい
  - **Spring プロファイル不使用との整合**: `@Profile` が AOT コンパイルで問題を起こすリスクを排除
- **欠点**:
  - **ビルド時間**: Native build は GHA runner で 5〜15 分(Fat JAR の 5〜10 倍)
  - **Reflection 制約**: 動的に解決されるクラスは build-time hint (`@RegisterReflectionForBinding` / `META-INF/native-image/`) が必要
  - **動的クラスロード禁止**: `Class.forName(...)` / `java.lang.reflect.*` の無制限使用不可
  - **一部ライブラリ非対応**: Native Image に未対応のライブラリがあれば差し替えが必要
- **リスク・未知数**: Spring Boot 4 AOT での Hibernate 6 / Flyway 10 / Keycloak adapter の安定性(既に公式 Native サポートあり)

### 選択肢 C: Layered JAR

- **概要**: Spring Boot のレイヤード JAR 機能でコンテナイメージを分割し、依存関係レイヤーをキャッシュ。JVM は引き続き使用。
- **利点**: ビルド変更なし、CI キャッシュ改善でイメージ push 時間短縮
- **欠点**: Cold start・メモリ・セキュリティ面は Fat JAR と同等。`@Profile` 制約は解決されない。
- **リスク・未知数**: 根本的な cold start 問題を解決しない

### 選択肢 D: CRaC(Coordinated Restore at Checkpoint)

- **概要**: JVM プロセスのスナップショットを取り、warm state から復元することで起動高速化を図る。OpenJDK CRaC / Azul Zing JVM で利用可能。
- **利点**: JIT コンパイル済みのウォーム状態から復元できるため最高スループット
- **欠点**:
  - AWS ECS Fargate での CRaC サポートが限定的(Linux checkpoint/restore にホスト権限が必要)
  - Spring Boot の CRaC サポートは Spring Boot 3.2 以降だが実運用実績が少ない
  - `@Profile` 制約は解決されない
- **リスク・未知数**: Fargate の実行環境で `CRIU`(Checkpoint/Restore In Userspace)が利用可能か不明

## 3. 決定

**採用**: 選択肢 B — GraalVM Native Image

## 4. 理由

1. **Cold start 解決**: ECS Fargate でのスケールアウト・コンテナ再起動を高速化する最も直接的な手段。Fat JAR の 5〜15 秒に対し、Native Image では 100〜500 ms を見込む。
2. **Fargate コスト削減**: ヒープを 64〜128 MB に抑えることで task size を 512 MB → 256 MB クラスに落とせ、長期的に月次コストを削減できる。
3. **Spring Boot 4 の公式サポート安定**: Spring Boot 4 は Native Image ビルドを公式にサポートしており、Hibernate 6 / Flyway 10 も Native 対応済み。Keycloak Spring Security Adapter も Native hints を提供している。
4. **Spring プロファイル不使用との設計整合**: `@Profile` 使用禁止は Native Image AOT コンパイルで profile 分岐を build-time に解決できないリスクを回避する設計判断として位置付ける(infrastructure-plan v5 §2 #5 の伏線)。環境差は ECS Task Definition の ENV 変数注入で吸収するため、`@Profile` は不要である。
5. **ビルド時間のトレードオフを許容**: Native build に 5〜15 分かかるが、(a) ローカル開発は JVM で行い Native build は CI のみ、(b) GHA キャッシュで GraalVM ツールチェインをキャッシュし短縮、という運用で許容範囲と判断した。
6. **セキュリティ改善**: JVM ランタイムを同梱しないため、コンテナイメージのアタックサーフェスを削減できる。
7. **Reflection ヒントは管理可能**: `@RegisterReflectionForBinding` + Spring Boot AOT processor の自動解析でほぼカバーできる。残る手動 hints は `META-INF/native-image/` に集約管理する。

## 5. 影響

### 良い影響(Positive)

- コンテナ起動時間が 100〜500 ms 程度になり、ECS のオートスケール UX が大幅に改善する
- Fargate task size を縮小でき、月次インフラコストが削減できる
- Container image が小型化し、ECR への push / pull が高速になる
- `@Profile` 禁止を規約に明文化することで環境差の管理方式が統一される
- JVM を含まないコンテナは脆弱性スキャンのヒット数が減る

### 悪い影響・制約(Negative)

- **ビルド時間増**: Native build に GHA runner で 5〜15 分かかる。PR ごとに実行する場合は CI コスト・待ち時間が増加する → Sprint 1 以降に `:webapi:nativeBuild` を workflow に追加するが、初期は手動トリガーとし段階的に自動化する。
- **Reflection 制約**: 動的に class を解決するコード(`Class.forName(...)` / `java.lang.reflect.*` / `java.lang.invoke.*` の無制限使用)は build-time hint が必要。実装者は §20 の Native Image 実装制約を遵守する必要がある。
- **一部ライブラリ調査必須**: Native build 前に各依存ライブラリの Native 対応状況を確認し、非対応なら代替を検討する(詳細は §6 参照)。
- **デバッグ難化**: ネイティブバイナリはスタックトレースが簡略化される場合がある。Native build は CI のみで動かし、ローカルデバッグは JVM で行う。

### 既存ドキュメント・規約への波及

- `docs/specs/コーディング規約.md` に §20「Native Image 実装制約」を追加(本 ADR と同 PR)
- `docs/architecture/infrastructure-plan.md` §2 #5 の「Spring プロファイル不使用」の伏線がこの ADR に集約される
- `webapi/build.gradle` に GraalVM Native plugin を追加(Sprint 0/1 で実施)
- `.github/workflows/` に Native build / Native test workflow を追加(Sprint 1 以降)

## 6. 実装メモ

### 依存ライブラリの Native 対応状況

| ライブラリ | Native 対応状況 | 備考 |
|---|---|---|
| Spring Boot 4 | 対応済み(公式) | `spring-boot-starter-aot` で自動 hints |
| Spring Modulith 2.x | 対応済み | `spring-modulith-runtime` は Native 対応 |
| Hibernate 6.x | 対応済み | `hibernate-core` に native-image.properties 同梱 |
| Flyway 10.x | 対応済み | `flyway-database-mysql` は Native 対応 |
| Keycloak Spring Security Adapter | 要確認(Sprint 0/1 で検証) | Native hints の手動追加が必要な場合あり |
| Lombok | 対応済み | APT で compile-time に処理されるため Native に影響しない |
| Testcontainers | テスト用途のみ | Native test では使用しない方針(JVM テストで完結) |

### Reflection ヒント管理戦略

1. **Spring Boot AOT 自動解析に任せる**: `@Component` / `@Entity` / `@Repository` / `@RestController` などの Spring 管理 Bean は AOT processor が自動で hints を生成する
2. **`@RegisterReflectionForBinding` を明示**: Jackson のシリアライズ対象 DTO や、Spring 管理外で Reflection を必要とするクラスには `@RegisterReflectionForBinding(Foo.class)` を付与する
3. **手動 hints は `META-INF/native-image/` に集約**: AOT で対応できない場合は `webapi/src/main/resources/META-INF/native-image/xyz.dgz48.tasks.webapi/reflect-config.json` に記述する
4. **動的クラスロード禁止**: `Class.forName(...)` を使うコードは代替実装(型安全な注入 / `@Autowired` / `@Value`)に置き換える

### CI/CD への組み込み方針

```text
Sprint 0/1: ./gradlew :webapi:build (Fat JAR) のみ → Native build は手動トリガー
Sprint 1:   Native build を CI workflow に追加(nativeBuild job、PR トリガー)
Sprint 2:   Dockerfile を Native build ベースに変更し、ECR push を Native image に切り替え
```

GraalVM ツールチェインのキャッシュ:

```yaml
- uses: graalvm/setup-graalvm@v1
  with:
    java-version: '21'
    distribution: 'graalvm'
    cache: 'gradle'
```

### ローカル開発戦略

- **通常開発**: `./gradlew :webapi:bootRun`(JVM 起動、数秒)で開発・デバッグを行う
- **Native build はローカルでは行わない**: GraalVM ツールチェインのインストール・5〜15 分のビルド時間を開発者に強制しない
- **Native 動作確認**: CI/CD パイプラインの Native build ジョブで確認する

## 7. 参考リンク

- `docs/architecture/infrastructure-plan.md` §2 #5(Spring プロファイル不使用の宣言)
- `docs/architecture/infrastructure-plan.md` §3.4(環境変数による設定注入)
- `docs/specs/コーディング規約.md` §20(Native Image 実装制約)
- GitHub Issue #223(本 ADR 策定)
- GitHub Issue #206(親 tracker)
- [Spring Boot GraalVM Native Image Support](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Native Image Compatibility](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/)
