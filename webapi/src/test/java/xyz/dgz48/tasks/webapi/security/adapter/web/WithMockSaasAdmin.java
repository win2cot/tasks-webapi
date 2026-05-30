package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@code APP_ADMIN} ロール(Keycloak realm role)の SaaS 管理者を SecurityContext に投入するショートカット。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithMockJwt(roles = "APP_ADMIN")
public @interface WithMockSaasAdmin {}
