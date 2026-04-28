package xyz.dgz48.tasks.webapi.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("NullAway.Init") // JPA requires no-args constructor; fields initialized via JPA
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "oidc_sub", nullable = false, unique = true, length = 255)
  private String oidcSub;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "full_name_kana", nullable = false, length = 255)
  private String fullNameKana;

  @Nullable
  @Column(name = "department_name", length = 255)
  private String departmentName;

  public User(
      String oidcSub,
      String email,
      String fullName,
      String fullNameKana,
      @Nullable String departmentName) {
    this.oidcSub = oidcSub;
    this.email = email;
    this.fullName = fullName;
    this.fullNameKana = fullNameKana;
    this.departmentName = departmentName;
  }
}
