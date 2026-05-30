package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@code TENANT_ADMIN} ロールのユーザーを SecurityContext に投入するショートカット。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithMockJwt(roles = "TENANT_ADMIN")
public @interface WithMockTenantAdmin {}
