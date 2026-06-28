package xyz.dgz48.tasks.webapi.user.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Keycloak Admin REST API による credential プロビジョニングの設定(application.yml の {@code
 * keycloak.admin.*}、ADR-0040 §3.1)。
 *
 * <p>{@code enabled=false}(既定)では Keycloak を呼ばず {@link
 * xyz.dgz48.tasks.webapi.user.adapter.external.LoggingCredentialProvisioningAdapter}
 * にフォールバックする(dev/test)。 {@code enabled=true}(本番)では service-account クライアント({@code clientId} / {@code
 * clientSecret})で Admin API を呼ぶ(realm-management の {@code manage-users} 限定権限)。
 *
 * @param enabled Keycloak Admin REST API 実装を有効化するか
 * @param serverUrl Keycloak のベース URL(例 {@code https://auth-dev.dgz48.xyz})。{@code /realms/...} と
 *     {@code /admin/realms/...} の共通プレフィックス
 * @param realm 対象 realm(例 {@code tasks})
 * @param clientId service-account confidential クライアントの clientId
 * @param clientSecret service-account クライアントシークレット(本番は SSM 由来。ログ出力禁止)
 */
@ConfigurationProperties("keycloak.admin")
public record KeycloakAdminProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:8080") String serverUrl,
    @DefaultValue("tasks") String realm,
    @DefaultValue("tasks-webapi-admin") String clientId,
    @DefaultValue("") String clientSecret) {}
