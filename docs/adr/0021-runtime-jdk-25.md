# ADR-0021: ランタイム / ビルド JDK を Java 25 (LTS) に引き上げる

- **Status**: Accepted
- **Date**: 2026-06-09
- **Deciders**: 開発チーム(win2cot)
- **Tags**: runtime, build, infrastructure, native-image

## 目次

- [1. コンテキスト](#1-コンテキスト)
- [2. 検討した選択肢](#2-検討した選択肢)
  - [選択肢 A: Java 25 (LTS) に引き上げる](#選択肢-a-java-25-lts-に引き上げる)
  - [選択肢 B: Java 21 のまま native build のみ Java 25](#選択肢-b-java-21-のまま-native-build-のみ-java-25)
  - [選択肢 C: Java 21 を維持し Native Image を撤回(JVM 配信に戻す)](#選択肢-c-java-21-を維持し-native-image-を撤回jvm-配信に戻す)
- [3. 決定](#3-決定)
- [4. 理由](#4-理由)
- [5. 影響](#5-影響)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ](#6-実装メモ)
- [7. 参考リンク](#7-参考リンク)

## 1. コンテキスト

ADR-0008 で tasks-webapi の実行バイナリを GraalVM Native Image に、ADR-0018 でコンテナイメージ作成手段を `bootBuildImage`(Cloud Native Buildpacks)に確定した。Sprint 2 の #478 で native イメージ基盤を実装する過程で、**Spring Framework 7 / Spring Boot 4 が native image の GraalVM baseline を Java 25 に引き上げている**ことが判明した(paketo-buildpacks/spring-boot Issue #562、Spring Boot 4 は native image を Java < 25 で起動しようとすると実行時チェックで停止する)。

本プロジェクトは scaffold 以来 Java 21 (LTS) を採用してきた。設計ドキュメント(基本設計書 §技術スタック)に「Eclipse Temurin 21 **(LTS)**」とあるのみで、Java 21 を選んだ唯一の根拠は **LTS であること**である。ADR-0008 採用(2026-05-24)時点ではこの baseline 引き上げは考慮されておらず、見落としであった。

重要な非対称性:

- **JVM モード**では Java 21 で全く問題ない(Spring Boot 4.0 の最低は Java 17、21 / 25 をサポート)
- **native image 配信**(ADR-0008 で確定済)では **Java 25 が事実上必須**

native image 配信を続ける以上、JDK バージョンの引き上げが必要になった。本 ADR でその決定根拠を集約する。

## 2. 検討した選択肢

### 選択肢 A: Java 25 (LTS) に引き上げる

- 概要: toolchain / CI / 配信のすべてを Java 25 にする。
- 利点:
  - native image 配信(ADR-0008 / ADR-0018)が成立する
  - Java 25 も LTS(2025-09-16 GA、約 8 年保守)であり、Java 21 を選んだ唯一の根拠「LTS」をそのまま満たす
  - Spring Framework 7 は Java 25 を推奨しており、フレームワーク方針と整合する
  - 2026-06-08 のローカル実ビルド検証で、開発ツールチェーンの追随コストが小さいことを確認済み(後述)
- 欠点:
  - toolchain / CI / docs の一括更新が必要
  - 一部ツールの版上げが伴う(google-java-format)
- リスク・未知数: native build を Java 25 で完遂する検証はメモリ制約によりローカル未完了。GHA で別途検証する(#478 の検証手段と共通)。

### 選択肢 B: Java 21 のまま native build のみ Java 25

- 概要: 開発 / テストは Java 21、native ビルド時のみ GraalVM 25 を使う。
- 利点: 言語レベルを保守的に据え置ける。
- 欠点: 開発 JDK と native ビルド JDK が二重管理になり、ローカルと CI で挙動が乖離する温床になる。AOT 処理(`processAot`)は build 時に Java を使うため、結局 build 系は 25 を要求し、利点が薄い。
- リスク・未知数: 「JVM テストは 21 で緑、native は 25 でしか動かない」差分が常態化し、不具合の検知が遅れる。

### 選択肢 C: Java 21 を維持し Native Image を撤回(JVM 配信に戻す)

- 概要: ADR-0008 を覆し、Fat JAR + JVM 配信に戻す。
- 利点: Java 21 を維持でき、Java 25 baseline 問題を回避できる。
- 欠点: ADR-0008 が native を選んだ根拠(ECS Fargate の cold start、メモリコスト、イメージサイズ、攻撃面)をすべて捨てる。ADR-0018 も連鎖的に無効化される。
- リスク・未知数: 大きな後戻り。MVP の非機能要件(スケールアウト時の UX)に影響する。

## 3. 決定

**採用**: 選択肢 A — **webapi モジュールの**ランタイム / ビルド JDK を Java 25 (LTS) に引き上げる

`webapi/build.gradle` の toolchain を `JavaLanguageVersion.of(25)` とし、`cicd.yml` の Set up JDK を 25 に、native ビルドの GraalVM も 25 に統一する。

**スコープは webapi に限定する。keycloak サブプロジェクト(User Storage SPI)は対象外で Java 21 を維持する。** 理由は、SPI JAR が公式 Keycloak イメージ(`quay.io/keycloak/keycloak:26.6.3`、OpenJDK 21 ランタイム)に**ロードされて他プロセス上で動く**成果物であり、bytecode を載せ先の JVM に合わせる必要があるため(Java は上位互換がなく、Java 25 でコンパイルした class は JDK 21 ランタイムで `UnsupportedClassVersionError` になる)。`keycloak/Dockerfile` のビルドステージも `eclipse-temurin:21-jdk-jammy` である。したがって `keycloak/build.gradle.kts` の toolchain と `keycloak-ci.yml` の JDK はいずれも 21 のまま据え置く。

native 配信される webapi は「自分自身が実行バイナリ」であるため Java 25 を選べるが、SPI は「他者のランタイムに従う」という非対称性が本決定の境界である。

## 4. 理由

1. **native 配信の前提**: ADR-0008 / ADR-0018 を維持する以上、Spring Boot 4 native の Java 25 baseline は避けられない。B は build 系が結局 25 を要求し利点が薄く、C は native 採用根拠を捨てる後戻り。
2. **「LTS」根拠の継承**: Java 21 を選んだ唯一の根拠は LTS であること。Java 25 も LTS であり、根拠を損なわずに上げられる。Spring Framework 7 も 25 を推奨。
3. **追随コストが小さいことを実証**: 2026-06-08 のローカル実ビルド検証で、開発ツールチェーンの Java 25 対応は **google-java-format を 1.24.0 → 1.34.0 に上げるだけで足りる**ことを確認(`clean :webapi:check` が Java 25 daemon で BUILD SUCCESSFUL、10 tasks 実走)。Error Prone 2.49.0 / NullAway 0.13.6 / Gradle 9.5.1 / Spring Boot 4.0.6 は据え置きで動作した。
4. **捨てた利点の明示**: Java 21 の「枯れた一つ前の LTS」という保守性は捨てる。ただし Java 25 も GA 済み LTS であり、実用上のリスクは限定的と判断した。

## 5. 影響

### 良い影響(Positive)

- native image 配信(ADR-0008 / ADR-0018)が成立する
- Spring Framework 7 推奨構成に揃い、将来の Spring アップデート追随が素直になる

### 悪い影響・制約(Negative)

- google-java-format を 1.24.0 → 1.34.0 に更新する必要がある(JDK 25 で javac 内部 API `Log$DeferredDiagnosticHandler.getDiagnostics()` のシグネチャが変わり、旧 gjf が `NoSuchMethodError` を出すため。Spotless 8.6.0 本体は据え置きで可)
- **Lombok の警告**: Java 25 daemon で Lombok が `sun.misc.Unsafe::objectFieldOffset`(将来削除予定 API)を呼ぶ警告が出る。現状ビルドは通るが、Lombok のバージョンは Spring Boot 4.0.6 BOM 管理任せのため、将来の JDK で問題化する前に明示 pin / 更新を検討する(本 ADR のスコープ外、フォローアップ課題)
- native build を Java 25 で完遂する検証は、AOT 解析が 8GB 級メモリを要求するためローカル(16GB ホスト)では未完了。GHA(標準ランナー)で検証する

### 既存ドキュメント・規約への波及

- 基本設計書 §技術スタック「Eclipse Temurin 21」→ 25
- 要件定義書 / 開発計画書 / コーディング規約 / `docs/dev/local-setup.md` の Java 21 記述を 25 に
- CI: `cicd.yml`(webapi)の Set up JDK を 25 に。**`keycloak-ci.yml` は 21 のまま据え置く**(keycloak SPI は対象外)
- ADR-0008 の前提(「Java 21 で native 配信」)を本 ADR が更新する旨を ADR-0008 に注記
- コーディング規約 §20(Native Image 実装制約)の JDK バージョン関連記述(#493 の同期作業に統合可)

## 6. 実装メモ

- JDK 25 化は **#478(native 基盤)とは別 PR** とする。#478 の事実上の blocker であり、JDK 25 化を先行 merge する
- 最小変更: `webapi/build.gradle`(toolchain 21→25、gjf 1.24.0→1.34.0)+ `cicd.yml` + docs 群。**keycloak サブプロジェクト(`build.gradle.kts` / `keycloak-ci.yml`)は触らない**
- native の Java 25 起動検証は GHA の使い捨て workflow で行う(#478 の native build 検証と共通の手段)
- Lombok 警告の解消(バージョン明示 pin 等)は別フォローアップ Issue で扱う

## 7. 参考リンク

- [ADR-0008: GraalVM Native Image 採用](0008-graalvm-native-image.md)
- [ADR-0018: コンテナイメージ作成手段に bootBuildImage を採用](0018-container-image-build-with-boot-build-image.md)
- Issue #478(native コンテナイメージ基盤)/ #490(nativeBuild CI)/ #493(コーディング規約 §20 同期)
- [paketo-buildpacks/spring-boot #562: Spring Boot 4 raises GraalVM baseline to 25](https://github.com/paketo-buildpacks/spring-boot/issues/562)
- [google-java-format リリース(1.34.0 で Java 25 対応)](https://github.com/google/google-java-format/releases)
- [JDK 25(OpenJDK、2025-09-16 GA / LTS)](https://openjdk.org/projects/jdk/25/)
