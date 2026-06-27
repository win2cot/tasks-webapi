package xyz.dgz48.tasks.webapi.tenant.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 招待メール関連の設定(ADR-0017)。
 *
 * @param acceptUrlBase 招待受諾画面のベース URL。メールには {@code <acceptUrlBase>?token=<平文トークン>} を載せる
 */
@ConfigurationProperties("tenant.invite")
public record InviteProperties(
    @DefaultValue("http://localhost:8080/invitations/accept") String acceptUrlBase) {}
