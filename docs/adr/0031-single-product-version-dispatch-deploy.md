# ADR-0031: デプロイ/リリースモデル再設計 — 単一プロダクトバージョン + dispatch 駆動デプロイ

- **Status**: Accepted
- **Date**: 2026-06-14
- **Deciders**: win2cot, 開発チーム
- **Tags**: ci-cd, release, infra
- **Supersedes**: [ADR-0026](0026-component-scoped-deploy-tags.md)(component 別 prefix tag・環境 suffix・tag 駆動デプロイを全面置換)/ [ADR-0030](0030-auto-github-release-per-deploy-tag.md)(全環境デプロイタグでの Release 自動生成・チャンネル比較を廃止)
- **Amends**: [ADR-0028](0028-build-once-promote-digest.md)(build once / promote digest / 巻き戻し防止の中核は存続。トリガを tag→dispatch、バージョン体系を component 別→単一プロダクトに改訂)

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点 1: バージョン体系](#論点-1-バージョン体系)
  - [論点 2: デプロイのトリガ](#論点-2-デプロイのトリガ)
  - [論点 3: Release の単位](#論点-3-release-の単位)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

[ADR-0026](0026-component-scoped-deploy-tags.md) は monorepo の 4 コンポーネント(`webapi` / `keycloak` / `web` / `infra`)を `<component>-vX.Y.Z[-dev|-stg]` の component 別 prefix + 環境 suffix tag で**タグ駆動デプロイ**する方式を、[ADR-0028](0028-build-once-promote-digest.md) は build once / promote digest を、[ADR-0030](0030-auto-github-release-per-deploy-tag.md) は全環境デプロイタグでの GitHub Release 自動生成(環境チャンネル比較)を定めた。

これらの運用設計を詰める過程で、GitHub 標準との構造的な不整合が明らかになった。

- GitHub Releases は「1 リポジトリ = 1 本の線形バージョン列」を前提とする。component 別 × 環境でタグを切ると、Release 一覧が混在し、`Latest` バッジが曖昧になり、前回比較に環境チャンネル解決が要り、monorepo ゆえに無関係コンポーネントの PR まで混入するため path 絞りが要る([ADR-0030](0030-auto-github-release-per-deploy-tag.md) の適応コスト)。
- 環境を tag に埋めると、環境非依存にしたい Release タグ(`vX.Y.Z`)と prd デプロイタグ(suffix なし `vX.Y.Z`)が衝突する。
- アプリのコンテナイメージは環境非依存([ADR-0028](0028-build-once-promote-digest.md))であり、Release も環境非依存にするのが自然。
- 本プロジェクトは単独運用で、4 コンポーネントを外部に個別出荷せず**1 つのプロダクトとして一緒にデプロイ**する。

したがって、デプロイ/リリースモデルを **単一プロダクトバージョン + dispatch 駆動デプロイ + GitHub Deployments による環境追跡** に再設計する。これは [ADR-0026](0026-component-scoped-deploy-tags.md) で却下した「選択肢 B(単一 tag + 変更検知)」の復活でもあるが、デプロイを tag 駆動から `workflow_dispatch` に移すことで、当時の却下理由(4 workflow 分の検知ロジック・誤判定リスク)が解消される。

## 2. 検討した選択肢(Options Considered)

### 論点 1: バージョン体系

#### 選択肢 A: component 別の独立バージョン(ADR-0026 現行)

- 概要: `webapi-v1.2.0` / `keycloak-v3.1.0` のように component ごとに独立した SemVer
- 利点: コンポーネントを独立にバージョニングできる
- 欠点: Release 一覧が混在、tag prefix、`Latest` 曖昧、前回比較に環境チャンネル解決が必要

#### 選択肢 B: 単一プロダクトバージョン + 変更検知(採用)

- 概要: リポジトリ全体で `vX.Y.Z` 1 本。ビルド/デプロイ対象は前バージョンとのパス差分で判定
- 利点: GitHub 標準(1 リポ = 1 バージョン列)に整合、混在一覧・`Latest` 曖昧・チャンネル解決・タグ衝突を一掃
- 欠点: 1 コンポーネントの変更でもプロダクトバージョンが進む(独立バージョンを持てない)。共有/ルート変更時の「全ビルド」規則が要る

### 論点 2: デプロイのトリガ

#### 選択肢 C: tag 駆動(環境 suffix tag、ADR-0026 現行)

- 概要: `webapi-v1.2.0-dev` 等の環境 suffix tag push でデプロイ
- 欠点: 環境を tag に埋める→ Release タグと衝突、デプロイのたびに tag を切る

#### 選択肢 D: workflow_dispatch(version + environment)(採用)

- 概要: デプロイは `workflow_dispatch`(入力 = バージョン + 環境)で起動
- 利点: tag から環境を排除、`gh` でスクリプト化(ローカル Claude / スクリプト起動)、Environment 承認ゲートと Deployments 追跡を native に得る
- 欠点: tag 駆動の「タグ = デプロイトリガ」という監査性を失う(→ dispatch run + Deployments レコードで代替)

### 論点 3: Release の単位

#### 選択肢 E: 環境ごと(ADR-0030 現行)

- 概要: dev/stg/prd のデプロイごとに Release
- 欠点: 環境軸で Release が乱立、pre-release 膨張、チャンネル比較が必要

#### 選択肢 F: バージョンごとに 1 つ、環境は Deployments で追跡(採用)

- 概要: Release はバージョン単位で 1 つ(環境非依存)。「どの環境に何が出ているか」は GitHub Deployments で追跡
- 利点: GitHub 標準に整合、環境軸の複雑さが消える

## 3. 決定(Decision)

**採用**: 論点 1 = B、論点 2 = D、論点 3 = F。

1. **単一プロダクトバージョン** `vX.Y.Z` をリポジトリ全体に 1 つ持つ。component prefix も環境 suffix も付けない。Release は 1 本の線形列。
2. **タグ = リリース生成トリガ**。`vX.Y.Z` タグ push が「変更検知 → ビルド → GitHub Release 作成」を起動する。**タグはデプロイのトリガではない**。
3. **ビルドは変更コンポーネントのみ**。前バージョンとのパス差分で判定する。共有/ルート変更は対象コンポーネントを全ビルドする(§6 マッピング)。未変更コンポーネントは前バージョンの成果物 digest に新バージョンタグを付け替える(同 digest)。infra はビルド成果物を持たず、`terraform validate` のみ実施する。
4. **デプロイは `workflow_dispatch`**(入力 = `version` + `environment`)。Environment 保護ルール(required reviewers)でゲートし、対象環境の現状と差分のあるコンポーネントのみ実処理する(同 digest は ECS no-op)。infra は対象環境に対する plan→承認→apply とする([ADR-0026](0026-component-scoped-deploy-tags.md) / [ADR-0028](0028-build-once-promote-digest.md) の安全弁を継承)。
5. **環境状態は GitHub Deployments / Environments で追跡**する(どのバージョンがどの環境に居るか)。
6. **Release 本文**は全リポジトリの changelog を `.github/release.yml` の `area/*` ラベルでコンポーネント章立てする。
7. **ロールバック**は旧バージョンを `workflow_dispatch` で対象環境に再デプロイする(専用機構を設けない)。
8. **不変条件**: 1 つの環境は常に単一プロダクトバージョンの整合したコンポーネント集合を持つ(部分・つまみ食いデプロイを許可しない)。

## 4. 理由(Rationale)

- 単一バージョン + 1 本の Release 列は GitHub Releases の素の前提に一致し、混在一覧・`Latest` 曖昧・チャンネル解決・タグ衝突を一掃する。
- 4 コンポーネントは外部に個別出荷せず 1 プロダクトとして一緒にデプロイされるため、プロダクト単位の単一バージョンが実態に合う。
- 環境非依存イメージ([ADR-0028](0028-build-once-promote-digest.md))と Release の環境非依存が揃い、「ビルドは 1 回、環境へは promote」が Release(バージョン)= 昇格単位として一貫する。
- dispatch 駆動は環境を tag から排し、`gh` でスクリプト化でき(ローカル Claude / スクリプトから起動)、Environment 承認ゲートと Deployments 追跡を native に得られる。tag 駆動の監査性は dispatch run + Deployments レコードで代替できる。
- 変更検知はパス差分(`git diff --name-only`)で決定的に行え、Release 本文の path 絞りで既に必要な計算を使い回せる。[ADR-0026](0026-component-scoped-deploy-tags.md) が選択肢 B を却下した理由(4 workflow 分の検知・誤判定)は、検知を 1 か所に集約する dispatch 駆動では当てはまらない。
- [ADR-0028](0028-build-once-promote-digest.md) の build once / promote digest / 巻き戻し防止は本モデルの土台としてそのまま生きる(トリガとバージョン体系のみ改訂)。

## 5. 影響(Consequences)

### 良い影響(Positive)

- GitHub 標準に整合し、Release 一覧・`Latest`・前回比較が native に機能する。
- 環境軸の Release 乱立・pre-release 膨張・チャンネル比較が不要になる。
- ロールバックが「旧バージョンを再デプロイ」で自然に表現される。
- 環境非依存イメージと整合し、デプロイがスクリプト化できる。

### 悪い影響・制約(Negative)

- 単一バージョンのため、1 コンポーネントの変更でもプロダクトバージョンが進む(コンポーネント独立バージョンは持てない)。
- 「共有/ルート変更 = 全ビルド」のパス集合を定義・保守する必要がある(§6)。
- 変更検知ロジックの保守が要る(ただしパス差分で決定的)。
- infra はビルド成果物を持たない非対称(タグ時は `validate` のみ、`plan` は PR とデプロイ時)。
- ECR タグ immutable([ADR-0028](0028-build-once-promote-digest.md))のため、ビルド再実行は「同バージョンで push 済みのコンポーネントをスキップ」する冪等性が要る。

### 既存ドキュメント・規約への波及

- [ADR-0026](0026-component-scoped-deploy-tags.md) を supersede(本 ADR が tag / デプロイモデルの正本)。
- [ADR-0030](0030-auto-github-release-per-deploy-tag.md) を supersede(環境軸 Release を廃止。path 絞りは「1 Release 内のコンポーネント章立て」に転用)。
- [ADR-0028](0028-build-once-promote-digest.md) を amend(build once / promote digest / 巻き戻し防止は存続、トリガ = dispatch・バージョン = 単一に改訂)。
- `docs/architecture/infrastructure-plan.md` / `docs/specs/開発計画書.md` の tag 駆動・環境 suffix・component 別バージョンの記述を本モデルへ改訂。
- Issue 影響: #481 / #482 / #483(app デプロイ workflow、未実装)を dispatch + 変更検知 + 単一バージョンに作り直し。#484(infra、実装済・`workflow_dispatch` 併設)は調整。#628 / #629(Release 基盤・infra retrofit)を本モデルに再設計。#630 で採択した [ADR-0030](0030-auto-github-release-per-deploy-tag.md) は本 ADR で superseded。親トラッカー #206。

## 6. 実装メモ(Implementation Notes)

### フロー A: タグ `vX.Y.Z` 作成時(リリース生成)

トリガ = `vX.Y.Z` タグ push(`on: push: tags: ['v*']`)。ビルド + Release 作成のみで、デプロイはしない。

1. 前バージョン `vPREV`(直前の `v*`)を解決。初回は基準なし = 全コンポーネント対象。
2. 変更検知: 各コンポーネント `C` で `git diff --name-only vPREV..vX.Y.Z -- <C>/`。差分ありで「変更あり」。
3. 共有/ルート変更の扱い(全ビルド誘発):
   - ルート Gradle 系(`build.gradle` / `settings.gradle` / `gradle.properties` / `gradle/` / `buildSrc/` / version catalog)→ {webapi, keycloak} を全ビルド
   - `.github/` / `docs/` / README → リビルド不要(成果物の中身に影響しない)
   - 上記マップ外の共有変更 → 保守的に全ビルド
   - `force-rebuild-all` フラグ(手動)= ベースイメージ更新・ビルドロジック変更時の逃げ道
4. ビルド(変更ありのみ):
   - webapi / keycloak: イメージ build → ECR(`tasks-webapi` / `keycloak-custom`)に `vX.Y.Z` で push
   - web: 静的バンドル build → S3 成果物 prefix(`web/vX.Y.Z/`)へ保存
   - infra: ビルド成果物なし。`terraform validate` のみ(環境非依存・creds 不要)。`terraform plan` はここでは回さない(環境 state に依存し、先行する dev に対して縮退 diff を出すなど不健全。plan は PR とデプロイ時に行う)
5. 未変更コンポーネント: 前バージョンの digest に `vX.Y.Z` タグを付け替え(同 digest、安い)。
6. ビルド成功後に GitHub Release `vX.Y.Z` を作成(本文 = `vPREV..vX.Y.Z` の changelog を `.github/release.yml` の `area/*` で章立て)。
   - ビルド / `validate` 失敗 → Release を作らず workflow を fail(未完成バージョンの Release を残さない)。再実行は push 済みコンポーネントをスキップ(ECR immutable 対応)。

### フロー B: デプロイ実行時

トリガ = `workflow_dispatch`(`version` + `environment`)。`gh workflow run` でスクリプト / ローカル Claude から起動可。

1. `version` の成果物を解決(webapi/keycloak = ECR digest、web = バンドル、infra = `vX.Y.Z` コミットの terraform)。Release / 成果物が無ければ fail-closed。
2. job が `environment: <選択>` を参照 → required reviewers の承認(dev = なし / stg・prd = あり 等、環境ごとに設定)。
3. 対象環境の現状と差分のあるコンポーネントのみ実処理:
   - webapi/keycloak: 目標 digest ≠ 現 digest のときだけ ECS 更新(同 digest = no-op、rolling update 起きない)
   - web: バンドル差分時のみ S3 sync + CloudFront invalidation
   - infra: `terraform plan` → 変更あれば承認 → apply(無ければ no-op)。[ADR-0028](0028-build-once-promote-digest.md) (iii) の巻き戻し防止ガードを継承
4. `environment:` 参照で GitHub Deployment が作られ、`vX.Y.Z` がその環境に出たことを記録。
   - per-env 同時実行は `concurrency: deploy-<env>` で直列化
   - (任意)Deployment に `environment_url`(dev/stg/prd の稼働 URL)を付け、Environments UI から飛べるようにする

### その他

- バージョン番号は当面手動(切るときに SemVer 判断で `vX.Y.Z` を決める)。自動 bump は頻度増加時に再検討。
- 成果物保持 = 直近 N バージョン / 90 日のいずれか長い方 + 現 prod バージョンは常に保持(ロールバック地平に合わせる)。
- CI 検証ビルド(既存 `*-ci.yml`、PR で test/build)とリリース成果物ビルド(タグ時)は別物。PR plan(`terraform-plan.yml`)はマージ前の infra 検証として存続。

## 7. 参考リンク(References)

- [ADR-0026](0026-component-scoped-deploy-tags.md)(supersede)/ [ADR-0028](0028-build-once-promote-digest.md)(amend)/ [ADR-0030](0030-auto-github-release-per-deploy-tag.md)(supersede)
- Issue #481 / #482 / #483 / #484 / #628 / #629 / #630、親トラッカー #206
- [GitHub Docs: Automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
- [GitHub Docs: Managing environments for deployment](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)
- [GitHub Docs: Events that trigger workflows — workflow_dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
