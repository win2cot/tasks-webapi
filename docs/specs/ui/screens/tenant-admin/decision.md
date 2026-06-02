# S-13 / S-14 テナント管理 方向性決定(Issue #152)

Version 1.0 / 2026-06-02 / 親 Issue #152 / #149

## 0. 本書の位置付け

- 本書は Issue #152 のサブ画面 **S-13 テナント一覧 / S-14 テナント詳細・状態切替**(SaaS Admin 専用画面群) について、Claude Design に SSOT を投入して生成した 3 案の HTML 案を比較し、方向性を 1 案に絞った決定記録。
- 採用案・却下案ともこのディレクトリ配下に HTML をそのまま残し、Sprint 1 以降の画面実装スプリントが本書だけで着手判断できるようにする。
- 本書は実装着手 Issue ではない(ハイファイモック完成ではなく「方向性確定」が目的)。
- **本書は Issue #152 サブ画面 4 つ目(最終)。本 PR で Issue #152 を close する**。

### 0.1 決定プロセス(2026-06-02)

1. SSOT(`docs/specs/ui/screen-flow.md` §3 SaaS Admin 動線 / §2 サイトマップ / `docs/specs/基本設計書.md` §3.2 / `docs/specs/ui/access-matrix.md` §3 / §6 / `api/openapi.yaml` v1.5.0 `listTenants` / `getTenant` / `updateTenant` / `updateTenantStatus`)+ S-03 / S-04 / S-05 採用案 HTML(デザインシステム継承元)を Claude Design に投入、3 案生成。
2. 採用判断の前提として要件定義書のペルソナを再確認:
   - **SaaS 運営者 = 数名・専任**(要件 §2.3、「テナント管理・全体監視。業務データには触れない」)
   - 想定テナント数 = 数百規模(合計 5000 ユーザー / 1 テナント最大 50 名)
   - デバイス = PC + タブレット
   - screen-flow.md §3 注釈「業務動線と SaaS Admin 動線は画面遷移として直結しない、認可境界を視覚的にも明示」
3. ユーザ議論で以下を確認:
   - **視覚分離は強く表したい**(濃色 chrome / KPI ストリップ / 別サイドナビ等を積極採用)
   - **主要動線は「1 件を丁寧に確認・状態切替」**(専任 SaaS Admin、誤操作防止重視)
4. 上記前提下で 3 案を 7 軸(別空間視覚分離 / 一覧⇄詳細往復速度 / 誤操作防止 / URL 直アクセス整合 / 実装コスト / 想定読者ペルソナ / 拡張余地)で比較し、**案 B 管理コンソール風(別空間フルページ)を採用**。

## 1. 採用案: **案 B 管理コンソール風(Admin Console / Separate-Space Fullpage)**

ファイル: [`option-b-admin-console.html`](./option-b-admin-console.html)

### 採用の理由

1. **要件定義書のペルソナ「専任 SaaS Admin」と整合**
   - 要件 §2.3 では SaaS 運営者は「数名」「テナント管理・全体監視・業務データには触れない」と明記。専任業務として運営に集中する想定。
   - 案 B の濃色 chrome + 偽 URL バー + KPI ストリップは「専任業務用画面」の文脈を画面構造で表現する。
2. **screen-flow.md §3 注釈の「両動線非直結を視覚的にも明示」と最も整合**
   - SSOT §3 末尾で「両動線は画面遷移として直結しないことで、認可境界を視覚的にも明示する」と明記。
   - 案 B は濃色 chrome / 別サイドナビ / KPI ストリップで業務動線(S-03〜S-06)とは明確に別空間と認知できる。
   - 案 A は業務動線を継承するため別空間感がヘッダ帯色頼り、案 C はレイアウト構造で示すが色彩や chrome の主張は弱い。
3. **状態切替操作の重さに見合った文脈明示**
   - テナント停止(ACTIVE → SUSPENDED)は所属ユーザー全員がログイン不可になる重大操作。
   - 案 B のフルページ詳細 + 専任空間 chrome なら誤クリック誘発リスクを構造的に抑制できる。
   - 案 A のドロワーや案 C のマスターディテール構造はクリック数が少なく、状態切替を「軽い操作」と誤認させる懸念。
4. **URL 直アクセスと UI 実体が一致**
   - 案 B は `/admin/tenants/{id}` フルページ遷移なので、URL 直アクセス時のフォールバック対応が不要。実装・テスト・ブックマーク・通知メールリンクのいずれも素直。
   - 案 A はドロワー UI 実体 + URL fallback(タブ復元等)の二重実装が必要。
5. **Phase 2 以降の運営画面拡張に強い**
   - 専用サイドナビ構造は S-12 プラットフォーム監視ダッシュボード / 監査ログ参照 / プラン管理(Phase 2)等が追加されても自然に拡張できる。
   - 案 A は業務動線を継承するため、運営画面が増える度に「業務 / 運営」の混在を解きほぐす必要がある。

### 採用案の主な構成要素(Claude Design 出力から)

- **濃色トップバー**: 業務動線のブランドより濃い背景色(管理コンソール感)+ ブランド「tasks-webapi 管理コンソール」ラベル + APP_ADMIN バッジ + ユーザアバター(山田太郎)
- **偽 URL バー**: ヘッダ下に `/admin/tenants` 表示(本実装ではブラウザアドレスバーで代替されるが、モック上で「いま運営空間にいる」を視覚的に明示)
- **KPI ストリップ**: 数値カード(総テナント数 / ACTIVE 数 / SUSPENDED 数 / 直近 24h 新規)。S-12 プラットフォーム監視の予告も兼ねる(本 PR スコープ外なので静的表示のみ)
- **左サイドナビ(SaaS Admin 動線専用)**: テナント一覧(active) / プラットフォーム監視(S-12、disabled、Sprint 1 以降) / 監査ログ(disabled、Phase 2 以降)。業務動線リンクは表示しない
- **S-13 テナント一覧画面**:
  - 上部フィルタバー(常時展開):状態フィルタ(ACTIVE / SUSPENDED / すべて) + 名前検索(部分一致)
  - 一覧テーブル:テナント名 / 状態バッジ(緑 = ACTIVE / 赤 = SUSPENDED)/ 所属ユーザー数 / タスク数 / 作成日 / 操作セル(行クリックで S-14 へ)
  - ページング:50 件/ページ(OpenAPI `listTenants` parameters と一致)
  - 行クリック → `/admin/tenants/{id}` フルページ遷移
- **S-14 テナント詳細・状態切替画面**:
  - パンくず(管理コンソール > テナント一覧 > テナント A 株式会社)
  - テナント名(大表示 + 編集アイコン、クリックで inline 編集モード → `PUT /api/tenants/{id}` = A-06)
  - 状態バッジ(現状表示)
  - 主要メトリクス(所属ユーザー数 / タスク数 / 作成日 / 更新日)
  - 状態切替セクション:
    - ACTIVE 時 = 「このテナントを停止する」赤系 destructive ボタン → 確認ダイアログ(テナント名入力で確認、所属ユーザー全員がログイン不可になる旨を明示)
    - SUSPENDED 時 = 「このテナントを再有効化する」緑系 primary ボタン → 簡易確認
    - 切替後 `TENANT_SUSPENDED` / `TENANT_REACTIVATED` を監査ログに記録(サーバ側、UI は通知トーストのみ)
  - 戻るボタン(`/admin/tenants` へ戻る、ブラウザ戻るでも同じ動作)

### 1.1 ハイファイ化で追加された UI 要素(2026-06-02 第 2 反復)

採用後に Claude Design に作り込みを依頼し、以下を追加実装:

- **モック状態プレビューバー**: 開発・レビュー用に listView / detailView / errorView / loading 状態を切り替えるバー(本実装では非表示)
- **状態切替確認モーダル**(`#statusModal`):テナント名入力で confirm + ユーザー影響範囲表示 + destructive ボタン色強調(ACTIVE→SUSPENDED 時)/ 簡易 confirm(SUSPENDED→ACTIVE 時)
- **トースト通知ホスト**(`#toastHost`):保存成功 / 失敗のフィードバック(画面右下)
- **空状態**(`empty-state`):検索 / フィルタ結果 0 件時のメッセージ
- **エラー画面 fallback**(403 / 404 / 401):
  - 403:Tenant Admin / Member が `/admin/*` を直接 URL で叩いた時(`ROLE_BASED_DENIED`)
  - 404:存在しないテナント ID を直接 URL で叩いた時
  - 401:未認証時
- **ローディングスケルトン**:GET /api/tenants / GET /api/tenants/{id} 取得中の placeholder
- **保存スピナー**:PATCH / PUT 送信中の操作ボタン disabled + spinner

## 2. 却下案

### 案 A 業務動線統合型(右ドロワー)

ファイル: [`option-a-integrated-drawer.html`](./option-a-integrated-drawer.html)

**Claude Design 出力 注釈から**:

- 強み:S-04 の上部フィルタバー + 一覧 + S-05 の右ドロワーをそのまま流用。業務動線とコンポーネントが完全に一致するため学習コストが最小で、実装も既存基盤に素直に乗る。一覧を見たまま詳細を開閉でき、複数テナントの確認テンポが速い。
- 弱み:視覚が業務画面とほぼ同じため「別空間(運営軸)」の境界がヘッダの帯色・ラベル頼みになり弱め。ドロワー幅 480px では参考メトリクスや今後の運営情報が増えると手狭。URL 直アクセス時はフルページ fallback を別途用意する必要がある。
- 想定読者:業務側 UI に慣れた運用担当が片手間でテナント状態を確認・切替する兼務 SaaS Admin。一覧⇄詳細を素早く往復したい層。

**却下の理由**(本書):

- 要件 §2.3 の SaaS 運営者ペルソナ(専任、業務データに触れない)と齟齬。兼務前提の動線設計だが、要件では兼務想定されていない。
- 視覚分離が弱く、screen-flow.md §3 注釈「両動線非直結を視覚的にも明示」と整合性が低い。
- ドロワー幅で参考メトリクス / 監査ログ予告 / Phase 2 拡張余地を持ちにくい。

**Phase 2 で復活する余地**: SaaS Admin が業務テナントも兼務して片手間運用するシナリオが現実化した場合、「シンプルドロワーモード」として個人設定で復活する選択肢。

### 案 C マスターディテール(左一覧 + 右詳細)

ファイル: [`option-c-master-detail.html`](./option-c-master-detail.html)

**Claude Design 出力 注釈から**:

- 強み:一覧と詳細を常時同時表示。左で次々選択するだけで右に S-14 が即時差し替わり、複数テナントを連続して確認・状態切替するパワーユーザの操作効率が最も高い。一覧の文脈を失わずに 1 件を深掘りでき、選択行はハイライトで追跡できる。
- 弱み:左ペインに 392px を固定で割くため、右の詳細幅が狭まり 1 件の情報量はフルページ案に劣る。タブレット縦・狭幅では上下 2 段に折り返り、同時表示の利点が薄れる。1 件をじっくり見る用途には冗長。
- 想定読者:多数テナントを横断して棚卸し・一括点検する運用パワーユーザ。「次々に切り替えて状態を確認」する頻度が高い PC 中心の専任 SaaS Admin。

**却下の理由**(本書):

- 「1 件を丁寧に確認・状態切替」「誤操作防止」というユーザ確定動線と方向が異なる(案 C は「次々切替」が強み)。
- 詳細幅が狭まると状態切替の重さに見合った文脈表現がしにくい(確認ダイアログでカバーは可能だが、案 B のフルページ警告の方が構造的に強い)。
- タブレット縦で折返しが発生し、同時表示の利点が薄れる。

**Phase 2 で復活する余地**: テナント数が極端に増えた場合(例:1000 テナント超)に「棚卸しビュー」として個人設定で切替可能にする選択肢。

## 3. 開いている論点(別 Issue に引き継ぐ)

### #154 デザインシステム素案に引き継ぐ

1. **管理コンソール用 chrome の正式トークン化**:
   - 濃色トップバー / 偽 URL バー / 専用サイドナビの色トークン(`--admin-bg`, `--admin-accent` 等)。
   - 業務動線との視覚境界(`body.admin-mode` クラスとの相互作用)。
2. **KPI ストリップコンポーネント**:
   - 数値カード(総テナント数 / ACTIVE 数 / SUSPENDED 数 / 直近 24h 新規)の規格化。S-12 プラットフォーム監視で再利用予定。
   - 集計 API(`getPlatformMetrics` A-27)との連携、本 PR ではスコープ外で静的表示のみ。
3. **状態切替確認ダイアログ**:
   - ハイファイ版で実装済(`#statusModal`、ACTIVE → SUSPENDED 時はテナント名入力 + ユーザー影響範囲表示、SUSPENDED → ACTIVE 時は簡易 confirm)。
   - 共通の destructive 操作確認ダイアログコンポーネントとして規格化(他画面の論理削除等にも転用)。
4. **状態バッジ色トークン**:
   - 緑(ACTIVE)/ 赤(SUSPENDED)。本 PR では Bootstrap デフォルト色だがトークン化必要。
5. **inline 編集パターン(テナント名)**:
   - S-03 / S-04 の行内編集と同じパターンを 1 フィールドに適用。
6. **左サイドナビの「disabled」項目表示**:
   - Sprint 1 / Phase 2 で実装予定の項目(S-12 / 監査ログ参照)を予告表示する規約。
7. **偽 URL バーの本実装での扱い**:
   - モック上では「いま運営空間にいる」を視覚的に明示するが、本実装ではブラウザアドレスバーで代替。本実装で残すか判断。

### #153 OpenAPI v1.5.0 突き合わせに引き継ぐ

| # | 検討項目 | 本書の前提 | 確認したいこと |
|---|---|---|---|
| 1 | テナント一覧 API | OpenAPI v1.5.0 `GET /api/tenants` (A-04, `listTenants`) | 状態フィルタ / 名前検索 / ページング(50 件)が parameters に揃っているか |
| 2 | テナント単体取得 API | OpenAPI v1.5.0 `GET /api/tenants/{id}` (A-25, `getTenant`) | レスポンスに `userCount` / `taskCount` / `status` / `createdAt` / `updatedAt` が含まれるか |
| 3 | テナント名更新 API | OpenAPI v1.5.0 `PUT /api/tenants/{id}` (A-06, `updateTenant`) | テナント名のみの更新で確定済(状態は別 endpoint) |
| 4 | テナント状態切替 API | OpenAPI v1.5.0 `PATCH /api/tenants/{id}/status` (A-26, `updateTenantStatus`) | ACTIVE ↔ SUSPENDED 切替で確定済、監査ログ記録は Server 側 |
| 5 | KPI 集計 API | OpenAPI v1.5.0 `GET /api/platform/metrics` (A-27, `getPlatformMetrics`) | 総テナント数 / ACTIVE 数 / SUSPENDED 数 / 直近 24h 新規が揃うか(本 PR では静的表示、S-12 で本実装) |
| 6 | 監査ログ参照 API | `/api/audit-logs`(Tenant Admin 限定) | SaaS Admin が `TENANT_SUSPENDED` / `TENANT_REACTIVATED` の監査ログを参照する画面が必要か(本 PR スコープ外、Phase 2 候補) |
| 7 | 認可 | SaaS Admin (`hasRole('APP_ADMIN')`) のみ全 endpoint アクセス可 | Spring Security `@PreAuthorize` 設定が API 側で完備されているか |

### 別 Issue として独立起票候補(本書では結論を出さない)

- **S-12 プラットフォーム監視ダッシュボード**: KPI ストリップを本格化したフル画面、時系列グラフ等(基本設計書 §3.2)。Sprint 1 以降。
- **監査ログ参照画面**: SaaS Admin / Tenant Admin の両者向け。`TENANT_SUSPENDED` / `TENANT_REACTIVATED` を含む全アクション参照(Phase 2 想定)。
- **テナント削除 / プラン管理**: 物理削除はテナント解約 #167 で扱う(Phase 2)。プラン管理は MVP 範囲外。
- **テナント新規作成画面 S-11**: 認証済みユーザーのセルフサインアップ(Tenant Admin / Member 動線)、本画面の SaaS Admin 動線とは別。Sprint 1 以降。
- **個人設定でのビュー切替**: Phase 2 で「シンプルドロワー(案 A)」「マスターディテール(案 C)」を切替可能にする際の設定 UI / API。

## 4. 認可ポリシー整合確認

ロール × 画面 × 操作の認可マトリクスは `docs/specs/ui/access-matrix.md`(#151 成果物、main マージ済み)を SSOT とする。本書は案 B の UI 表現が SSOT と矛盾しないことを以下で局所的に確認する。差分が生じた場合は access-matrix.md と基本設計書 §6.2 を優先する。

| 観点 | 仕様 | 案 B での扱い | OK |
|---|---|---|---|
| 画面アクセス可否 | SaaS Admin (`APP_ADMIN`) のみ可、Tenant Admin / Member は 403 | ヘッダにロール切替セレクタを置かず SaaS Admin 一択、APP_ADMIN バッジ表示 | ✓ |
| visibility の効きどころ | テナント管理は visibility に依存しない(screen-flow.md §4 末尾) | 一覧 / 詳細とも visibility 判定なし、状態(ACTIVE/SUSPENDED)のみ | ✓ |
| テナント一覧参照 | `GET /api/tenants` (A-04) SaaS Admin 専用 | フィルタ + 検索 + ページング、サーバ側認可前提 | ✓ |
| テナント単体取得 | `GET /api/tenants/{id}` (A-25) SaaS Admin は任意テナント可 | 全テナントへ任意アクセス、URL 直アクセス時もサーバ側認可 | ✓ |
| テナント名更新 | `PUT /api/tenants/{id}` (A-06) SaaS Admin のみ | inline 編集パターン、不可時 403 | ✓ |
| テナント状態切替 | `PATCH /api/tenants/{id}/status` (A-26) SaaS Admin のみ | フルページ destructive 操作、確認ダイアログ | ✓ |
| 業務 API 不可 | SaaS Admin 単独は業務 API に触れない(ADR-0005) | サイドナビに業務動線リンクなし、業務 API 呼び出しは存在しない | ✓ |
| 監査ログ記録 | `TENANT_SUSPENDED` / `TENANT_REACTIVATED` を Server 側で記録 | UI 側は通知トーストのみ、サーバが自動記録 | ✓ |
| 兼務ユーザーの扱い | `APP_ADMIN` + `user_tenants` 行ありユーザーが `/admin/*` を開いた瞬間に SaaS Admin 動線へ切替 | サイドナビに業務動線リンクがないため、URL 変更で明示的に切替必要 | ✓ |
| Tenant Admin / Member が直接 URL で `/admin/tenants` を叩いた場合 | 403 (`ROLE_BASED_DENIED`、access-matrix.md §6 ケース 8) | サーバ側 `@PreAuthorize` で拒否、ハイファイ版で 403 エラー画面 fallback 実装(`errorView`) | ✓ |
| 存在しないテナント ID 直接 URL アクセス | 404 (`TENANT_NOT_FOUND`) | ハイファイ版で 404 エラー画面 fallback 実装 | ✓ |
| 未認証アクセス | 401 (`LOGIN_FAILED`) | ハイファイ版で 401 エラー画面 fallback 実装(Keycloak ログインへリダイレクト前提) | ✓ |

## 5. 関連

- 親 Issue: #152(本書、本 PR で close)/ #149(Sprint 0 画面設計)
- SSOT: `docs/specs/ui/screen-flow.md` v1.1 §3 / §2 / `docs/specs/基本設計書.md` §3.2 / `docs/specs/ui/access-matrix.md` §3 / §6 / `docs/specs/要件定義書.md` §2.3
- 前提整合確認済み: PR #348(access-matrix.md)/ PR #349(OpenAPI v1.5.0)/ PR #350(S-03 採用案)/ PR #351(S-04 採用案)/ PR #352(S-05 採用案)
- 引き継ぎ先: #153(OpenAPI 突合)/ #154(デザインシステム素案、特に管理コンソール用 chrome + KPI ストリップ + destructive 確認ダイアログ)
- 関連 ADR: ADR-0005(タスク認可 3 役割 = SaaS Admin の業務特権なし、本画面でも整合)
- 関連 Issue: #167(テナント解約 Phase 2)/ S-12 プラットフォーム監視(Sprint 1 以降)/ S-15 テナント運営者向けダッシュボード(Out of Scope #149)
