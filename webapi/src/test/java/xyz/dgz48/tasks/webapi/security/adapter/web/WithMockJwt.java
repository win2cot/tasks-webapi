package xyz.dgz48.tasks.webapi.security.adapter.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * テストメソッドまたはクラスに付与することで、{@link xyz.dgz48.tasks.webapi.security.domain.TasksPrincipal} を
 * SecurityContext に投入した状態でテストを実行する。
 *
 * <p>{@link org.springframework.security.test.context.support.WithMockUser} の代替として使用し、{@link
 * xyz.dgz48.tasks.webapi.security.adapter.web.TasksAuthenticationToken} を経由する経路をカバーする。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwt {

  long id() default 1L;

  String sub() default "test-sub";

  String email() default "test@example.com";

  String fullName() default "テスト太郎";

  String fullNameKana() default "テストタロウ";

  /** 空文字列は {@code null}(部署なし)として扱われる。 */
  String departmentName() default "";

  /**
   * 付与するロール名。{@code ROLE_} プレフィックスは不要({@code hasRole()} と同じ形式)。
   *
   * <p>例: {@code "MEMBER"}, {@code "TENANT_ADMIN"}, {@code "APP_ADMIN"}
   */
  String[] roles() default {"MEMBER"};
}
