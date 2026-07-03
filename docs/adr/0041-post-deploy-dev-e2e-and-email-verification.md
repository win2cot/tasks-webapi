# ADR-0041: dev 環境 post-deploy E2E テスト戦略(ブラウザ主軸 + 薄い API 補完 + SES 受信による会員登録メール検証)

- **Status**: Accepted
- **Date**: 2026-07-04
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: testing, e2e, security, infra, observability

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

Phase 1 の機能実装が一巡し、単体・Testcontainers 統合テスト・Playwright E2E(hermetic compose)を整備してきた。**これらはすべて dev へデプロイする前(pre-deploy)に JVM モードで実行される**。一方、dev/stg/prd の実行バイナリは **GraalVM ネイティブイメージ**([ADR-0008](0008-graalvm-native-image.md))であり、pre-deploy テストでは捕まえられない事象が存在する。

- **native 限定リグレッション**: `@AuthenticationPrincipal(expression="id")` の SpEL が native で `EL1008E → 500`、Hibernate Validator の reflection hints 欠落で全 write が native で 500(#810)など。JVM CI では非検出。
- **実 Keycloak + カスタム User Storage SPI**: Keycloak はユーザを tasks 側 MySQL から federation する自作 SPI で動く。ログインのたびに「Keycloak(最適化イメージ)→ tasks DB」連携が走り、**実デプロイ環境でしか通しで検証できない**。
- **ユーザが実際に見る画面**: SPA の描画・OIDC リダイレクト・ログインテーマ(新規登録リンク)等はブラウザでしか検証できない。
- **NIST 対応の API 不変条件**: 本システムは NIST 800-53 対応を掲げる(AC-4 等)。「クロステナント参照は 404(403 でなく)で存在を漏らさない」等の不変条件は、**正しく作られた UI からは発生しない操作**であり、API を直接叩かないと固定できない。
- **会員登録(ADR-0040)**: セルフサインアップは **double opt-in**(確認メールのリンク受領が到達性の証明)。E2E で検証するには **送られたメールを受信して確認リンクを取り出す**必要がある。dev の SMTP は実 SES で、かつ **sandbox 運用(解除予定なし)**。

したがって「dev に上げたものを、native 実機で・実 Keycloak 経由で・画面と API 不変条件と会員登録メールまで含めて」検証する **post-deploy テスト層**が必要になる。トリガーは完全自動でなくてよい(手動可)。

## 2. 検討した選択肢(Options Considered)

### 2.1 テスト層の構成

- **(a) API スモークのみ**: native ギャップ・API 不変条件は取れるが、**ユーザの画面を検証しない**。
- **(b) ブラウザ E2E のみ**: 実画面 + 全層(UI→API→native→Keycloak→DB)を貫くが、①障害の切り分けが甘い、②UI から踏めない不変条件(越境 404/403・ETag 競合)を検証できない、③native カバレッジが UI の通り道に限定される。
- **(c) ハイブリッド = ブラウザ主軸 + 薄い API 補完(採用)**: ブラウザで実ジャーニーと native/画面/Keycloak を担保し、**UI から踏めない不変条件だけ**を薄い API アサーションで補う。二重フルスイートは避ける。

### 2.2 会員登録メールの受信手段(dev, SES sandbox)

- **Mailpit**: OSS の SMTP キャッチャ。**ローカル/CI 専用**(実 SES を覗けない)。dev では使えない。
- **メール受信 SaaS(Mailosaur / MailSlurp 等)**: 一意アドレス + 取得 API。外部依存(Mailosaur は有料、MailSlurp 無料枠)。
- **転送(ImprovMX 等)→ IMAP**: 無料だが部品が多い。
- **SES 受信 → S3(採用)**: 専用サブドメインを SES 検証し、SES email receiving(ap-northeast-1)で受信メールを S3 に格納、テストが S3 の MIME を読む。**オール AWS・全部東京・追加外部サービス無し・sandbox のまま**。

> リージョン確認(2026-07-04、AWS 一般リファレンス): SES email receiving は **ap-northeast-1(Tokyo)対応済み**(`inbound-smtp.ap-northeast-1.amazonaws.com`)。以前は US/Ireland 中心だったが拡大した。リージョンまたぎ不要。

### 2.3 トリガー

- **(a) 手動 `workflow_dispatch`(採用)**: 夜間停止と相性が良く、必要な時だけ回せる。
- **(b) deploy.yml に post-deploy job 追加**: 毎デプロイ自動。将来の選択肢として残す。
- **(c) スケジュール実行**: 夜間停止で時間帯が限られ扱いづらい。採用しない。

## 3. 決定(Decision)

1. **ブラウザ主軸 + 薄い API 補完(2.1(c))** を dev post-deploy テスト戦略とする。
   - ブラウザ E2E(Playwright、`BASE_URL` を dev に向ける)で **実ジャーニー**(ログイン → 一覧 → タスク作成 → ステータス変更)を検証。ログインは **Keycloak OIDC(認可コード + PKCE)経由 = カスタム User Storage SPI を実機で通す**。ログインテーマ / 新規登録リンク描画も対象。
   - **UI から踏めない不変条件のみ** API で補完: クロステナント参照が 404 / 更新が 403、`If-Match` 競合(412)、未認証 401、エラースキーマ等。
2. **会員登録(ADR-0040 Flow B double opt-in)は SES 受信 → S3 方式(2.2)で実配信込みに検証**する。専用サブドメイン `e2e.dgz48.xyz` を SES 検証(sandbox の recipient 制限を満たす)+ SES receiving(ap-northeast-1)→ S3。テストは `run-<uuid>@e2e.dgz48.xyz` で登録 → S3 の受信メールをポーリング → MIME から確認リンク抽出 → complete。
3. **メール中身の厚い検証はローカル/CI(Mailpit)** に置き、**dev では実配信の到達性を最小限**に確認する(カバレッジ分割)。ただし dev の実配信検証(本 ADR の 2)は **必須**とする。
4. **トリガーは手動 `workflow_dispatch` を基本**(2.3(a))。将来 deploy 後 job 化の余地は残す。
5. dev データ汚染を避けるため、書き込みは **冪等 create + cleanup**、または **専用スモークテナント**に閉じる。

## 4. 理由(Rationale)

- native/JVM のギャップは実バグ(#810 等)で顕在化しており、native 実機を叩く post-deploy 層が唯一の自動検出手段。SPI federation も同様に dev でしか通しで確認できない。
- 「画面中心で API も巻き込む」は実利が高い一方、**セキュリティ/契約の不変条件は UI から発生しない**。NIST AC-4(存在を漏らさない = 越境は 404)を謳う以上、API 補完は省けない。
- SES sandbox のままでも、**宛先をドメイン単位で検証**すれば当該ドメイン宛に送れる。専用サブドメインの 1 回の検証で「sandbox recipient 制限の充足」と「受信の前提」を兼ねられる。Tokyo が SES 受信対応済のためリージョンまたぎも不要。
- 完全自動より運用の単純さ(手動トリガー)を優先。夜間停止(平日 02:00〜19:00 停止)とも衝突しない。

## 5. 影響(Consequences)

### 良い影響(Positive)

- native 限定リグレッション + 実 Keycloak/SPI + 実画面 + 会員登録メール(実配信)を**自動で**検証できる。
- 障害時に「ブラウザ(UI 統合)/ API(層特定)」の二段で切り分けられる。
- API 補完により NIST 系の越境・認可不変条件を dev 実機でも固定できる。

### 悪い影響・制約(Negative)

- **新規 infra**: SES domain identity(`e2e.dgz48.xyz`)+ Route53 MX + SES receipt rule + S3 バケット + 最小 IAM(Terraform 規約どおり IAM は同 PR)。
- **sandbox 制約**: 送信上限 200/24h・1/秒(smoke には十分)。宛先ドメイン検証が前提。
- **dev データ汚染**: 冪等 create + cleanup / 専用スモークテナントで閉じる運用が必要。
- **夜間停止**: 手動運用で「起動してから実行」。完全自動化する場合は起動 job を足す。
- **S3 MIME パース**: 受信生メールから確認リンクを抽出する処理が要る。
- **既存 hermetic E2E は流用不可**: 固定 seed 前提のため、dev 実機向けは `@dev-smoke` を新設(自己完結/read 寛容)。

### 既存ドキュメント・規約への波及

- `infra/` に SES receiving(S3 + 受信ルール + IAM)を追加([Terraform 規約](../specs/Terraform規約.md): 最小権限・IAM 同 PR)。
- `e2e/` に dev-smoke(Playwright `@dev-smoke` + API 補完 + S3/MIME ヘルパ)を追加。
- `.github/workflows/` に `dev-smoke.yml`(`workflow_dispatch`)を追加。
- [リリース前チェックリスト](../runbook/release-checklist.md) の §1 / §6 に post-deploy dev E2E を追記。

## 6. 実装メモ(Implementation Notes)

- **dev SES 現状(2026-07-04 確認)**: sandbox(`ProductionAccessEnabled=false`、200/日・1/秒)。送信 identity に `dgz48.xyz` / `mail.dgz48.xyz` を登録済。sandbox 受信用に Gmail 2 件を検証済(現行の signup メール到達手段)。**receipt rule set は未設定**(受信は greenfield)。
- **SES 受信の配線**: `e2e.dgz48.xyz` を SES domain identity として検証 → Route53 に `MX e2e.dgz48.xyz → inbound-smtp.ap-northeast-1.amazonaws.com`(優先度 10)→ active receipt rule set に「recipients=`e2e.dgz48.xyz`、action=S3(bucket + prefix)」を追加。DKIM/SPF は受信のみなら必須ではない。
- **一意アドレス**: `run-<uuid>@e2e.dgz48.xyz`。ドメイン検証が効くためアドレス個別検証は不要。
- **E2E フロー(会員登録)**: `POST /api/signup/request` → S3 prefix をポーリング(到着待ち)→ 最新オブジェクトの MIME を取得・パース → 確認リンク/トークン抽出 → `GET /api/signup/{token}` → `POST .../complete` → ログインまで到達確認。
- **資格情報**: 既存ジャーニーは dev テストユーザー(`tasks-webapi` public client の password grant、realm-export / e2e fixtures の固定ユーザー)。CI からは GitHub secret か SSM(OIDC ロール)で取得。
- **対象ホスト**: API=`https://api-dev.tasks.dgz48.xyz` / SPA=`https://tasks-dev.dgz48.xyz` / Keycloak=`https://auth-dev.dgz48.xyz`。いずれも公開 ALB のため GitHub Actions ランナーから到達可能(VPC 不要)。
- **夜間停止対応**: 手動トリガー時は dev 稼働中に実行。完全自動化する場合のみ「RDS→ECS 起動 + health 待ち」の前段 job を追加(AWS OIDC ロール要)。
- **段階実装(想定 PR 分割)**: ① SES 受信 infra(S3 + rule + IAM + MX)、② dev-smoke E2E(ブラウザ + API 補完)、③ 会員登録メール E2E(S3/MIME)、④ `dev-smoke.yml` + runbook 追記。

## 7. 参考リンク(References)

- [ADR-0008](0008-graalvm-native-image.md) — GraalVM Native Image(native/JVM ギャップの根拠)
- [ADR-0040](0040-onboarding-registration-and-credential-provisioning.md) — 会員登録 / double opt-in(検証対象フロー)
- [ADR-0037](0037-scheduled-batch-shedlock-and-ses.md) — SES による送信(本 ADR は受信側を追加)
- [ADR-0031](0031-single-product-version-dispatch-deploy.md) — デプロイ(post-deploy の起点)
- [リリース前チェックリスト](../runbook/release-checklist.md) — 本 E2E のゲート組込先
- [AWS 一般リファレンス — SES endpoints/quotas](https://docs.aws.amazon.com/general/latest/gr/ses.html) — SES email receiving 対応リージョン(Tokyo 対応を確認)
