package xyz.dgz48.tasks.webapi.audit.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 監査ハッシュチェーンの設定(application.yml の {@code audit.hash-chain.*}、ADR-0038 §3.3)。
 *
 * <p>{@code source=property}(既定)は {@code secret} の値を HMAC 鍵に用いる(ローカル / テスト用)。{@code source=ssm} は
 * Parameter Store SecureString から鍵をロードする(本番)。{@code keyId} は新規書込の {@code hash_key_id} となる。
 *
 * @param keyId 現行の鍵識別子({@code hash_key_id})
 * @param source 鍵の取得元({@code property} | {@code ssm})
 * @param secret {@code source=property} 時の HMAC 鍵(本番では使わない)
 * @param ssmParameterPrefix {@code source=ssm} 時の鍵パラメータ名の接頭辞(実名は {@code <prefix>-<keyId>})
 */
@ConfigurationProperties("audit.hash-chain")
public record AuditHashChainProperties(
    @DefaultValue("v1") String keyId,
    @DefaultValue("property") String source,
    @DefaultValue("dev-insecure-audit-hmac-key-change-me") String secret,
    @DefaultValue("/tasks/dev/app/audit-hash-key") String ssmParameterPrefix) {}
