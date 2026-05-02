package xyz.dgz48.tasks.webapi.security;

import java.security.Principal;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(of = "sub")
public class TasksPrincipal implements Principal {

  private final Long id;
  private final String sub;
  private final String email;
  private final String fullName;
  private final String fullNameKana;
  @Nullable private final String departmentName;

  public TasksPrincipal(
      Long id,
      String sub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    this.id = id;
    this.sub = sub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
  }

  @Override
  public String getName() {
    return sub;
  }

  public Long getId() {
    return id;
  }

  public String getSub() {
    return sub;
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
