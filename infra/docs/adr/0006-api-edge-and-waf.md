# ADR-0006: API 前段構成と WAF — ALB は CloudFront 配下に入れず、WAF は stg/prd の ALB に配置

- **Status**: Accepted
- **Date**: 2026-06-08
- **Deciders**: win2cot (Masayuki Ishikawa)
- **Tags**: infra, security, network, waf, cloudfront, alb, terraform

## 目次

- [1. コンテキスト(Context)](#1-コンテキストcontext)
- [2. 検討した選択肢(Options Considered)](#2-検討した選択肢options-considered)
- [3. 決定(Decision)](#3-決定decision)
- [4. 理由(Rationale)](#4-理由rationale)
- [5. 影響(Consequences)](#5-影響consequences)
- [6. 実装メモ(Implementation Notes)](#6-実装メモimplementation-notes)
- [7. 参考リンク(References)](#7-参考リンクreferences)

## 1. コンテキスト(Context)

### 背景

Issue #451(ログ設計)の議論で、API の前段構成と WAF 採否に関する三者不整合が発見された(#498 で起票)。

- 基本設計書 §2.3 物理構成図: ALB の備考に「WAF連携」と記載
- `docs/architecture/infrastructure-plan.md`: WAF の記述なし(Terraform タスクにも存在しない)
- `infra/docs/adr/0005-logging-platform.md` §3 ログカタログ: WAF ログの行なし

また、API が CloudFront を経由するか(現計画は `api-dev` → ALB 直、`tasks-dev` → CloudFront → S3 の分離)はこれまで明示的に議論されていなかった。WAF を edge に置くなら CloudFront 前提になるため、両論点は相互依存であり 1 ADR で扱う。

### 前提(確定事項)

- 現状の前段構成(`infrastructure-plan.md` §3.1 / §3.2): 単一 ALB(platform 所有)が `api-dev.tasks.dgz48.xyz`(tasks-webapi)と `auth-dev.dgz48.xyz`(共有 Keycloak)を Host ベースの Listener Rule で収容。`tasks-dev.dgz48.xyz`(フロント静的)のみ CloudFront → S3。
- MVP リリース時の稼働環境は **local + gha + dev のみ**。stg / prd は Post-Sprint-0 に延期(`infrastructure-plan.md` §2)。
- 運用は win2cot 単独、コスト感度が高い。
- API は全エンドポイントが認証付き・テナント別で、レスポンスはキャッシュ不可。
- 利用者は当面国内のみ。
- 本線の防御は API 自身の認証・認可・テナント分離(`docs/specs/認可マトリクス.md`、Hibernate Filter による越境遮断 ADR-0010、JPA prepared statement、Bean Validation)で成立している。WAF はその上の defense in depth に位置づく。

### 価格(一次ソース確認、2026-06-08)

- AWS WAF: $5/Web ACL/月 + $1/ルール(またはルールグループ)/月 + $0.60/100万リクエスト。
- CloudFront VPC origins(内部 ALB を private 化して CloudFront 配下に置く機構)自体は追加料金なし。ただし標準の CloudFront リクエスト課金は発生する。

## 2. 検討した選択肢(Options Considered)

### 選択肢 A: WAF なし(現状追認)

- 概要: ALB 直のまま。Shield Standard(無料・自動の L3/L4 DDoS 緩和)のみに依存。基本設計書 §2.3 の「WAF連携」を削除する。
- 利点: 追加コスト $0。作業なし。false positive 運用が発生しない。
- 欠点: L7 の雑多なスキャナ・ボット・ブルートフォースに対する減衰層がない。SaaS としてのセキュリティ説明力(顧客チェックシートの「WAF 有無」)で弱い。

### 選択肢 B: WAF on ALB(REGIONAL Web ACL)

- 概要: ALB に REGIONAL scope の Web ACL を関連付ける。platform stack(ALB と同居)所有。1 ACL で api + auth(Keycloak)両方を保護。
- 利点: 現行の前段構成・ログ相関設計(#451 で確定した §4.3)を一切変えない。Keycloak のログイン入口も同じ ACL でカバーできる。最小作業。WAF ログは CloudWatch Logs へ直接出力可。
- 欠点: ALB の生 DNS 名を特定されると WAF をバイパスして ALB を直接叩ける(理論上)。約 $6〜9/月。

### 選択肢 C: API を CloudFront 配下に入れ edge WAF(CLOUDFRONT scope)

- 概要: API 用の CloudFront distribution を新設し、WAF を CLOUDFRONT scope(us-east-1)で edge に配置。ALB は VPC origins で private 化し直接アクセスを遮断。
- 利点: WAF バイパス不能。DDoS 吸収を edge で行え耐性が最も高い。public 入口を CloudFront に一本化できる。
- 欠点: 作業量が最大 — API distribution 新設 + ACM(us-east-1)+ DNS 切替 + ALB の CloudFront 限定化 + **ログ設計.md §4 の相関キー再設計(CloudFront request id の伝播)が発火**。API はキャッシュ不可・国内のみのため CloudFront 配下化の本来便益(キャッシュ・edge TLS 終端の地理的高速化)は実質ゼロ。auth-dev を同様に守るには別途対応が要る。#451 で確定したばかりのログ設計に即手戻りが入る。

## 3. 決定(Decision)

**採用**: 前段構成は **選択肢 A の構成(ALB を CloudFront 配下に入れない)** を維持し、WAF は **選択肢 B(ALB に REGIONAL Web ACL)を stg/prd のみに適用** する。

- **API は CloudFront を経由しない**。CloudFront は引き続きフロント静的配信(`tasks-dev` 系)専用とする。
- **WAF は stg / prd の ALB にのみ適用**(REGIONAL Web ACL、platform stack 所有、api + auth を 1 ACL でカバー)。**dev は WAF なし**(攻撃露出が小さく、コストと false positive 調査負荷を回避)。
- **初期ルールの出発点**は AWS Managed Rules の Core(`AWSManagedRulesCommonRuleSet`)+ 既知悪性 IP(`AWSManagedRulesAmazonIpReputationList`)+ rate-based rule とし、まず **count モードで観測してから block へ昇格**する。ルールグループの最終選定・rate 閾値・count→block 昇格・false positive 運用は **Phase 2 の派生 Issue(#501)に委譲**する(本 ADR では器と出発点のみ確定)。

## 4. 理由(Rationale)

1. **CloudFront 配下化の便益が本システムでは実質ゼロ**: API は全リクエストが認証付き・テナント別でキャッシュ不可、利用者は国内のみで edge TLS 終端の地理的便益も薄い。CloudFront を被せる動機はキャッシュではなく「WAF/DDoS を edge に」だけに畳めるが、それは WAF 配置の選択(REGIONAL か CLOUDFRONT か)の問題に還元できる。
2. **「設計の綺麗さ」での C の優位は限定的**: 「単一入口」が綺麗になるのは VPC origins で ALB を完全に閉じたときであり、その代償が §4 ログ相関の再設計と distribution 増設の運用負荷。一方 B は「静的=CloudFront / 動的=ALB / WAF は動的入口に 1 個」という役割分担で説明が立ち、混在というより対称的な配置になる。
3. **バイパス耐性の実リスク差が小さい**: WAF をバイパスされて素通りするのは ALB 直叩きの経路だが、本線防御(認証・認可・テナント分離)はそこでも有効。WAF の実価値の大半(スキャナ・ボット・ブルートフォースの減衰)は DNS 正面に来るトラフィックに効く。ALB 生 DNS 名を特定して狙う段階の脅威が顕在化したときが C への移行点で、MVP の脅威モデル外と判断する。
4. **#451 で確定したログ相関設計を保全**: B は §4.3 を一切変更しない。C は確定直後の設計に即手戻りを生む。
5. **コストより作業量・手戻り・運用負荷で判断**: 金額差はほぼ無視できる(dev 月額は NAT ≈$45 が支配的、WAF は stg/prd のみ)。判断軸を作業量・手戻り・単独運用の認知負荷に置くと B が明確に軽い。
6. **dev に WAF を置かない**: dev は部分稼働・低トラフィックで攻撃露出が小さく、ルールチューニング前の false positive がローカル開発を阻害する不利益のほうが大きい。stg で count モード観測してから prd へ展開する。

捨てた利点として、C のバイパス不能・最高 DDoS 耐性・単一入口は魅力だが、本システムの脅威モデル・トラフィック特性ではコスト(作業量・手戻り)がそれを上回る。グローバル展開・実攻撃顕在化時に再評価する(§6 再評価トリガ)。

## 5. 影響(Consequences)

### 良い影響(Positive)

- #451 で確定したログ相関設計(ログ設計.md §4)に手戻りが発生しない
- 前段構成・DNS・証明書(`*.tasks.dgz48.xyz`)の現行設計を変えない
- WAF が 1 ACL で api + auth(Keycloak)両方を保護でき、Keycloak のブルートフォースにも効く
- dev のコストと false positive 調査負荷を回避できる

### 悪い影響・制約(Negative)

- ALB の生 DNS 名を特定されると WAF をバイパスして ALB を直接叩ける(理論上のバイパス経路、本線防御は有効)。深刻化時は C へ移行(§6)
- WAF ログは requestId 相関を持たない(時間 + 送信元 IP ベースの相関を受容。app アクセスログ行 `HTTP_REQUEST` が相関ハブのまま)
- WAF が効くのは stg/prd のみで、dev では L7 減衰がない(dev は許容)
- 約 $6〜9/月の追加(stg/prd 稼働時)

### 既存ドキュメント・規約への波及

- **基本設計書 §2.3**: ALB 備考「WAF連携」を「WAF連携(stg/prd のみ、REGIONAL Web ACL。dev は不適用。詳細 infra ADR-0006)」に修正する。
- **`infrastructure-plan.md`**: WAF 方針(stg/prd の ALB に REGIONAL Web ACL、CloudFront 非経由)を追記する。stg/prd Terraform 設計タスクに WAF を含める。
- **`infra/docs/adr/0005-logging-platform.md` §3 ログカタログ**: WAF アクセスログ(stg/prd、CloudWatch Logs)の行を追加する。
- **`docs/specs/ログ設計.md` §4.3 再評価トリガ**: 「API を CloudFront 配下に入れる構成変更」の参照先 Issue を本 ADR(#498)に更新する。
- WAF ルールの最終選定は Phase 2 派生 Issue で扱う。

## 6. 実装メモ(Implementation Notes)

### 配置と所有

| 項目 | 内容 |
|---|---|
| scope | REGIONAL(ALB 用) |
| 所有 stack | platform(ALB と同居、ADR-0004) |
| 適用環境 | stg / prd のみ(dev なし) |
| カバー対象 | api(tasks-webapi)+ auth(Keycloak)を 1 Web ACL で |
| ログ出力先 | CloudWatch Logs(直接出力可、ADR-0005 カタログに行追加) |

### 初期ルールの出発点(最終確定は Phase 2)

- `AWSManagedRulesCommonRuleSet`(Core rule set)
- `AWSManagedRulesAmazonIpReputationList`(既知悪性 IP)
- rate-based rule(L7 DoS / ブルートフォース緩和、閾値は観測で確定)
- 運用: stg で **count モード**で false positive を観測 → block へ昇格してから prd 展開

### Terraform を scope 非依存に書く(将来 C 移行コストの最小化)

ルール定義(managed rule group の選定・rate rule)を scope に依存しない module 変数として切り出しておく。将来 C(CLOUDFRONT scope、us-east-1)へ移行する場合の差分を「Web ACL の器と provider/region」だけに限定でき、ルール資産を再利用できる。

```hcl
# 例: ルール定義を変数化し、scope/provider のみ環境で差し替える
variable "waf_scope" {
  type    = string
  default = "REGIONAL" # 将来 C 移行時は "CLOUDFRONT"(us-east-1 provider)
}
```

### 再評価トリガ(C への移行を検討する条件)

- グローバル展開(国外利用者)で edge TLS 終端 / キャッシュの便益が顕在化
- ALB 生 DNS 名を狙った実攻撃の顕在化(WAF バイパス経路の悪用)
- 公開・キャッシュ可能な API(認証不要の参照系等)の出現
- これらに該当した場合、選択肢 C(CloudFront + VPC origins + CLOUDFRONT scope WAF)へ移行し、ログ設計.md §4 の相関キーを CloudFront request id 伝播込みで再設計する

## 7. 参考リンク(References)

- `docs/specs/基本設計書.md` §2.3(物理構成図、WAF連携)
- `docs/architecture/infrastructure-plan.md` §3.1 / §3.2(ALB Host ルール、CloudFront/S3 分離)
- `infra/docs/adr/0004-platform-project-infra-separation.md`(ALB は platform 所有)
- `infra/docs/adr/0005-logging-platform.md` §3(ログカタログ)
- `docs/specs/ログ設計.md` §4.3(相関キー再評価トリガ)
- [GitHub Issue #498](https://github.com/win2cot/tasks-webapi/issues/498): 本 ADR の起票 Issue
- [GitHub Issue #501](https://github.com/win2cot/tasks-webapi/issues/501): WAF ルール選定とチューニング(Phase 2、本 ADR から委譲)
- [GitHub Issue #451](https://github.com/win2cot/tasks-webapi/issues/451): 発見元(ログ設計)
- [AWS WAF Pricing](https://aws.amazon.com/waf/pricing/)
- [Amazon CloudFront Pricing](https://aws.amazon.com/cloudfront/pricing/)
- [Introducing Amazon CloudFront VPC origins (AWS Blog)](https://aws.amazon.com/blogs/aws/introducing-amazon-cloudfront-vpc-origins-enhanced-security-and-streamlined-operations-for-your-applications/)
