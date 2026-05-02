package xyz.dgz48.tasks.webapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TasksPrincipalTest {

  @Test
  void storesAllFields() {
    TasksPrincipal principal =
        new TasksPrincipal(1L, "sub123", "user@example.com", "山田太郎", "ヤマダタロウ", "開発部");

    assertThat(principal.getId()).isEqualTo(1L);
    assertThat(principal.getOidcSub()).isEqualTo("sub123");
    assertThat(principal.getEmail()).isEqualTo("user@example.com");
    assertThat(principal.getFullName()).isEqualTo("山田太郎");
    assertThat(principal.getFullNameKana()).isEqualTo("ヤマダタロウ");
    assertThat(principal.getDepartmentName()).isEqualTo("開発部");
  }

  @Test
  void getNameReturnsOidcSub() {
    TasksPrincipal principal =
        new TasksPrincipal(1L, "sub123", "user@example.com", "山田太郎", "ヤマダタロウ", null);

    assertThat(principal.getName()).isEqualTo("sub123");
  }

  @Test
  void departmentNameCanBeNull() {
    TasksPrincipal principal =
        new TasksPrincipal(1L, "sub123", "user@example.com", "山田太郎", "ヤマダタロウ", null);

    assertThat(principal.getDepartmentName()).isNull();
  }
}
