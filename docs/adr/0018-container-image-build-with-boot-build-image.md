# ADR-0018: コンテナイメージ作成手段として Spring Boot bootBuildImage(Cloud Native Buildpacks)を採用

- **Status**: Accepted
- **Date**: 2026-06-07
- **Deciders**: 開発チーム(win2cot)
- **Tags**: infrastructure, build, container, runtime

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [選択肢 A: bootBuildImage(Cloud Native Buildpacks)](#選択肢-a-bootbuildimagecloud-native-buildpacks)
  - [選択肢 B: マルチステージ Dockerfile(コンテナ内ビルド)](#選択肢-b-マルチステージ-dockerfileコンテナ内ビルド)
  - [選択肢 C: CI ランナーでビルドして COPY する Dockerfile](#選択肢-c-ci-ランナーでビルドして-copy-する-dockerfile)
  - [選択肢 D: Jib](#選択肢-d-jib)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

ADR-0008 で tasks-webapi の実行バイナリとして GraalVM Native Image を採用した。Sprint 2 の S2Infra-1(#478)でコンテナイメージ作成手段を確立するにあたり、「どうやって Native Image をコンテナイメージにするか」は ADR-0008 では未決定だった(同 ADR §6 は「Dockerfile を Native build ベースに変更」と記述していたが、これは手段を比較検討した結果ではない)。

選定の前提条件(2026-06-07 議論で確定):

- チームに Native Image ビルドの知見が薄く、**自分たちの裁量・自前管理が大きい手段はリスク**と評価する
- **Distribution 相当部分(ベースイメージ)はなるべく薄く**する
- **過大な CI 時間増加は避ける**(ただし支配項である native コンパイル自体は手段によらず共通)
- リンク方式は **mostly-static(glibc 動的リンク)で確定**(full-static/musl は性能・実績面の不安から不採用)
- ローカルでのイメージ再現手順の簡便さは重視しない
- ECS exec(コンテナ内 shell)はしない前提

また、Native バイナリの実行時依存は次の通りで、ベースイメージ選定の土台となる:

| 要素 | 必要か | 根拠 |
|---|---|---|
| JVM / JRE | 不要 | AOT コンパイル済みバイナリ |
| glibc | 必要 | mostly-static リンクのため libc のみ動的 |
| tzdata | 不要(同梱) | GraalVM 20.1+ で全タイムゾーンをバイナリに同梱。`TZ` env が実行時に有効 |
| CA 証明書 | おそらく不要(要検証) | ビルド時 JDK の cacerts を同梱する設計。#478 実装時に TLS(RDS / Keycloak)で検証 |
| shell | 不要 | ECS exec をしない前提 |

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: bootBuildImage(Cloud Native Buildpacks)

- 概要: Spring Boot 標準の Gradle task。`org.graalvm.buildtools.native` プラグインが適用されていれば `bootBuildImage` が native モードでイメージを生成する。Dockerfile を書かない。Spring Boot 4 の既定 builder は `paketobuildpacks/builder-noble-java-tiny`(Ubuntu 24.04 Noble)、run image は `paketobuildpacks/ubuntu-noble-run-tiny`。
- 利点:
  - ビルドレシピを Spring Boot + Paketo が管理し、自前の裁量・知見要求が最小
  - builder と run image が同一 Noble 世代のため **glibc 不整合が構造的に発生しない**
  - run image は `FROM scratch` に 8 つの deb(`base-files` / `ca-certificates` / `libc6` / `libssl3` / `netbase` / `openssl` / `tzdata` / `zlib1g`)のみを展開した distroless 同等の構成(shell なし)。Google distroless と同じ構築手法で、薄さの要求をデフォルトで満たす
  - ビルドがコンテナ内で完結するため環境非依存(ローカル / CI で同等のバイナリ)
  - run image の CVE 対応は Paketo の再発行をリビルドで追従
- 欠点:
  - ビルダーの内部動作がブラックボックス気味(トラブル時の調査が Paketo 層に及ぶ)
  - Docker daemon 必須(ローカル再現手順は重視しない前提のため許容)
  - Gradle キャッシュがコンテナ内に閉じ、CI でのキャッシュ効率は劣る(後述の役割分担で緩和)
- リスク・未知数: builder / run image のバージョン固定方針(latest 追従 or pin)は実装時に判断

### 選択肢 B: マルチステージ Dockerfile(コンテナ内ビルド)

- 概要: builder ステージ = GraalVM 公式コンテナ(`ghcr.io/graalvm/native-image-community` 等)で `:webapi:nativeCompile` を実行し、runtime ステージ(`gcr.io/distroless/base-debian12` 等)へバイナリを COPY する。
- 利点:
  - 全レイヤを自分で制御でき、ベースイメージを自由に選択できる(distroless/base-debian12 は約 8.5MB)
  - コンテナ内ビルドのため環境非依存性は A と同等に高い
- 欠点:
  - Dockerfile・ビルド引数・builder/runtime ベースの更新をすべて自前管理。**Native Image の知見が薄い現状では裁量が大きすぎる**
  - glibc 整合(builder と runtime の distro 世代)を自分で保証し続ける必要がある
  - distroless のタグ更新追従も自前
- リスク・未知数: ビルドメモリ・引数のチューニングを自前で持つ

### 選択肢 C: CI ランナーでビルドして COPY する Dockerfile

- 概要: GHA ランナー上で `setup-graalvm` + `:webapi:nativeCompile` を実行し、Dockerfile は `FROM <base>` + `COPY <binary>` の数行のみ。
- 利点:
  - Gradle / GraalVM キャッシュが GHA の action キャッシュに直結し、CI 上のオーバーヘッドが最小
  - nativeBuild CI job(#490)と成果物・キャッシュを完全共用できる
- 欠点:
  - **glibc 不整合の罠**: ランナー(Ubuntu 24.04、glibc 2.39)でビルドした動的リンクバイナリは、より古い glibc のベース(debian12 = 2.36)で動かない可能性がある(glibc に前方互換なし)。ベース選択が制約されるか、整合管理が自前になる
  - ビルドが GHA ランナー環境に依存し、環境非依存性が劣る(ローカルビルドと別物になり得る)
- リスク・未知数: ランナーイメージ更新(glibc 上がる)に伴う暗黙の破壊

### 選択肢 D: Jib

- 概要: Google 製の Dockerfile レスなコンテナ化ツール。
- 利点: JVM アプリでは実績豊富。
- 欠点: **Native Image の公式サポートがない**。native バイナリを包む使い方は非標準で、採用メリットがない。
- リスク・未知数: 評価対象外(早期除外)。

横断比較(2026-06-07 議論の確定版):

| 観点 | A: bootBuildImage | B: multi-stage Dockerfile | C: CI ビルド + COPY |
|---|---|---|---|
| ベースイメージ | `ubuntu-noble-run-tiny` = scratch + 8 deb、shell なし、distroless 同等 | distroless/base-debian12(8.5MB、構成ほぼ同じ) | 同左、ただし glibc 整合で選択に制約 |
| Distribution の薄さ | 薄い | 薄い | 薄い(制約付き) |
| 自分たちの裁量・必要知見 | **最小** | 大 | 中 |
| ビルド再現性(環境非依存性) | **高**(コンテナ内完結) | 高(同左) | 中(ランナー依存) |
| glibc 整合 | **構造的に安全** | 自前管理 | 罠あり |
| ベース更新・CVE 追従 | Paketo 再発行に追従 | 自前 | 自前 |
| CI 時間 | 支配項(native コンパイル 5〜15 分)は共通。役割分担で緩和 | 同様、キャッシュ不利 | 最速だが glibc とトレード |
| ローカル再現手順 | Gradle 一発(Docker 必須) | docker build 一発 | 二本立て |
| ECS exec | 不可(shell なし) | ベース次第 | 同左 |

## 3. 決定(Decision)

**採用**: 選択肢 A — Spring Boot `bootBuildImage`(Cloud Native Buildpacks)

builder / run image は Spring Boot 4 の既定(`builder-noble-java-tiny` / `ubuntu-noble-run-tiny`)を使用し、run image の差し替えは行わない。Dockerfile は作成しない。

## 4. 理由(Rationale)

1. **裁量の最小化**: Native Image ビルドの知見が薄い前提条件に対し、レシピを Spring Boot + Paketo に委譲できる唯一の選択肢。B は薄さ・制御性で同等以上だが、自前管理の範囲が広く前提条件に反する。
2. **薄さはデフォルトで達成**: run image の実体は `FROM scratch` + 8 deb の distroless 同等構成であり、「Distribution はなるべく薄く」を追加作業なしで満たす。差し替えによる更なる薄化(十数 MB)は glibc 整合の検証負担に見合わない。
3. **glibc 整合の構造的安全**: builder と run が同一 Noble 世代のため、mostly-static(glibc 確定)の前提で不整合が起き得ない。C はこの罠を自前で管理することになる。
4. **CI 時間は役割分担で許容範囲**: 支配項の native コンパイル(5〜15 分)は手段共通。PR 検証(#490)は `nativeCompile` 単体(builder pull・イメージ組立なし)+ changes ゲートで最小化し、`bootBuildImage` フル実行は tag 駆動デプロイ(#481)時のみとする。
5. **捨てた利点の明示**: C の CI キャッシュ最速・A/B 比での時間短縮は捨てる。ローカル再現手順の Docker 必須・ECS exec 不可は前提条件により不問。

## 5. 影響(Consequences)

### 良い影響(Positive)

- Dockerfile という新たな自前管理物が発生しない(リポジトリに置く IaC 的成果物は Gradle 設定に集約)
- ローカル / CI どこでビルドしても同等のイメージが得られる
- ベースイメージの CVE 追従が「リビルドして再 push」に単純化される

### 悪い影響・制約(Negative)

- ビルド内部(buildpack の検出・レイヤ構成)がブラックボックス気味で、ビルド失敗時の調査に Paketo の知識が必要になる場合がある
- `bootBuildImage` の実行に Docker daemon が必要(CI は GHA ランナーの Docker で充足)
- ECS exec によるコンテナ内デバッグは不可(shell なし。前提条件として受容済み)

### 既存ドキュメント・規約への波及

- ADR-0008 §6「CI/CD への組み込み方針」の「Dockerfile を Native build ベースに変更」という記述は本 ADR が確定させた手段に読み替える(同節に注記を追加)
- infrastructure-plan §6 S2Infra-1 の「`webapi/Dockerfile` を作成」記述は #478 close 時に doc 同期する(#478 のやること欄に明記済み)

## 6. 実装メモ(Implementation Notes)

- #478(S2Infra-1): `org.graalvm.buildtools.native` プラグイン追加 + `bootBuildImage` 設定(builder / run image は既定のため設定は最小)。検証項目: (1) RDS IAM 認証クライアント(AWS JDBC ドライバ)の reflection hints 要否、(2) `TZ` env による JST 出力(#265 の「Native Image 後の挙動要再検証」をここで解消)、(3) ビルド時 cacerts 同梱での TLS 接続(RDS / Keycloak)
- #490(S2Infra-8): PR トリガーの nativeBuild CI job は `bootBuildImage` ではなく `:webapi:nativeCompile` 単体を実行する(AOT 互換性の破壊検知が目的であり、イメージ組み立ては不要)。changes ゲート + concurrency で時間影響を最小化
- #481(S2Infra-4): tag 駆動デプロイで `bootBuildImage` フル実行 → ECR push。native コンパイル 5〜15 分 + イメージ組立は tag 時のみのため許容
- ビルドメモリ: native コンパイルは 8GB 級を要求するが、GHA public リポジトリの標準ランナー(4 vCPU / 16GB)で充足
- builder / run image のバージョン pin の要否(latest 追従 or digest 固定)は #478 実装時に判断し、結果を PR に記録する

## 7. 参考リンク(References)

- [ADR-0008: tasks-webapi の実行バイナリとして GraalVM Native Image を採用](0008-graalvm-native-image.md)
- Issue #478(S2Infra-1 コンテナイメージ)/ #490(S2Infra-8 nativeBuild CI job)/ #481(S2Infra-4 tag 駆動デプロイ)/ #366(changes ゲート方式)/ #265(JST 全層統一)
- [Spring Boot: Developing Your First GraalVM Native Application(v4.0.6)](https://github.com/spring-projects/spring-boot/blob/v4.0.6/documentation/spring-boot-docs/src/docs/antora/modules/how-to/pages/native-image/developing-your-first-application.adoc)
- [Paketo Java Native Image Buildpack Reference](https://paketo.io/docs/reference/java-native-image-reference/)
- [paketo-buildpacks/noble-tiny-stack(stack.toml / run.Dockerfile)](https://github.com/paketo-buildpacks/noble-tiny-stack)
- [GraalVM: Build a Statically Linked or Mostly-Statically Linked Native Executable](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-static-executables/)
- [GoogleContainerTools/distroless: base/static の構成](https://github.com/GoogleContainerTools/distroless/blob/main/base/README.md)
- [oracle/graal #2692: native image の timezone(20.1+ で全 TZ 同梱)](https://github.com/oracle/graal/issues/2692)
- [aws/aws-advanced-jdbc-wrapper(IAM 認証プラグイン、GraalVM テスト実施)](https://github.com/aws/aws-advanced-jdbc-wrapper)
