# ADR-0032: エージェント駆動の手動/探索 E2E 検証レイヤー(dev/local)— ADR-0023 の補完

- Status: Accepted
- Date: 2026-06-18
- Deciders: win2cot, 開発チーム
- Tags: testing, e2e, frontend, tooling, quality
- 補完対象: ADR-0023(最小 E2E テスト基盤)
- 改訂: 2026-06-18 — #538 実装議論を受け、実行スタック構成(stateful 依存=compose / app=ホスト)と静的配信の CSP ヘッダ local/CI 再現を §2/§3/§6 に追記(中核決定は不変)。作成当日・影響軽微のため追補でなく直接改訂。

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

- **local(#538 の full-stack)+ dev(採用)**: ADR-0023 が CI で dev を却下したのは「決定論 spec を共有環境で回す」文脈であり、探索検証や本番投入前の実環境確認で dev を使うのは別問題として許容する。

### アーキテクチャ: 頭脳とブラウザ係の接続

本レイヤーは 2 層で捉える。**頭脳 = Claude Code**(ファイルシステム・シェル・git/gh・MCP を持ち、repo / ローカルクローン読取・compose 起動・ビルド・ソース突き合わせを担う)。**ブラウザ係**は実際にブラウザを操作・観測する部品で、頭脳との接続方式が 2 通りある。

```text
頭脳: Claude Code(repo/ローカルクローン読取・git/gh・compose 起動・ビルド)
  │
  ├─(a) MCP プロトコル ──→ Playwright MCP / Chrome DevTools MCP ──→ 自動化用ブラウザ   ← 採用
  │
  └─(b) nativeMessaging ─→ Claude in Chrome 拡張 ──────────────→ 実Chrome(ログイン中)  ← 不採用(将来再評価)
```

ブラウザ係はどの方式でも「ブラウザ内(タブ / DOM / CDP の console・network)」だけを担当する。repo / ローカル / ビルドは常に頭脳(Claude Code)側であり、ブラウザ係はそれを代替しない。

### 実現手段(ツール)

- **Playwright MCP(Microsoft)— 採用(操作・通し検証)**: MCP サーバ。アクセシビリティツリー基盤、Chrome/Firefox/WebKit/Edge 対応。ADR-0023 と同一 Playwright エンジンのため、探索で見つけた問題を #535 配下の決定論 spec に昇格できる。
- **Chrome DevTools MCP(Google)— 採用(観測)**: MCP サーバ。CDP。`list_console_messages`(ソースマップ付き)/`list_network_requests`/perf trace で console・CSP 違反・失敗リクエストの観測が最も厚い。Chrome 専用。
- **Claude in Chrome(Anthropic)— 不採用(将来再評価)**: Chrome 拡張が実ブラウザを `debugger`(CDP)+`scripting` で制御し、頭脳とは nativeMessaging で連携。**拡張単体ではブラウザ内しか扱えず、ローカルクローン読取・git/gh・gradle 実行はできない**(GitHub も Web UI を見るだけ)。β・Chrome 専用・hosted/tier 依存のため現時点見送り、GA・安定後に再評価。

両 MCP はオープンプロトコルでモデル / クライアント非依存。win2cot の Claude Code に MCP サーバとして導入する。

### 実行スタックの構成(execution topology)

検証 / E2E で起動するスタックの分割線:

- **stateful・バージョン固定したい依存(mysql / keycloak)= compose**。
- **app(webapi / web)= ホスト直起動**(webapi は `bootRun` / `java -jar`、web はヘッダ注入 serve)。**webapi 用 Dockerfile は作らない(ADR-0018 準拠: 本番イメージは `bootBuildImage` の GraalVM native、Dockerfile 不作成)**。
- 理由: webapi/web は stateless で Docker 固有の利点が無く、CI ランナー(clean VM)自体が hermetic。compose に webapi を入れると ADR-0018 と二重のコンテナ化になる。issuer URL もホスト起動なら単一 `localhost` に揃い単純。
- **ADR-0023 §6 の「compose に webapi+web を入れる」実装スケッチを更新**する(ADR-0023 は確立・被参照のため不変、本 ADR に記録)。ADR-0023 の中核決定(Playwright + hermetic full stack + Sprint 3 scaffold)は不変。
- 制約(native パリティ): E2E は JVM(`java -jar`)で回り、本番の GraalVM native(ADR-0008/0018/0021)固有の退行は拾えない。native 退行検知が要る場合は別途 Paketo native イメージを compose に載せる重い job を追加(本レイヤーの既定スコープ外、既知の制約)。

### 静的配信のセキュリティヘッダ(CSP)

- **local / CI とも、本番(#529 CloudFront Response Headers Policy、ADR-0022)相当の CSP / セキュリティヘッダを静的配信に注入する(採用)**。素の `http-server` / `python -m http.server` はヘッダを付けないため**不採用**。
- 採用: **ヘッダを注入できる軽量 serve**(`web/package.json` の `serve` スクリプト = ヘッダ付与する小さな Node サーバ、Node 統一・cross-platform)。RHP のヘッダ集合は単一正本から prod / local / CI に展開し、ドリフトを防ぐ。
- 理由: 本レイヤーと #541(ADR-0022(b) CSP 違反0 フィクスチャ)が、静的ページの CSP を本番同等で検証できるようにする。#538 はこの方式で web を配信する。

## 3. 決定(Decision)

検証の**頭脳は Claude Code**(repo / ローカル / ビルドを握る)とし、その下位補完レイヤーとしてブラウザ検証を ADR-0023 に追加する。対象 = local + dev。CI には載せない(決定論ゲートは Playwright=ADR-0023)。

ブラウザ係の実現手段は **MCP 接続で併用**:

- 操作・通し検証 = **Playwright MCP** / 観測 = **Chrome DevTools MCP**。
- **Claude in Chrome は現時点不採用**(β・Chrome 専用・hosted/tier 依存・単体で repo 不可)。GA 後再評価。

実行形態:

- 起動スタック = **stateful 依存(mysql/keycloak)= compose + app(webapi/web)= ホスト**。webapi Dockerfile 不作成(ADR-0018)。ADR-0023 §6 の compose 同梱スケッチを上書き(中核決定は不変)。
- 静的配信は **#529 RHP 相当の CSP/セキュリティヘッダを注入する serve** で local / CI とも本番同等(素の http-server 不可)。
- readiness gating: keycloak(healthcheck)・webapi(`/actuator/health`)・web の起動完了を待ってから検証 / Playwright を実行。

成果物: 検証 runbook(対象 URL・Keycloak ログイン手順・代表フロー・観測項目・起動スタック手順)+ 両 MCP を Claude Code に入れる前提の文書化。棲み分け: 決定論ゲート=Playwright(ADR-0023)/ 探索・実環境確認=本レイヤー。

## 4. 理由(Rationale)

- **#530 の再発防止**: 手動依存だった通し検証をエージェントで反復可能にし、「動く前提」を検証できる土台を作る。
- **ADR-0023 との一貫性**: 操作を Playwright MCP に寄せ、探索結果を同一エンジンの決定論 spec に昇格でき、二重投資を避ける。
- **観測の厚み**: Chrome DevTools MCP の CDP 観測(ソースマップ付き console・network・CSP)が #663 の警告撲滅に直結。
- **実行形態の単純化と規約整合**: app をホストに出すと issuer が単一 localhost に揃い、ADR-0018 の no-Dockerfile とも整合。CI ランナーが clean VM のため hermetic 性は保たれる。
- **検証の代表性**: 静的配信に本番 RHP 相当の CSP を注入することで、#541 の CSP 検証が local/CI でも本番同等に効く。
- **可搬性と層の分離**: ブラウザ係をオープンな MCP 部品にし、頭脳と疎結合。nativeMessaging 一体型(Claude in Chrome)はベンダー・tier・Chrome に固定され単体で repo も読めないため見送り。

## 5. 影響(Consequences)

### 良い影響(Positive)

- local/dev で実装直後に通し確認・警告撲滅ができ、退行と「そもそも動かない」を早期に発見。
- 探索→決定論 spec(#535)への昇格パス。#530 型検証を反復可能化。CSP を local/CI で本番同等に検証できる。

### 悪い影響・制約(Negative)

- 非決定的でゲートにできない(意図的)。runbook と MCP 設定の鮮度維持が要る。
- dev を対象に含むため、データ汚染・並行操作の注意は残る(探索用途に限定)。
- 静的配信のヘッダ集合を prod(#529 RHP)と local/CI で同期し続ける必要(単一正本から展開)。
- native パリティは E2E 非対象(JVM 実行)。native 退行は別 job。
- 2 つの MCP を Claude Code に導入・維持するコスト。
- 将来 Claude in Chrome を採用してもブラウザ係に限られ、repo/ローカル読取・ビルドは引き続き Claude Code(拡張単体では代替不可)。

### 既存ドキュメント・規約への波及

- #538(compose は mysql/keycloak のみ、app はホスト、web はヘッダ注入 serve)。
- ADR-0023 §6 の実装スケッチを本 ADR で上書き(ADR-0023 本文は不変)。
- 検証 runbook を docs に追加(配置は実装 Issue #662 で確定)。

## 6. 実装メモ(Implementation Notes)

### 頭脳の所在(Cowork ではなく Claude Code)

- 頭脳は **Claude Code(WSL 等の実環境)に限定**。Cowork は設計・ADR 起草・起票=「実装の手前」担当で、検証ブレインには使わない。
- 理由(Cowork sandbox の制約): 別の揮発性 Linux で network allowlist 制 → テスト対象と非同居・非到達 / gh・PAT 不在 / sandbox で git 実行不可(host `.git/index.lock` 破壊)/ host 書込みが末尾欠落しうる。
- Cowork のブラウザ手段は Claude in Chrome / computer-use で本 ADR の MCP 構成とは別系統。Cowork は「目視する軽いスモーク」が上限。

### 着手(#662)

- Claude Code に Playwright MCP / Chrome DevTools MCP を設定、環境前提・起動スタック手順の文書化、Keycloak ログイン手順、代表フロー、観測項目の列挙。
- 起動スタック: `docker compose up -d`(mysql+keycloak)→ webapi をホスト起動 → web をヘッダ注入 serve → readiness 待ち → 検証。
- 最初の適用 = Sprint 2.5 のブラウザ警告/エラー撲滅(#663)。
- 決定論化すべきケースは #540/#541(Playwright spec)へ backfill。

### 環境前提(実行構成)

- ベース技術: 両 MCP は **Node.js(npx)で Docker ではない**。app(webapi/web)もホスト直起動で Docker 化しない。compose は **mysql/keycloak のみ**。web はヘッダ注入 serve(#529 RHP 相当)。
- 群分け: テスター群(Claude Code + MCP + ブラウザ)/ テスト対象群(web・webapi・keycloak・mysql)。**各群を単一 OS に閉じ、MCP サーバとブラウザは必ず同一 OS**。
- サポートする実行構成(tester → target):
  - **WSL2 → WSL2(既定)**: クロス無し・localhost 自明・prod 近似。headed は WSLg + `DISPLAY`/`WAYLAND_DISPLAY`/`XDG_RUNTIME_DIR` 注入。
  - **Windows → Windows**: localhost 自明・headed ネイティブ。
  - **Windows → WSL2**: app=WSL + ブラウザ=Windows headed(WSLg 不要)。Windows→WSL2 の localhost forwarding 前提(WSL 側 `0.0.0.0` bind・ポート publish)。
- **非サポート: WSL2 → Windows**(localhost が Windows ホストに届かず host IP/ミラーモード要・keycloak issuer 割れ)。
- クロスは「ブラウザ → テスト対象の HTTP」のみ。3 構成は localhost が一意で keycloak issuer/redirect も一貫。
- readiness gating: keycloak healthcheck・webapi `/actuator/health`・web の起動完了を待ってから検証/Playwright を撃つ。

## 7. 参考リンク(References)

- ADR-0023 — 最小 E2E テスト基盤(本 ADR の被補完元)
- ADR-0022 — セキュリティレスポンスヘッダー(CSP)/ #529 CloudFront Response Headers Policy
- ADR-0018 — bootBuildImage(Dockerfile 不作成)/ ADR-0008・0021 — GraalVM native・JDK
- Issue #530 — CSP enforce 検証(手動依存の反省)
- Issue #535 / #538 / #541 — E2E エポック / compose full-stack / CSP フィクスチャ
- Playwright MCP(Microsoft, Node)/ Chrome DevTools MCP(Google, Node+Puppeteer)/ Claude in Chrome(Anthropic, β)
