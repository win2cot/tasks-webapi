# ADR-0032: エージェント駆動の手動/探索 E2E 検証レイヤー(dev/local)— ADR-0023 の補完

- Status: Accepted
- Date: 2026-06-18
- Deciders: win2cot, 開発チーム
- Tags: testing, e2e, frontend, tooling, quality
- 補完対象: ADR-0023(最小 E2E テスト基盤)

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

ADR-0023 は hermetic compose 上の Playwright(TypeScript)による決定論的・自動 E2E を CI ゲートとして定めた。同 ADR は CI E2E の実行先としてデプロイ済み dev を、共有環境・データ汚染・フレーキーを理由に却下している。

別の課題が #530(CSP enforce 検証)で顕在化した。enforce 切替前の手動パス(Keycloak ログイン/トークン更新・タスク CRUD・モーダル/ドロップダウン=Popper・カレンダー)が win2cot の手作業に依存し、結果として検証されないまま close された。さらに `/api/auth/me` 未実装・RDS IAM 未配線という「そもそも通しで動かない」状態を、機能 Issue の単体完了基準が検知できなかった。

実装直後に local/dev の稼働インスタンスを素早く・探索的に触り、「最低限通しで動く」「ブラウザに警告/エラーが出ていない」を確認する手段が無い。これは Playwright の事前に書ききる決定論 spec とは目的・モダリティが異なる(探索的・対話的・ライブ環境・アサーション後付け)。

## 2. 検討した選択肢(Options Considered)

### レイヤーの位置づけ

- **ADR-0023 の補完レイヤーとして追加(採用)**: 決定論 CI ゲートは Playwright(ADR-0023)に残し、その手前に探索・実環境確認レイヤーを置く。
- ADR-0023 に一本化: 探索・ライブ dev 確認という別モダリティを賄えない。不採用。

### 対象環境

- **local(#538 の full-stack compose)+ dev(採用)**: ADR-0023 が CI で dev を却下したのは「決定論 spec を共有環境で回す」文脈であり、探索検証や本番投入前の実環境確認で dev を使うのは別問題として許容する。

### アーキテクチャ: 頭脳とブラウザ係の接続

本レイヤーは 2 層で捉える。**頭脳 = Claude Code**(ファイルシステム・シェル・git/gh・MCP を持ち、repo / ローカルクローン読取・compose 起動・ビルド・ソース突き合わせを担う)。**ブラウザ係**は実際にブラウザを操作・観測する部品で、頭脳との接続方式が 2 通りある。

```text
頭脳: Claude Code(repo/ローカルクローン読取・git/gh・compose 起動・ビルド)
  │
  ├─(a) MCP プロトコル ──→ Playwright MCP / Chrome DevTools MCP ──→ 自動化用ブラウザ   ← 採用
  │
  └─(b) nativeMessaging ─→ Claude in Chrome 拡張 ──────────────→ 実Chrome(ログイン中)  ← 不採用(将来再評価)
```

ブラウザ係はどの方式でも「ブラウザ内(タブ / DOM / CDP の console・network)」だけを担当する。repo / ローカル / ビルドは常に頭脳(Claude Code)側であり、ブラウザ係はそれを代替しない。接続方式の差は (a) オープンな MCP プロトコルで部品として呼ぶか、(b) Anthropic 製拡張を nativeMessaging で連携するか、である。

### 実現手段(ツール)

- **Playwright MCP(Microsoft)— 採用(操作・通し検証)**: MCP サーバ。アクセシビリティツリー基盤で意味論的・トークン効率、Chrome/Firefox/WebKit/Edge 対応。ADR-0023 と同一 Playwright エンジンのため、探索で見つけた問題を #535 配下の決定論 spec に昇格できる。Claude Code に MCP として接続。
- **Chrome DevTools MCP(Google)— 採用(観測)**: MCP サーバ。Chrome DevTools Protocol。`list_console_messages`(ソースマップ付き)/`list_network_requests`/perf trace で console・CSP 違反・失敗リクエストの観測が最も厚い。Chrome 専用。Claude Code に MCP として接続。
- **Claude in Chrome(Anthropic)— 不採用(将来再評価)**: Chrome 拡張が実ブラウザを `debugger`(CDP)+`scripting` で制御し、頭脳とは nativeMessaging で連携する。モデルは Anthropic ホスト(tier 依存)。**拡張単体ではブラウザ内(タブ / DOM / CDP の console・network)しか扱えず、ローカルクローンの読取・git/gh・gradle 実行はできない。GitHub も Web UI をブラウザで見るだけで、API でも git でもない**。したがって repo / ローカルを読む頭脳にはなり得ず、あくまで Claude Code の「ブラウザ係」として連携する位置づけ。β・Chrome 専用・非決定的・hosted/tier 依存のため恒常運用の手段としては現時点で見送り、GA・安定後に再評価する。

両 MCP はオープンプロトコルでモデル / クライアント非依存。win2cot の Claude Code に MCP サーバとして導入する。

## 3. 決定(Decision)

検証の**頭脳は Claude Code**(repo / ローカル / ビルドを握る)とし、その下位補完レイヤーとしてブラウザ検証を ADR-0023 に追加する。対象 = local(#538 の full-stack compose)+ dev。CI には載せない(決定論ゲートは Playwright=ADR-0023)。

ブラウザ係の実現手段は **MCP 接続で併用**とする:

- **操作・通し検証 = Playwright MCP**(ログイン → `/api/auth/me` → タスク CRUD のハッピーパス操作。発見した決定論ケースは #535 の spec へ昇格)
- **観測 = Chrome DevTools MCP**(console error/warning・CSP 違反・失敗リクエスト・perf を構造化観測。#530 型検証と #663 の警告撲滅に使用)
- **Claude in Chrome は現時点不採用**(nativeMessaging 接続の別系統。β・Chrome 専用・hosted/tier 依存・非決定、かつ単体では repo/ローカルを読めない)。GA・安定後に再評価。

成果物: 検証 runbook(対象 URL・Keycloak ログイン手順・代表フロー・観測項目)+ 両 MCP を Claude Code に入れる前提の文書化。棲み分け: 決定論ゲート=Playwright(ADR-0023)/ 探索・実環境確認=本レイヤー。

## 4. 理由(Rationale)

- **#530 の再発防止**: 手動依存だった通し検証をエージェントで反復可能にし、「動く前提」を検証できる土台を作る。
- **ADR-0023 との一貫性**: 操作を Playwright MCP に寄せることで、探索結果を同一エンジンの決定論 spec に昇格でき、二重投資を避ける。
- **観測の厚み**: Chrome DevTools MCP の CDP 観測(ソースマップ付き console・network・CSP)が #663 の警告撲滅に直結する。
- **可搬性と層の分離**: ブラウザ係をオープンな MCP 部品にすることで、頭脳(Claude Code)と疎結合になり、モデル/クライアント/単一ブラウザに固定されない。nativeMessaging 一体型(Claude in Chrome)は手軽だがベンダー・tier・Chrome に固定され、単体では repo/ローカルも読めないため見送った。
- **機械検知優先の原則と整合**: 恒久ゲートは Playwright に集約しつつ、その手前の探索層を明示する。

## 5. 影響(Consequences)

### 良い影響(Positive)

- local/dev で実装直後に通し確認・警告撲滅ができ、退行と「そもそも動かない」を早期に発見できる。
- 探索→決定論 spec(#535)への昇格パスができる。#530 型検証を反復可能化できる。

### 悪い影響・制約(Negative)

- 非決定的でゲートにできない(意図的)。runbook と MCP 設定の鮮度維持が要る。
- dev を対象に含むため、データ汚染・並行操作の注意は残る(探索用途に限定)。
- 2 つの MCP を Claude Code に導入・維持するコスト。
- 将来 Claude in Chrome を採用する場合も、それはブラウザ係に限られ、repo/ローカル読取・ビルドは引き続き Claude Code が担う(拡張単体では代替不可)。

### 既存ドキュメント・規約への波及

- #538(compose full-stack)が前提。
- 検証 runbook を docs に追加(配置は実装 Issue #662 で確定)。

## 6. 実装メモ(Implementation Notes)

### 頭脳の所在(Cowork ではなく Claude Code)

- 頭脳は **Claude Code(WSL 等の実環境)に限定**する。Cowork は設計・ADR 起草・起票オーケストレーション=「実装の手前」担当であり、本レイヤーの検証ブレインには使わない(repo `.claude/CLAUDE.md` / Cowork `CLAUDE.md` の役割分担と一致)。
- 理由(Cowork sandbox の制約): あなたの WSL とは別の揮発性 Linux で network allowlist 制のため、テスト対象 compose と非同居・非到達 / gh・PAT 不在 / sandbox で git 実行不可(host `.git/index.lock` を破壊)/ host ファイル書込みが末尾欠落しうる。これらにより「頭脳＋ブラウザ係＋テスト対象を同一 OS」という本レイヤーの前提を満たせない。
- Cowork のブラウザ手段は Claude in Chrome / computer-use であり、本 ADR の MCP 構成とは別系統。Cowork では「起動中アプリを目視する軽いスモーク」が上限で、build → run → console↔ソース突き合わせ → 修正の本ループは回さない。

### 着手(#662)

- win2cot の Claude Code に Playwright MCP と Chrome DevTools MCP を設定、環境前提の文書化、Keycloak ログイン手順、代表フロー定義、観測項目の列挙。
- 観測項目: console error/warning、uncaught、CSP 違反(Report-Only/enforce)、失敗リクエスト(4xx/5xx)。
- 最初の適用 = Sprint 2.5 のブラウザ警告/エラー撲滅(#663)。
- 決定論化すべきケースは #540/#541(Playwright spec)へ backfill。
- Claude in Chrome は再評価用に本 ADR に記録のみ(導入しない)。

### 環境前提(実行構成)

- ベース技術: 両 MCP は **Node.js(npx 起動)で Docker ではない**。Playwright MCP=Node 18+、Chromium/Firefox/WebKit を同梱(Linux は `playwright install --with-deps`)。Chrome DevTools MCP=Node 20.19+、Puppeteer で別途導入した実 Chrome を CDP 駆動(Chrome 専用)。Docker はテスト対象(#538 compose)側の話で、MCP のブラウザとは別レイヤー。
- 群分け: テスター群(Claude Code + MCP + ブラウザ)/ テスト対象群(web・webapi・keycloak・mysql)。**各群を単一 OS に閉じ、MCP サーバとブラウザは必ず同一 OS** に置く。
- サポートする実行構成(tester → target):
  - **WSL2 → WSL2(既定)**: 全部 WSL 内、クロス無し・localhost 自明・prod 近似。ヘッドレス自動検証はこれで過不足なし。headed は WSLg + `DISPLAY`/`WAYLAND_DISPLAY`/`XDG_RUNTIME_DIR` 注入。
  - **Windows → Windows**: 全部 Windows、localhost 自明・headed ネイティブ。
  - **Windows → WSL2**: app=WSL/Docker、ブラウザ=Windows headed(WSLg 不要)。Windows→WSL2 の localhost forwarding 前提(WSL 側 `0.0.0.0` bind・ポート publish)。
- **非サポート: WSL2 → Windows**(localhost が Windows ホストに届かず host IP/ミラーモードが要る上、keycloak issuer が割れるため除外)。
- クロスするのは「ブラウザ → テスト対象の HTTP」のみ(webapi↔keycloak↔mysql は対象群内で完結)。上記3構成は localhost が一意に効くため keycloak issuer/redirect は localhost ベースで一貫する。

## 7. 参考リンク(References)

- ADR-0023 — 最小 E2E テスト基盤(本 ADR の被補完元)
- ADR-0022 — セキュリティレスポンスヘッダー(CSP)
- Issue #530 — CSP enforce 検証(手動依存の反省)
- Issue #535 / #538 — E2E エポック / compose full-stack 化
- Playwright MCP(Microsoft, Node)/ Chrome DevTools MCP(Google, Node+Puppeteer)/ Claude in Chrome(Anthropic, β)
