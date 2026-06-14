# ADR-0030: 全環境デプロイタグでの GitHub Release 自動生成(チャンネル比較)

- **Status**: Accepted
- **Date**: 2026-06-14
- **Deciders**: win2cot, 開発チーム
- **Tags**: ci-cd, release
- **Amends**: [ADR-0026](0026-component-scoped-deploy-tags.md) §4 / [ADR-0028](0028-build-once-promote-digest.md) §3.4(両 ADR が「GitHub Release を稼働記録 / リリース束 note に使う」と示した方向を、運用・粒度・本文仕様として具体化する。両 ADR の決定は不変)

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点 1: トリガー(どのタグで Release を作るか)](#論点-1-トリガーどのタグで-release-を作るか)
  - [論点 2: 前回比較の基準](#論点-2-前回比較の基準)
  - [論点 3: 本文の内容(Issue or PR)](#論点-3-本文の内容issue-or-pr)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

[ADR-0026](0026-component-scoped-deploy-tags.md) §4 は「製品全体を指す単一バージョンは持たず、リリースの束は GitHub Release の release note で代替する」とし、[ADR-0028](0028-build-once-promote-digest.md) §3.4 は「稼働バージョンの記録は git tag + ECS + GitHub Release」とした。いずれも GitHub Release を記録レイヤーとして使う方向を示したが、**いつ・どの粒度で・何を本文にするか**の運用は未定義のまま残っていた。

本プロジェクトは単独運用 + MVP 期間で dev のみ構築の段階にあり、Release の主目的は外部公開ではなく **内部の人 / Claude が「どの環境に何がリリースされたか」を一覧で把握する**ことである。デプロイタグは component 別 prefix(ADR-0026)で、build once / promote digest(ADR-0028)により dev / stg / prd は同一 version で同一 commit・同一 digest を指す。この前提のもとで Release 運用を確定する。

## 2. 検討した選択肢(Options Considered)

### 論点 1: トリガー(どのタグで Release を作るか)

#### 選択肢 A: prd タグのみ

- 概要: 環境 suffix なしの prd タグ push 時だけ Release を作る(外部リリースの一般的慣行)
- 利点: 「Release = 本番に出た版」という意味論が素直
- 欠点: MVP 期間は dev のみ構築のため当面 Release がゼロになり、目的(どの環境に何が出たかの把握)を満たさない
- リスク・未知数: 特になし

#### 選択肢 B: 全環境タグ(dev / stg / prd)で作る

- 概要: dev / stg / prd すべてのデプロイタグ push で Release を作る
- 利点: dev であっても「今その環境に何が出ているか」を一覧で把握できる。内部把握という目的に直結
- 欠点: Release 数が component × env × version で増える
- リスク・未知数: prd 以外の Release を「正式版」と誤読される懸念は pre-release バッジで回避

### 論点 2: 前回比較の基準

#### 選択肢 C: 直前 Release を自動検出

- 概要: `--generate-notes` の既定(直近 Release を起点)に任せる
- 利点: 実装ゼロ
- 欠点: component / env が混在した一覧では誤った直前を拾う。特に昇格(同一 commit の `-stg` / 素タグ)では直前 `-dev` と同 commit になり **差分が空**になる
- リスク・未知数: 「その環境に何が出たか」を表せない

#### 選択肢 D: (component, env) チャンネル比較

- 概要: previous tag を **同 component・同 env** の直前タグに明示解決する
- 利点: 「その環境に新しく到達した PR」が正確に出る。build once / promote(昇格は同 commit)とも整合し、意味のある差分は env チャンネル内でのみ生じる、という性質に合致
- 欠点: previous tag 解決の小ロジック(prefix + env suffix で filter + version sort)が要る
- リスク・未知数: 初回(チャンネルに前タグ無し)の扱いを定義する必要

### 論点 3: 本文の内容(Issue or PR)

#### 選択肢 E: PR ベース

- 概要: `--generate-notes` でマージ済み PR を一覧化
- 利点: 「どの feature / bugfix が出たか」を読む内部用途に適合。番号は GitHub UI で自動リンク。`Closes #N` 運用により PR から Issue へ辿れる
- 欠点: Issue を直接列挙はしない
- リスク・未知数: 特になし

#### 選択肢 F: Issue ベース(独自集約)

- 概要: 範囲内マージ済み PR から `Closes #N` を抽出し Issue を解決して一覧化
- 利点: Issue 中心の一覧
- 欠点: 独自スクリプトの実装・保守。閲覧者(内部の人)が見たいのは「出荷された変更単位」であり PR と粒度が近い
- リスク・未知数: 抽出漏れ・解決失敗の保守コスト

## 3. 決定(Decision)

**採用**: 論点 1 = 選択肢 B、論点 2 = 選択肢 D、論点 3 = 選択肢 E

1. **トリガー**: 全環境デプロイタグ(`<component>-vX.Y.Z-dev` / `-stg` / 素タグ=prd)push 時に、そのタグを Release タグとして GitHub Release を作成する。対象 component は `webapi` / `keycloak` / `web` / `infra` の 4 つ。
2. **粒度**: 束ねず component 別。Release タグはデプロイタグそのもの。一覧で混在してよく、Release 名 `<component> vX.Y.Z (<env>)` で識別する。
3. **本文**: PR ベースの自動生成ノート(`--generate-notes`)。比較基準は **(component, env) チャンネル**の前回タグを `--notes-start-tag` に明示する(他環境とは比較しない)。チャンネルに前タグが無い初回は起点省略で作成する。
4. **pre-release バッジ**: dev / stg は pre-release(`--prerelease` + `--latest=false`)、prd(素タグ)は latest。一覧で成熟度をバッジ識別する。
5. **カテゴリ分け**: `.github/release.yml` を置き、`area/*` ラベルで changelog をカテゴリ表示する。
6. **実装形態**: 各 deploy workflow(#481 / #482 / #483 + infra #484 retrofit)が共通の reusable `create-release`(#628)を deploy 成功後に呼ぶ。4 本で DRY 化する。
7. **非ブロッキング**: Release 作成失敗は deploy job を失敗させない(warning のみ)。デプロイ自体は成功しているため、記録生成の失敗で止めない。

## 4. 理由(Rationale)

- 目的が内部把握である以上、dev を含む全環境で Release を作るほうが価値が高い。Release はイメージを再添付せず notes 生成のみなのでコストは極小である。
- チャンネル比較は「その環境に何が出たか」を正確に表す唯一の基準であり、build once / promote(昇格は同 commit)の性質(意味ある差分は env チャンネル内でのみ生じる)と一致する。
- PR ベースは出荷された変更単位(feature / bugfix)で読め、`Closes #N` で Issue へ辿れるため、Issue 独自集約(選択肢 F)の保守を持ち込まずに目的を満たす。
- pre-release バッジにより「dev / stg は検証段、prd が正式版」という成熟度が一覧で即座に伝わり、全環境 Release の誤読(選択肢 B の懸念)を回避できる。
- 捨てた利点: prd 限定(選択肢 A)の意味論の素直さ。MVP 期間に Release ゼロになる代償が目的と矛盾するため採らない。

## 5. 影響(Consequences)

### 良い影響(Positive)

- 内部の人 / Claude が Release 一覧で全環境の稼働内容を把握できる。
- ADR-0026 §4 / ADR-0028 §3.4 が示した GitHub Release 活用の未実装部分が、運用として確定する。
- Release 本文は後から編集可能(annotated tag メッセージと異なり force re-tag 不要)で、changelog を安全に追補できる。

### 悪い影響・制約(Negative)

- repo は public のため Release は世界から可視になる(内部向け内容が公開される)。機密は元から Release 本文・タグメッセージに載せない運用とする。
- Release 数が component × env × version で増える。
- previous tag 解決の小ロジック(prefix + env suffix の filter)が必要。prd(suffix なし)は `-dev` / `-stg` を除外する。

### 既存ドキュメント・規約への波及

- [ADR-0026](0026-component-scoped-deploy-tags.md) §4 / [ADR-0028](0028-build-once-promote-digest.md) §3.4 — 本 ADR への相互参照を必要に応じて補足(決定は不変)。
- Issue #628(Foundation)— reusable `create-release` + channel 比較 + `.github/release.yml` + pre-release ポリシーの実装担当。
- Issue #629(infra retrofit)— closed の #484 に Release 作成 step を後付け。
- Issue #481 / #482 / #483 — deploy 成功後に `create-release`(#628)を呼ぶ step を追補済(本体 deploy は #628 にブロックされない)。
- 採択 Issue: #630(本 ADR の PR が `Closes #630`)。

## 6. 実装メモ(Implementation Notes)

- Release 作成(reusable `create-release` の中核):

  ```bash
  # 例: 現タグ webapi-v1.2.0-stg(component=webapi, env=stg)
  PREV=$(git tag --list 'webapi-v*-stg' --sort=-v:refname | grep -vFx "$CUR_TAG" | head -1)
  gh release create "$CUR_TAG" --generate-notes \
    ${PREV:+--notes-start-tag "$PREV"} \
    --prerelease --latest=false \
    --title "webapi v1.2.0 (stg)"
  ```

- prd(suffix なし)のチャンネル抽出は `-dev` / `-stg` を除外する:

  ```bash
  git tag --list 'webapi-v*' --sort=-v:refname | grep -Ev -- '-(dev|stg)$' | grep -vFx "$CUR_TAG" | head -1
  ```

- prd は `--latest`(pre-release 指定なし)、dev / stg は `--prerelease --latest=false`。
- チャンネルに前タグが無い初回は `--notes-start-tag` を省略する(`${PREV:+...}` で空時に付けない)。空でも Release 作成は成功させる。
- `.github/release.yml` は `changelog.categories` を `area/*` ラベルで分類する。
- Release 作成は deploy job の最終 step とし、失敗しても job を fail させない(`continue-on-error` 等)。
- web / infra はイメージを持たないが Release は notes のみのため対象に含む。infra の Release は terraform apply の changelog。

## 7. 参考リンク(References)

- [ADR-0026](0026-component-scoped-deploy-tags.md) §4 — リリース束を Release note で代替
- [ADR-0028](0028-build-once-promote-digest.md) §3.4 — 稼働記録に GitHub Release
- Issue #628 / #629 / #630、消費側 #481 / #482 / #483 / #484、親 tracker #206
- [GitHub Docs: Automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
- [gh release create](https://cli.github.com/manual/gh_release_create) — `--generate-notes` / `--notes-start-tag` / `--prerelease` / `--latest`
