# ADR-0017: 招待・credential 回復フローの方式(自前招待トークン + Keycloak ホスト型標準、26.6 one-time action token 前提)

- **Status**: Accepted
- **Date**: 2026-06-07
- **Deciders**: win2cot
- **Related Issues**: #487(本 ADR)/ #464(トラッキング元)

## 1. コンテキスト(Context)

Keycloak 26.6.3 を取り込んだ(PR #271、2026-06-07 merged)。26.6 系で **Required Actions が one-time 化**された:

> Since this release, all *Required Actions* are now one-time actions by default to ensure they cannot be reused once completed. This change impacts the email action tokens created by the `execute-actions-email` endpoint, which is executed via the **Credential reset** button in the Admin console. Any action token generated through this endpoint (with whatever reset actions) is now strictly one-time use, ensuring that once a user completes the required steps, the token is immediately invalidated.
> — [Keycloak Upgrading Guide「Required actions are one-time actions by default」](https://www.keycloak.org/docs/latest/upgrading/index.html#required-actions-are-one-time-actions-by-default)

つまり `execute-actions-email` で発行された action token は、**利用者が必須アクションを完了した時点で即失効**し、完了後の再利用ができない(旧バージョンは有効期限内なら完了後も再利用可)。

この前提のもとで、メールに載せたリンク(トークン)で本人確認する 4 フローの方式を確定する必要がある(#464):

1. **ユーザー招待**(F-05、画面 S-08)
2. **パスワード reset**(forgot password)
3. **メール検証**(メールアドレス変更時の到達検証)
4. **MFA setup / reset**

なお既存決定との関係で次の不整合があり、本 ADR で解消する:

- ADR-0006(2026-06-04 改訂)は「user 作成の主経路は本システム招待 API」「会員登録は本システムの 1 画面(profile = project DB、password = Keycloak へ転送)」と決定済み
- 一方、要件定義書 §3.3.2(v1.4.4 時点)には「招待されたメールアドレスが Keycloak に未登録の場合、Keycloak 側で先にユーザー登録が必要(本システム外)」という旧整理が残っている

## 2. 検討した選択肢(Options Considered)— 招待フロー

### 選択肢 A: Keycloak action token 活用(execute-actions-email)

- 概要: 招待 API が users 行を作成し、Keycloak Admin API `execute-actions-email`(UPDATE_PASSWORD 等)で Keycloak が招待メールを送信。利用者は Keycloak ホストのパスワード設定画面で初期設定する
- 利点: トークンの発行・検証・失効を Keycloak に委譲できる。メール送信も Keycloak 標準
- 欠点:
  - **action token は「どのテナントへの招待か」を運べない**。テナント紐付け(`user_tenants`)の根拠が「招待した email = ログインした email」の一致だけになり、本人の受諾意思確認ステップが存在しない(誤招待・複数テナント同時招待で曖昧)。紐付けには結局自前の招待レコード + 初回ログイン時 JIT 紐付けロジックが必要
  - 26.6 one-time 化により、完了後にメールのリンクを再クリックした利用者は Keycloak のエラー画面(本システムへの導線なし)に落ちる。緩和には Keycloak テーマ(FreeMarker)でのエラー画面・メール文面の作り込みが必要で、Java/React と別系統の保守対象が増える
  - 本システム → Keycloak Admin API の呼び出し経路(service account)の整備が必要

### 選択肢 B: 自前招待トークン(本システム発行・検証)

- 概要: 招待メールは本システムが SES で送信。リンク先は本システムの招待受諾画面。トークンは本システムが発行・検証・消費する
- 利点:
  - リンク 1 本で「テナント紐付け + 受諾意思確認 + (未登録なら)会員登録」が完結。ADR-0006 の「会員登録 1 画面」「主経路は本システム招待 API」と整合
  - トークンの消費タイミングを設計で制御できる(表示では消費しない、受諾完了で消費)
  - `execute-actions-email` を招待で使わないため、**26.6 one-time 化の影響を構造的に受けない**
  - 画面・メール文面が本システム側(React / SES)に乗り、デザインシステム(#152 / #154)と単一系統
- 欠点: トークンの発行・保存・検証・失効・再送 API・メール文面を自前実装(`invitations` テーブル新設含む)。トークンのセキュリティ設計も自前責任(§3.3 のチェックリストで担保)

### 選択肢 C: ハイブリッド(紐付け = 自前トークン + credential 設定 = execute-actions-email)

- 概要: テナント紐付けは自前トークン、パスワード初期設定は Keycloak action token の 2 トークン 2 メール構成
- 欠点: 利用者が 2 通のメールを正しい順序で処理する必要があり UX が悪い。両トークンの失効状態の組合せ管理も複雑。却下

## 3. 決定(Decision)

### 3.1 招待フロー = 選択肢 B(自前招待トークン)

フローと消費条件:

1. Tenant Admin が S-08 でメールアドレスを指定して招待(A-09 `POST /api/tenant/users/invite`)→ 本システムが `invitations` 行を作成し、SES で招待メールを送信
2. 利用者がリンクをクリック(GET)→ 本システムの**招待受諾画面**(テナント名・招待者を表示)。**この時点ではトークンを消費しない**(何回開いても可。メールセキュリティ製品のリンク先読みでも消費されない)
3. 未登録の利用者: 会員登録 1 画面(ADR-0006 §3.3 — profile は project DB、password は Keycloak へ転送)で登録 → **登録 + `user_tenants` 紐付け + トークン消費を 1 トランザクション**で実行
4. 登録済みの利用者: 「ログインして参加」→ ログイン成功後に紐付け + トークン消費
5. 消費後の再クリック → 「この招待は使用済みです。ログインしてください」案内画面。期限切れ → 「期限切れです。管理者に再送を依頼してください」案内画面(S-08 に再送導線)

トークン仕様:

| 項目 | 値 | 根拠 |
|---|---|---|
| 生成 | `SecureRandom` 256bit、URL-safe Base64(約 43 文字) | 総当たり・推測を事実上不可能に |
| 保存 | DB には SHA-256 ハッシュのみ(平文はメール内にのみ存在) | DB 漏洩時のトークン流用防止(パスワードリセットと同じ定石) |
| TTL | **7 日** | 受け手都合の開封遅延(休暇・出張)を吸収できる最短クラス。業界相場(GitHub org 招待等)と一致。主防壁は one-time + 受諾時のパスワード設定であり、TTL は暴露窓の打ち切り |
| 消費 | one-time。受諾(POST)成功時に USED へ。表示(GET)では消費しない | 26.6 の one-time 原則と平仄を合わせつつ、消費は業務目的の達成時点に置く |
| 再送 | S-08 から。旧トークンを失効(REVOKED)させ新規発行 | 同一招待に有効トークンは常に高々 1 本 |

`invitations` テーブル(新設、列の概略 — DDL 詳細は実装 Issue で確定):
`id` / `tenant_id` / `email` / `token_hash` / `status`(PENDING / USED / REVOKED)/ `expires_at` / `invited_by` / `role` / `created_at` / `consumed_at`。

### 3.2 パスワード reset・メール検証・MFA setup = Keycloak ホスト型標準のまま

ADR-0006 §3.3「アカウント管理機能の所在」の決定を維持する。これらは**本人がいつでも再要求できる**フローであり、26.6 の one-time 化で UX は破綻しない(失効したら再要求すればよい)。追加実装はせず、以下を前提として明文化する:

- **action token は one-time(26.6+)である**。完了後のリンク再利用を前提とする設計・運用・ドキュメント記述をしてはならない
- Admin Console の **Credential reset** ボタン(SaaS 運用者の例外運用)も `execute-actions-email` 経路であり one-time。運用手順(runbook)に「リンクは 1 回限り、失効時は再送する」旨を明記する
- action token lifespan(`actionTokenGeneratedByUserLifespan` / `actionTokenGeneratedByAdminLifespan`)は Keycloak デフォルトを維持する。変更が必要になった場合は ADR-0015(Token Lifespan Policy)の改訂として扱う

### 3.3 セキュリティ受入チェックリスト(招待トークン実装の受入条件)

| 脅威 | 対策 |
|---|---|
| トークン推測・総当たり | SecureRandom 256bit + 照合失敗のレート制限・監査ログ |
| DB 漏洩時の流用 | SHA-256 ハッシュ保存(平文非保存) |
| アクセスログ・Referer 経由の漏洩 | GET では非消費 + TTL 7 日で生存限定 + ログのトークン masking + `Referrer-Policy` |
| 使い回し | one-time 消費(受諾成功時に USED、再送時に旧トークン REVOKED) |
| 誤招待・第三者の受諾 | 受諾画面でテナント名・招待者を表示し、本人のパスワード設定(or ログイン)で初めて成立。重複招待は E_CONFLICT(基本設計書 §5.4.1) |
| CSRF | 受諾 POST は既存の CSRF 対策枠(基本設計書 §6.6)に乗せる |
| open redirect | リンク先は固定パス。リダイレクトパラメータを持たない |

実装 PR では通常レビューに加えて security review を実施し、本表を受入条件として検証する。

## 4. 理由(Rationale)

- **テナントの真実はアプリ側にしかない**: テナント概念を Keycloak に持たせない(realm はプロジェクト単位、tenant role は `user_tenants` が SoT — ADR-0006 §3.7)既定アーキテクチャでは、招待リンクにテナント情報を運ばせる主体は本システムであるのが自然
- **総実装量は案 A と互角で、性質が良い**: 案 A も Admin API 連携 + FreeMarker テーマ + JIT 紐付けという同量級の固有実装を抱える。案 B は全部 Java/React(主戦場)に乗り、通常の Spring IT でテスト可能
- **26.6 影響の局所化**: Keycloak action token への依存を「本人が再要求できるフロー」のみに限定することで、one-time 化の UX 影響(完了後リンクのエラー画面)が実害化しない
- **定石化されたパターン**: 乱数トークン + ハッシュ保存 + TTL + one-time は OWASP Forgot Password Cheat Sheet 領域の確立した手法であり、§3.3 のチェックリストで機械的に検証できる

## 5. 影響(Consequences)

### 良い影響(Positive)

- 招待が「受諾意思確認 + テナント紐付け + 登録」をリンク 1 本・1 画面遷移で完結し、誤招待・複数テナント同時招待に頑健
- Keycloak バージョンアップ(action token 仕様変更)の影響面が縮小
- 画面・メール文面が単一デザイン系統に乗る

### 悪い影響・制約(Negative)

- `invitations` テーブル + トークン CRUD + 受諾画面 + SES 文面の自前実装(Sprint 3 以降で起票)
- トークンのセキュリティ品質を自前で担保する責任(§3.3 で受入条件化)
- 招待メールの到達性(SES bounce / 迷惑メール)の運用は本システム側の責務になる

### 既存ドキュメントへの波及

- **要件定義書 §3.3.2**(v1.4.5、本 ADR と同一 PR): 「Keycloak 側で先にユーザー登録が必要(本システム外)」を撤回し、招待受諾画面 + 会員登録 1 画面(ADR-0006)経由に改訂。招待トークンの one-time + TTL 7 日 + 再送導線を明記
- **基本設計書 §3.3 / §6.2**: 招待受諾画面・案内画面・`invitations` テーブルの詳細展開は実装 Issue 起票時に同時改訂(本 ADR では着手しない)
- **screen-flow.md / S-08**: 再送導線・受諾画面の追加は実装 Issue 起票時に反映
- **runbook**: Credential reset リンクの one-time 注記(実装 Issue 起票時)

## 6. 実装メモ(Sprint 3 以降の起票時に参照)

- スコープ分割の目安: (a) `invitations` migration + domain/usecase + 招待 API 拡張、(b) 受諾・登録画面(web/)+ 案内画面、(c) SES 文面 + 再送 + レート制限・監査ログ。レイヤー横断 ≥ 3 + テスト種類 ≥ 3 なら分割を検討(N4 の教訓)
- 実装 Issue 起票時は **#464 ではなく本 ADR / #487 を参照**する(#464 は本 ADR の Accepted 化で close 済みの想定)
- 招待経由の users 行作成は会員登録 1 画面経由(ADR-0006)。セルフサインアップ経路の JIT(要件定義書 §3.2.1 / §3.3.1)は本 ADR の対象外で従来どおり

## 7. 参考リンク

- #464(Keycloak 26.6 Required Actions one-time 化トラッキング、本 ADR の契機)
- #487(本 ADR の起票 Issue)
- PR #271(Keycloak 26.2.5 → 26.6.3)
- ADR-0006(Keycloak User Storage SPI、会員登録 1 画面・アカウント管理機能の所在・属性所有ルール)
- ADR-0015(Token Lifespan Policy)
- [Keycloak Upgrading Guide — Required actions are one-time actions by default](https://www.keycloak.org/docs/latest/upgrading/index.html#required-actions-are-one-time-actions-by-default)
- [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
