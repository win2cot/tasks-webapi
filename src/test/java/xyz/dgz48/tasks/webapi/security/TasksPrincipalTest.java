package xyz.dgz48.tasks.webapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TasksPrincipalTest {

  @Test
  void getNameReturnsOidcSub() {
    var principal =
        new TasksPrincipal(1L, "sub-001", "user@example.com", "山田 太郎", "ヤマダ タロウ", "開発部");
    assertThat(principal.getName()).isEqualTo("sub-001");
  }

  @Test
  void holdsAllUserData() {
    var principal =
        new TasksPrincipal(42L, "sub-002", "test@example.com", "鈴木 花子", "スズキ ハナコ", "営業部");
    assertThat(principal.getId()).isEqualTo(42L);
    assertThat(principal.getOidcSub()).isEqualTo("sub-002");
    assertThat(principal.getEmail()).isEqualTo("test@example.com");
    assertThat(principal.getFullName()).isEqualTo("鈴木 花子");
    assertThat(principal.getFullNameKana()).isEqualTo("スズキ ハナコ");
    assertThat(principal.getDepartmentName()).isEqualTo("営業部");
  }

  @Test
  void departmentNameIsNullable() {
    var principal =
        new TasksPrincipal(3L, "sub-003", "nodept@example.com", "田中 一郎", "タナカ イチロウ", null);
    assertThat(principal.getDepartmentName()).isNull();
  }
}
