# Runbook: エージェント駆動ブラウザ検証手順 (ADR-0032)

最終更新: 2026-06-19 | Issue #662 / ADR-0032

## 概要

Claude Code が **Playwright MCP** を使い、local / dev の稼働インスタンスをブラウザ経由で
探索的に検証する手順書。

CI の決定論ゲート(ADR-0023 / Playwright spec)とは**目的・モダリティが異なる**:

| 項目 | 本レイヤー (ADR-0032) | CI ゲート (ADR-0023) |
|---|---|---|
| 目的 | 実装直後の「最低限通しで動く」確認・警告撲滅 | 決定論的リグレッション防止 |
| 実行者 | Claude Code (エージェント駆動) | GitHub Actions (自動) |
| 対象環境 | local / dev | hermetic compose (CI) |
| 非決定性 | 許容(意図的) | 禁止 |
| CI 搭載 | **なし** | あり |

代表フロー: **ログイン → /api/auth/me → タスク CRUD → モーダル/ドロップダウン(Popper) → カレンダー**

---

## 前提条件

### 必須ツール

| ツール | 確認コマンド |
|---|---|
| Node.js ≥ 24 | `node --version` |
| Docker Desktop (WSL2 integration) | `docker compose version` |

### MCP サーバー設定

Playwright MCP が Claude Code に登録済みであること。
設定方法は「[MCP 設定](#mcp-設定)」セクションを参照。

### フルスタック起動

検証前に local フルスタックが起動済みであること。
手順は [fullstack-startup.md](fullstack-startup.md) を参照。

起動確認コマンド:

```bash
# keycloak / mysql
docker compose -f docker-compose.local.yml ps
# webapi
curl -s http://localhost:8080/actuator/health
# web
curl -sf http://localhost:5500/
```

期待状態: keycloak・mysql が `Up (healthy)`・webapi が `{"status":"UP"}`・web が HTTP 200。

---

## 対象 URL

| サービス | URL | 用途 |
|---|---|---|
| web フロント | `http://localhost:5500/` | ブラウザ操作の起点 |
| webapi | `http://localhost:8080/` | REST API |
| Keycloak Admin | `http://localhost:18080/` | realm / ユーザー管理 |
| webapi health | `http://localhost:8080/actuator/health` | readiness 確認 |

---

## テストユーザー

| ユーザー名 | パスワード | ロール | 用途 |
|---|---|---|---|
| `tenant1-member1@example.com` | `password` | `MEMBER` | 代表フロー検証(主) |
| `tenant1-admin@example.com` | `password` | `TENANT_ADMIN` | テナント管理操作確認 |
| `admin@example.com` | `admin` | `APP_ADMIN` | SaaS 管理者確認 |

---

## MCP 設定

### Playwright MCP (Microsoft) — 操作・通し検証・観測

#### ステップ 1: Chromium ブラウザのインストール

Playwright MCP はブラウザを別途インストールする必要がある。**一人一回だけ実行**すれば以降は不要。

```bash
npx playwright install chromium
# ~/.cache/ms-playwright/ にキャッシュされる
```

#### ステップ 2: MCP サーバー登録

スコープ別の選択肢:

| スコープ | コマンド | 保存先 | 他の開発者への影響 |
|---|---|---|---|
| **ユーザー**(個人) | `-s user` | `~/.claude.json` | 引き継がれない。各自が同じコマンドを実行する必要あり |
| **プロジェクト** | `-s project` | `.mcp.json`(コミット可) | クローン後に Claude Code が自動認識・承認プロンプトを出す。手動登録不要 |

プロジェクトスコープで登録済み(`.mcp.json` がリポジトリに含まれる):

```bash
# 新規クローン後など、未登録の場合のみ実行
claude mcp add playwright -s project -- npx @playwright/mcp@latest
```

`.mcp.json` がリポジトリに存在する場合、Claude Code を開いた際に承認プロンプトが表示される。
承認すると `~/.claude/settings.json` の `enabledMcpjsonServers` に `"playwright"` が追加される。

> **注意**: `.mcp.json` が共有されていても Chromium のインストール(ステップ 1)は各開発者が各自実行する必要がある。

登録確認:

```bash
claude mcp list
# playwright: npx @playwright/mcp@latest - ✔ Connected
```

デフォルト: ヘッドレス。ブラウザウィンドウを表示したい場合(WSLg 起動済み前提):

```bash
# --headed で再登録(WSLg + DISPLAY 環境要)
claude mcp remove playwright -s user
claude mcp add playwright -s user -- npx @playwright/mcp@latest --headed
```

WSLg 起動確認: `echo $DISPLAY`(`:0` 等が返ること)。

### Chrome DevTools MCP (Google) — CDP 観測

> **現時点での状況**: ADR-0032 が採用した Chrome DevTools MCP(Google) は
> 現在 npm 公開パッケージとして未リリースのため、別手段で代替する。

代替として Playwright MCP のコンソール・ネットワーク観測ツール
(`browser_console_messages` / `browser_network_requests`、バージョンによって名称が変わる場合あり)
で console・CSP 違反・失敗リクエストを観測する。

Chrome DevTools MCP が npm 公開された時点で:

```bash
claude mcp add chrome-devtools -s user -- npx <公開パッケージ名>@latest
```

を実行して追加する。公開状況は ADR-0032 を参照。

---

## 観測項目

代表フロー中に以下を収集・記録する。

### Playwright MCP で収集

```text
browser_console_messages (または同等ツール)
  → console.error / console.warn / uncaught 例外・スタックトレース

browser_network_requests (または同等ツール)
  → 失敗リクエスト(4xx/5xx)・リクエスト内容・レスポンスヘッダ
```

### CSP 違反

Chrome は `securitypolicyviolation` イベントを console に `[Report Only]` プレフィックス付きで出力する。
Playwright MCP のコンソールログ収集で取得し、違反ディレクティブ・ソース URL・行番号を記録する。

### 観測チェックリスト

| 分類 | 確認内容 | 目標 |
|---|---|---|
| console.error | JS ランタイムエラー | 0 件 |
| console.warn | 非推奨 API・設定ミス | 0 件(許容リスト除く) |
| uncaught 例外 | Promise rejection・uncaught Error | 0 件 |
| CSP 違反 | `Content-Security-Policy-Report-Only` 違反 | 0 件 |
| 失敗リクエスト | 4xx / 5xx | 0 件 |
| Deprecation | ブラウザ非推奨警告 | 0 件(許容リスト除く) |
| Mixed content | HTTP over HTTPS | 0 件 |

---

## 代表検証フロー

各フロー実行前に Playwright MCP でブラウザを起動し、コンソール・ネットワークの観測を開始する。
フロー完了後にコンソールログ・ネットワークリクエストを収集して観測項目を確認する。

### フロー 1: ログイン

**目的**: Keycloak OIDC フロー・トークン取得・セッション確立を確認する。

```text
1. Playwright MCP で http://localhost:5500/ を開く
2. Keycloak ログイン画面へリダイレクトされることを確認
   (リダイレクト先: http://localhost:18080/realms/tasks/protocol/openid-connect/auth)
3. ユーザー名: tenant1-member1@example.com / パスワード: password でログイン
4. http://localhost:5500/ に戻り、ナビバーにユーザー名が表示されることを確認
5. コンソールログ確認 → エラー・警告 0 件
6. ネットワーク確認 → /realms/tasks/protocol/openid-connect/token が 200
```

**合格基準**: リダイレクト成功・ログイン後のトップ画面表示・console error 0 件。

### フロー 2: /api/auth/me

**目的**: ログインユーザー情報取得と所属テナント一覧の表示を確認する。

```text
1. ログイン済み状態で GET /api/auth/me が呼ばれることをネットワークログで確認
2. レスポンス例: { "sub": "...", "email": "tenant1-member1@example.com", "tenants": [...] }
3. テナント選択 UI が表示されることを確認
4. /api/auth/me が HTTP 200 を返すこと・4xx/5xx でないこと
```

**合格基準**: /api/auth/me が 200・レスポンスに `tenants` 配列が含まれる。

### フロー 3: タスク一覧

**目的**: テナント選択後のタスク一覧取得・表示を確認する。

```text
1. テナントを選択してタスク画面に遷移
2. GET /api/tasks が呼ばれることを確認 (X-Tenant-Id ヘッダが付与されること)
3. タスク一覧が表示されること(0 件でも画面が壊れないこと)
4. console error 0 件・失敗リクエスト 0 件
```

**合格基準**: タスク一覧画面表示・API が 200。

### フロー 4: タスク作成(モーダル)

**目的**: タスク作成モーダルの表示・送信・レスポンス処理を確認する。

```text
1. 「新規タスク」ボタンをクリック
2. モーダルが開くことを確認(Bootstrap modal)
3. タイトル・説明を入力して送信
4. POST /api/tasks が 201 Created を返すことを確認
5. モーダルが閉じて一覧にタスクが追加されることを確認
6. console error 0 件・CSP 違反 0 件
```

**合格基準**: モーダル開閉・POST 201・一覧更新。

### フロー 5: タスク編集(ドロップダウン / Popper)

**目的**: ドロップダウン(Bootstrap + Popper.js)の動作・z-index・overflow クリッピングを確認する。

```text
1. タスク行のアクションメニュー(︙ または「編集」ボタン)をクリック
2. ドロップダウンが正しい位置に表示されることを確認(Popper 配置)
3. 「編集」を選択してタスク編集モーダルを開く
4. フィールドを変更して PUT /api/tasks/{id} を送信
5. Popper.js 警告 0 件・console error 0 件
```

**合格基準**: ドロップダウン正常表示・PUT 200・一覧更新。

### フロー 6: タスク削除

**目的**: 論理削除フローと UI 更新を確認する。

```text
1. タスク行の「削除」を選択
2. 確認ダイアログ表示(ブラウザ confirm またはモーダル)
3. 確認後 DELETE /api/tasks/{id} が呼ばれること
4. 一覧からタスクが消えることを確認
5. console error 0 件
```

**合格基準**: 削除確認・DELETE 204・一覧から消去。

### フロー 7: カレンダー

**目的**: カレンダービューの描画・スクロール・日付クリック動作を確認する。

```text
1. カレンダービューに切り替え(ナビまたはタブ)
2. 当月タスクがカレンダー上に表示されること
3. 月送りボタンで前後に遷移できることを確認
4. 日付セルをクリックしてタスク絞り込みまたは作成モーダルが開くことを確認
5. console error 0 件・描画崩れ 0 件
```

**合格基準**: カレンダー表示・月送り動作・日付クリック動作。

---

## #530 型 CSP/UI フロー検証

Issue #530 で手動依存だった「CSP enforce 切替前の通し検証」を再現可能な手順として定式化する。

### 手順

```text
1. フルスタック起動(fullstack-startup.md §1〜§4)
   → web は CSP-Report-Only モードで配信される(serve.mjs)

2. Playwright MCP でブラウザを起動し http://localhost:5500/ に接続

3. 代表フロー 1〜7 を実行しながら観測:
   - コンソールログ → "[Report Only]" で始まる CSP 違反を抽出
   - ネットワークログ → 失敗リクエストを確認
   - securitypolicyviolation のディレクティブ・ソース URL を記録

4. 違反 0 件を確認してから enforce への切替えを検討
   (web/security-headers.json の reportOnly: false に変更)

5. enforce 切替後に同フローを再実行し、ページ表示・機能動作が維持されることを確認

6. 違反が残る場合:
   a. ディレクティブ追加が必要か判断(正当な origin の許可)
   b. インライン JS/CSS の排除が先か判断
   c. 修正後にフロー 1〜7 を再実行
```

---

## 結果の記録と昇格

### 問題発見時

| 分類 | 対応 |
|---|---|
| バグ・エラー | 原因を特定して修正 → フロー再実行で回帰確認 |
| CSP 違反 | #541 と連携しディレクティブ調整または inline 排除 |
| 再現性のある動作パターン | #535(E2E エポック)配下に Playwright spec として昇格(backfill) |
| 不可避な第三者ノイズ | 根拠付きで許容リストに記録(下記) |

### 許容リスト (allowlist)

不可避と判断した警告・エラーはこの runbook に追記する:

<!-- 許容リスト: 確認済みの無害なエントリを以下に追加する -->
<!-- 例: | Chrome 拡張 | Unchecked runtime.lastError ... | 拡張が注入するもので制御不可 | -->

| 発生源 | メッセージ(抜粋) | 許容理由 |
|---|---|---|
| (なし) | | |

---

## 棲み分け確認

```text
本レイヤー(ADR-0032)   → 探索的・実環境確認・CI に載せない
CI ゲート(ADR-0023)    → 決定論的 Playwright spec・GitHub Actions で自動実行
```

本レイヤーで見つけたパターンは ADR-0023 配下の spec (#535) に昇格して初めて CI ゲートになる。
昇格せずに本レイヤーのみで確認したものは、次の検証サイクルで再確認が必要。

---

## 関連ドキュメント

- [fullstack-startup.md](fullstack-startup.md) — フルスタック起動手順(本 runbook の前提)
- [ADR-0032](../adr/0032-agent-driven-verification-layer.md) — 本レイヤーの設計決定
- [ADR-0023](../adr/0023-minimal-e2e-harness.md) — CI E2E 基盤(決定論ゲート)
- [ADR-0022](../adr/0022-security-response-headers.md) — セキュリティレスポンスヘッダー(CSP)
- [web/security-headers.json](../../web/security-headers.json) — CSP/ヘッダ値の単一正本
- [web/serve.mjs](../../web/serve.mjs) — 静的配信サーバ実装
- [local-setup.md](local-setup.md) — ローカル開発環境セットアップ
- Issue #663 — ブラウザ警告・エラー撲滅(本レイヤーの最初の適用)
- Issue #541 — CSP 違反0 フィクスチャ(Playwright spec 化)
