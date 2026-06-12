# ADR-0027: CI ワークフロー / ステータスチェックの命名規約

- **Status**: Accepted
- **Date**: 2026-06-12
- **Deciders**: win2cot, 開発チーム
- **Tags**: ci-cd, conventions, docs

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
  - [論点 1: 抽象ドメイン層を作るか](#論点-1-抽象ドメイン層を作るか)
  - [論点 2: kind 語の定義](#論点-2-kind-語の定義)
  - [選択肢 A: 現状維持](#選択肢-a-現状維持)
  - [選択肢 B: subject-kind 命名規約](#選択肢-b-subject-kind-命名規約)
- [3. 決定(Decision)](#3-決定decision)
  - [3.1 命名規則](#31-命名規則)
  - [3.2 リネーム一覧](#32-リネーム一覧)
  - [3.3 必須ステータスチェックの差し替え](#33-必須ステータスチェックの差し替え)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
  - [良い影響(Positive)](#良い影響positive)
  - [悪い影響・制約(Negative)](#悪い影響制約negative)
  - [既存ドキュメント・規約への波及](#既存ドキュメント規約への波及)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

`.github/workflows/` に 16 個のワークフローがある。GitHub のステータスチェック名は
ワークフロー表示名ではなく **job 名(job ID)** が使われるが、現状は job 名が複数ワークフロー間で
重複している。

- `check` … `code-quality` / `keycloak-ci` / `web-ci` の 3 ワークフローで同名
- `lint` … `markdown-lint` / `openapi-lint` / `terraform-lint` の 3 ワークフローで同名
- `changes` … paths-filter ゲート job として 10 ワークフローで同名

branch protection の必須チェックは名前(context)で照合するため、必須 `lint` / `check` が
「どのワークフローの結果でゲートしているか確定できない」状態になっている。見づらいだけでなく、
別ドメインの成功で必須が満たされうる潜在バグである。

さらに名前と中身が捻れている。`cicd.yml` の job は `test` だが中身は `./gradlew :webapi:check`
(JUnit + Spotless 検証 + 静的解析 + カバレッジの束)であり、`code-quality.yml` の job は `check`
だが gradle を一切使わず package-info.java 存在チェックと Flyway 命名規約検査を行う規約 lint である。
`nativeCompile` だけ camelCase で表記も不統一。表示名も `App` 系(CI / Code Quality / Native Build)が
バラバラで sidebar 上で分散している。

加えて本リポジトリ(monorepo)には `webapi/`(Spring Boot バックエンド)・`keycloak/`・`web/`
(フロントエンド)が同居しており、「app」という語はバックエンドにもフロントにも当てはまり曖昧である。
IaC ツールは Terraform 単一で、他ツール(Pulumi 等)導入の予定はない。

これらを一意・自己説明的に整える CI 命名規約を定める。

## 2. 検討した選択肢(Options Considered)

### 論点 1: 抽象ドメイン層を作るか

`iac-terraform-lint` のように抽象ドメイン(iac)+ ツール(terraform)の二段にするか、
`terraform-lint` のように具体名で平坦にするか。Terraform 以外の IaC ツールを導入する予定が
無いため、二段は冗長と判断。同様に `spec-openapi-lint` / `docs-markdown-lint` の抽象層も不要で、
対象そのもの(openapi / markdown / terraform / webapi / keycloak / web)を subject にする。

`iac-security-scan`(Trivy)は実 AWS リソースではなく `infra/` 配下の Terraform コードを
静的走査(`scan-type: config` / `scan-ref: infra/`)しているため、これも `terraform-` 配下に置ける。

### 論点 2: kind 語の定義

命名の suffix に使う「kind」語を、失敗が何を意味するかで定義する。

- **test** … コードを実行して期待動作を検証(動的)。JUnit 等。
- **lint** … 実行せず静的に規約・スタイル・軽微バグを検出。Spotless の検証(`spotlessCheck`)、
  Spectral、markdownlint、`terraform fmt -check` 等。
- **build** … 成果物(バイナリ / イメージ)を生成。失敗 = ビルド破壊検知。
- **scan(security)** … 脆弱性・設定ミスを走査。method(scan)でなく concern(security)で命名する。
- **plan / apply** … Terraform 固有の差分計画 / 適用。
- **ci** … 上記が複数混ざった「ビルド & 検証一式」の束。単一 kind に分解できないときに使う。

### 選択肢 A: 現状維持

- 概要: job 名・表示名・ファイル名をそのままにする。
- 利点: 変更コストゼロ。
- 欠点: 必須チェックの曖昧さ(`lint` / `check` の重複)が残り、潜在バグを放置する。
  名前と中身の捻れ、表記不統一も残る。

### 選択肢 B: subject-kind 命名規約

- 概要: ファイル名・表示名・チェック名を `<subject>-<kind>(-<限定>)` で 1:1:1 に揃える。
  subject は実モジュール / ディレクトリ名(webapi / keycloak / web / terraform / openapi / markdown)。
- 利点: 必須チェックが一意になり branch protection が明確化。stem を見れば中身と出所が直結。
  表示名の `<Subject>: <Kind>` で sidebar がグルーピングされる。
- 欠点: 全ワークフローのリネームと、それに伴う branch protection の必須リスト差し替えが必要。

## 3. 決定(Decision)

**採用**: 選択肢 B(subject-kind 命名規約)。

### 3.1 命名規則

- ファイル名 stem ・表示名 ・チェック名(job 名)を**同じ綴り**で揃える。
- 形式は `<subject>-<kind>(-<限定>)`。**ハイフンは語区切りであり階層レベルを表さない**
  (左 → 右で意味を絞る)。例: `terraform-plan-platform`、`webapi-lint-conventions`。
- subject は**実在のモジュール / ディレクトリ名**(webapi / keycloak / web / terraform /
  openapi / markdown)。抽象ドメイン層(iac / spec / docs)は作らない。
- kind は §2 論点 2 の定義に従う。複数 kind の束は `ci`、単一 kind はその kind 名、
  走査は concern(`security`)で命名する。
- paths-filter ゲート job は `<workflow-stem>-changes`(必須チェック外、認識性のため一意化)。
- bot / 自動化系(Claude / Renovate)は必須チェック外のため job 名は据え置き、
  ファイル名 `bot-` 接頭辞と表示名 `<Subject>: <Kind>` のみ整える。

### 3.2 リネーム一覧

| 新ファイル | 表示名 | チェック名(job) | changes job | 中身 |
| --- | --- | --- | --- | --- |
| `webapi-ci.yml` | Webapi: CI | `webapi-ci` | `webapi-ci-changes` | `:webapi:check`(Spotless検証 + 静的解析 + JUnit + JaCoCo) |
| `webapi-lint-conventions.yml` | Webapi: Conventions Lint | `webapi-lint-conventions` | `webapi-lint-conventions-changes` | package-info 存在 + Flyway 命名(webapi 限定) |
| `webapi-native-build.yml` | Webapi: Native Build | `webapi-native-build` | `webapi-native-build-changes` | GraalVM nativeCompile(イメージ生成、nativeTest 無し) |
| `keycloak-ci.yml` | Keycloak: CI | `keycloak-ci` | `keycloak-ci-changes` | `:keycloak:check` の束 |
| `web-ci.yml` | Web: CI | `web-ci` | `web-ci-changes` | lockfile 整合 + vendor アセット + スモーク |
| `openapi-lint.yml` | OpenAPI: Lint | `openapi-lint` | `openapi-lint-changes` | Spectral(単一 kind=lint) |
| `markdown-lint.yml` | Markdown: Lint | `markdown-lint` | `markdown-lint-changes` | markdownlint(単一 kind=lint) |
| `terraform-lint.yml` | Terraform: Lint | `terraform-lint` | `terraform-lint-changes` | fmt + validate + IAM wildcard gate |
| `terraform-plan.yml` | Terraform: Plan | `terraform-plan-platform` / `terraform-plan-tasks` | `terraform-plan-changes` | 各スタック plan |
| `terraform-apply.yml` | Terraform: Apply | `terraform-apply-platform` / `terraform-apply-tasks` | (なし、dispatch 専用) | 各スタック apply(手動) |
| `terraform-security-scan.yml` | Terraform: Security Scan | `terraform-security-scan` | `terraform-security-scan-changes` | Trivy で terraform コード静的走査 |
| `bot-claude-review.yml` | Claude: Review | (必須外、据置) | — | 自動化 |
| `bot-claude-impl.yml` | Claude: Impl | (必須外、据置) | — | 自動化 |
| `bot-claude-impl-fix.yml` | Claude: Impl Fix | (必須外、据置) | — | 自動化 |
| `bot-claude-auto-merge.yml` | Claude: Auto Merge | (必須外、据置) | — | 自動化 |
| `bot-claude-notify-human.yml` | Claude: Notify Human | (必須外、据置) | — | 自動化 |
| `bot-renovate-approve.yml` | Renovate: Auto Approve | (必須外、据置) | — | 自動化 |

### 3.3 必須ステータスチェックの差し替え

旧 7 件 → 新 11 件。旧 `lint`(×3)・`check`(×3)が曖昧に畳まれていたものを明示する
(`keycloak-ci` / `web-ci` は従来 `check` に紛れていたものを必須として明示する)。

```text
削除: test, lint, check, scan, platform-plan, tasks-plan, nativeCompile
追加: webapi-ci, webapi-lint-conventions, webapi-native-build,
      keycloak-ci, web-ci, openapi-lint, markdown-lint,
      terraform-lint, terraform-plan-platform, terraform-plan-tasks,
      terraform-security-scan
```

## 4. 理由(Rationale)

- 必須チェック名を全リポジトリで一意にすることで、branch protection が「どのワークフローを
  ゲートしているか」を確定でき、別ドメインの成功で必須が満たされる潜在バグを解消する。
- stem を見れば中身(何を gate するか)と出所(どのワークフロー)が直結し、認識コストが下がる。
- subject を実モジュール名に固定することで、`app` の曖昧さ(webapi / web どちらも app)を排除し、
  gradle サブプロジェクト名(`:webapi` / `:keycloak`)や `web/` ディレクトリと一致させる。
- 抽象ドメイン層(iac / spec / docs)を作らないことで、単一ツール前提の冗長な階層を避ける。
  捨てた利点として、将来 IaC ツールが複数になった場合のグルーピングは失うが、その予定は無い。
- kind 語を「失敗の意味」で定義することで、`ci`(束)と単一 kind を一貫して区別できる。

## 5. 影響(Consequences)

### 良い影響(Positive)

- branch protection の必須チェックが一意・自己説明的になり、レビュー時に何が要求されているか一目で分かる。
- 表示名 `<Subject>: <Kind>` により Actions sidebar が subject ごとにまとまる。
- `changes` ゲート job が `<workflow-stem>-changes` になり、run 画面でどのワークフロー由来か判別できる。

### 悪い影響・制約(Negative)

- job 名の変更はステータスチェック名の変更であり、**リネーム PR の merge と同じ作業窓で
  branch protection の必須チェックを旧 → 新へ差し替えないと、以後の PR が「報告されないチェック」
  待ちで固着する**(auto-merge bot も同じゲートで待つ)。
- `web-ci` と `webapi-ci` は綴りが近接するが、これは web / webapi 両モジュールが実在する以上の
  正直な区別であり許容する。

### 既存ドキュメント・規約への波及

- 本 ADR が **CI 命名規約の SSOT** となる。`docs/specs/設計規約.md` 等への規約節追加は行わない
  (必要なら本 ADR への pointer のみ)。
- `.github/workflows/` 全ワークフローのファイル名 / 表示名 / job 名を本表に合わせて変更する。
- branch protection の必須チェックリストを §3.3 のとおり差し替える(GitHub 設定の手動操作)。

## 6. 実装メモ(Implementation Notes)

移行は 2 ステップで行う。

1. **リネーム PR**: ファイル名・表示名・job 名・`changes` job 名を §3.2 に従って一括変更。
   リネーム漏れ防止のため、commit 前に旧 job 名・旧表示名を全文 grep で確認する。
   `workflow_run` トリガや bot 側からの名前ハードコード参照は存在しないことを確認済み
   (`github.workflow` は各ワークフローの `concurrency.group` 自己参照のみ)。
2. **branch protection 差し替え**: PR merge と同じ作業窓で §3.3 の必須チェックを旧 → 新へ差し替える。
   bot は `gh pr merge --auto` で GitHub ゲートに追従するため追加対応は不要。

PR 分割の方針: リネーム(手順 1)を 1 PR、branch protection 差し替え(手順 2)は GitHub 設定操作の
ため人手で実施し、tracker のチェックリストで完了を記録する。

## 7. 参考リンク(References)

- 現行ワークフロー: `.github/workflows/`
- Cowork 作業規約: `CLAUDE.md`(Cowork 専用)
- 関連 ADR: ADR-0025(フロントエンド品質ゲート、`web-ci` に lint/検証が追加される前提)
