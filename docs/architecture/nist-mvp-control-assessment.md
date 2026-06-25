# NIST SP 800-53 MVP control セット 最終確認(S4Infra-2)

最終更新: 2026-06-25 | Issue #753 | 前提: 監査ログ ハッシュチェーン #751(merged)

## 概要

infrastructure-plan v5 §6.5 / §7(S4Infra-2)。MVP 最小 control セット(NIST SP 800-53 Rev.5、
5 ファミリ × 各 1〜2 control)の **実装充足状況** を実コード・インフラ・Keycloak realm と突合し、
未充足項目の扱い(対応 / Phase 2)を確定する。control セットの正本は
[基本設計書 §6.1 NIST 統制マッピング](../specs/基本設計書.md)、準拠方針は
[要件定義書 §4.2](../specs/要件定義書.md)。

MVP control セットは AC / AU / IA / SC / SI の 5 ファミリ計 11 control(AU-9 を本確認で追加)。
本確認時点で **9 が充足、1 が部分、1 が設計のみ**。未充足は IA-5(認証情報ポリシー)と AU-2 の
1 年保管自動削除(B-03)で、扱いは「未充足項目」節に記す。

判定語彙:

- **充足** — 実装が存在し動作経路がある(コード / インフラ / realm で確認)
- **部分** — 中核は実装済だが付随要件(保管自動化等)が未実装
- **設計のみ** — 設計・スキーマはあるが有効化・設定が未了
- **Phase 2** — MVP スコープ外として明示的に繰り延べ

## MVP control セット 充足状況

| Control | ファミリ | 本システムでの実装 | 状態 | 主な根拠 |
|---|---|---|---|---|
| AC-2 アカウント管理 | AC | Keycloak + `user_tenants`(status INVITED/ACTIVE/DISABLED)、メンバー招待 | 充足 | `tenant/usecase/AddMemberUseCase`、`UserTenantJpaEntity`、JIT プロビジョニング(ADR-0006) |
| AC-3 アクセス制御 | AC | Spring Security `@EnableMethodSecurity` + `@PreAuthorize`(ロールベース) | 充足 | `security/adapter/web/SecurityConfig`、各 Controller の `hasRole`/`hasAnyRole` |
| AC-4 情報フロー | AC | `tenant_id` 分離(Hibernate Filter 自動付与)+ 越境検知の監査 | 充足 | `TenantAwareJpaTransactionManager`、`TenantFilteredEntity`、`CrossTenantViolation*`(ADR-0010) |
| AU-2 監査ログ | AU | `audit_logs` への記録(write + 特権 read、約 48 箇所) | 部分 | `audit/usecase/AuditLogPort` 呼出多数。1 年保管の **自動削除(B-03)未実装**(下記) |
| AU-9 監査情報保護 | AU | 自レコード HMAC ハッシュチェーン(改ざん検知)+ B-05 日次検証 | 充足 | #751 / ADR-0038(`AuditLogPersistenceAdapter`、`chain_heads`、`VerifyAuditChainUseCase`) |
| AU-10 否認防止 | AU | 同上(HMAC-SHA256、`chain_key` 単位連鎖、`chain_seq` 順序) | 充足 | #751 / ADR-0038。鍵は Parameter Store(本番 `source=ssm`) |
| IA-2 識別と認証 | IA | Keycloak OIDC + JWT(Resource Server 検証)、PKCE S256 | 充足 | `SecurityConfig`(issuer-uri)、`keycloak/realm-export/tasks-realm.json`(`pkce.code.challenge.method=S256`) |
| IA-5 認証情報管理 | IA | Keycloak が認証情報を保持(bcrypt 等) | 設計のみ | realm export に **passwordPolicy / OTP 未設定**。SP 800-63B ポリシー・MFA は未有効化(下記) |
| SC-8 通信保護 | SC | TLS 1.3(ALB / CloudFront)+ セキュリティレスポンスヘッダー | 充足 | ALB `ssl_policy=ELBSecurityPolicy-TLS13-1-2-2021-06`、`SecurityConfig` の CSP 等(ADR-0022) |
| SC-13 暗号化(保存時) | SC | RDS 保存時暗号化(KMS / AES-256)、S3 既定暗号化 | 充足 | `infra/modules/rds/main.tf`(`storage_encrypted = true`) |
| SI-10 入力検証 | SI | Jakarta Bean Validation(サーバ側 `@Valid`) | 充足 | 各 request DTO の `@NotBlank`/`@Size`/`@NotNull`、`@RestControllerAdvice`(ADR-0011) |

> ファミリ内訳: AC ×3 / AU ×3(AU-9 を本確認で追加)/ IA ×2 / SC ×2 / SI ×1。

## 未充足・要対応項目と扱い

| 項目 | 関連 control | 状態 | 扱い | 根拠 / 参照 |
|---|---|---|---|---|
| 監査ログ 1 年保管の自動削除(B-03) | AU-2 / AU-11 | 未実装 | **Phase 2** | 保管はスキーマ意図のみ。アンカー境界 prefix 削除は ADR-0038 §3.6 で設計済、解約 #167 と統合 |
| Keycloak passwordPolicy(SP 800-63B) | IA-5 | 設計のみ | **対応(別 Issue・小)** | realm export に未設定。最小長等の基本ポリシーは MVP で低コスト。realm 変更はレビュー分離のため別 Issue 推奨 |
| MFA(多要素認証)の有効化 | IA-5 | 設計のみ | **Phase 2** | 要件定義書では「MFA 推奨」。MVP では強制しない。Keycloak OTP は将来有効化 |
| HMAC 鍵の本番投入(SSM) | AU-9 / AU-10 | 運用前提 | **デプロイ手順** | Terraform は `audit_hmac_key_v1 = "CHANGE_ME"` のプレースホルダ。本番デプロイ前に SSM へ実鍵を投入(ADR-0038 §3.3、[dev-operations.md](../runbook/dev-operations.md)) |
| ユーザー無効化(ACTIVE→DISABLED)API | AC-2 | 未実装 | **Phase 2** | `user_tenants.status` に DISABLED はあるが遷移 API 未実装(解約 #167 圏) |

> 上記いずれも MVP の中核 control(AC-4 テナント分離・AU-9/10 改ざん検知・SC-8/13 暗号化・SI-10 検証)
> の充足を損なわない。残課題は保管自動化・認証情報ポリシー強化であり、いずれも段階導入が妥当。

## 本確認による基本設計書 §6.1 への反映

- **AU-9(監査情報保護)を追加**。#751 の自レコード HMAC ハッシュチェーンは改ざん検知(AU-9)と
  否認防止(AU-10)の双方を満たすため、従来 AU-10 のみだった表に AU-9 を明記する。
- **AU-10 の備考を更新**。「ハッシュチェーン(改ざん検知)」→ 自レコード HMAC-SHA256・`chain_key`
  単位連鎖・B-05 検証(ADR-0038)を参照。
- 本アセスメントへのポインタを §6.1 に付す。

## 関連ドキュメント

- [基本設計書 §6.1 NIST 統制マッピング](../specs/基本設計書.md)(control セット正本)
- [要件定義書 §4.2 セキュリティ要件(NIST 準拠)](../specs/要件定義書.md)
- [infrastructure-plan.md §6.5 / §7](./infrastructure-plan.md)(S4Infra-2)
- [ADR-0038 監査ログ ハッシュチェーン](../adr/0038-audit-log-hash-chain-tamper-evidence.md)(AU-9 / AU-10)
- [ADR-0010 テナント分離 Hibernate Filter](../adr/0010-tenant-isolation-hibernate-filter.md)(AC-4)
- [ADR-0022 セキュリティレスポンスヘッダー](../adr/0022-security-response-headers.md)(SC-8)
- [ADR-0006 Keycloak User Storage SPI](../adr/0006-keycloak-user-storage-spi.md)(AC-2 / IA-2)
- [dev-operations.md](../runbook/dev-operations.md)(HMAC 鍵投入・運用)
