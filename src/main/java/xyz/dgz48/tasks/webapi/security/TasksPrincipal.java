package xyz.dgz48.tasks.webapi.security;

import java.security.Principal;
import org.jspecify.annotations.Nullable;

public class TasksPrincipal implements Principal {

  private final Long id;
  private final String oidcSub;
  private final String email;
  private final String fullName;
  private final String fullNameKana;
  @Nullable private final String departmentName;

  public TasksPrincipal(
      Long id,
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    this.id = id;
    this.oidcSub = oidcSub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
  }

  @Override
  public String getName() {
    return oidcSub;
  }

  public Long getId() {
    return id;
  }

  public String getOidcSub() {
    return oidcSub;
  }

  public String getEmail() {
    return email;
  }

  public String getFullName() {
    return fullName;
  }

  public String getFullNameKana() {
    return fullNameKana;
  }

  @Nullable
  public String getDepartmentName() {
    return departmentName;
  }
}
