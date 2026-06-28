package xyz.dgz48.tasks.webapi.tenant.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * サインアップ確認メール関連の設定(ADR-0040 §3.3)。
 *
 * @param confirmUrlBase 確認画面のベース URL。メールには {@code <confirmUrlBase>?token=<平文トークン>} を載せる
 */
@ConfigurationProperties("tenant.signup")
public record SignupProperties(
    @DefaultValue("http://localhost:8080/signup-complete") String confirmUrlBase) {}
